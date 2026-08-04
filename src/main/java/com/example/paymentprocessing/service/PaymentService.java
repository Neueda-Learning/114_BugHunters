package com.example.paymentprocessing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.paymentprocessing.enums.PaymentStatus;
import com.example.paymentprocessing.exception.AccountNotFoundException;
import com.example.paymentprocessing.exception.InvalidStatusTransitionException;
import com.example.paymentprocessing.exception.OtpVerificationFailedException;
import com.example.paymentprocessing.exception.PaymentProcessingException;
import com.example.paymentprocessing.model.Account;
import com.example.paymentprocessing.model.Payment;
import com.example.paymentprocessing.model.PaymentHistory;
import com.example.paymentprocessing.repository.AccountRepository;
import com.example.paymentprocessing.repository.PaymentHistoryRepository;
import com.example.paymentprocessing.repository.PaymentRepository;
import com.example.paymentprocessing.validation.PaymentValidator;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final AccountRepository accountRepository;
    private final PaymentValidator paymentValidator;
    private final OtpVerificationService otpVerificationService;

    @Value("${app.otp.recipient-email}")
    private String otpRecipientEmail;

    public PaymentService(PaymentRepository paymentRepository, PaymentHistoryRepository paymentHistoryRepository,
            AccountRepository accountRepository, PaymentValidator paymentValidator,
            OtpVerificationService otpVerificationService) {
        this.paymentRepository = paymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.accountRepository = accountRepository;
        this.paymentValidator = paymentValidator;
        this.otpVerificationService = otpVerificationService;
    }

    public Payment createPayment(Payment payment) {
        // Generate idempotency key server-side before validation so the
        // duplicate-key check in the validator has a value to inspect.
        payment.setKey(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentValidator.validateNewPayment(payment);

        return paymentRepository.save(payment);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
    }

    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }

    public List<PaymentHistory> getPaymentHistoryByPaymentId(Long paymentId) {
        getPaymentById(paymentId); // ensure payment exists
        return paymentHistoryRepository.findByPaymentId(paymentId);
    }

    /**
     * Generates an OTP for the given payment and delivers it to the configured
     * recipient email address ({@code app.otp.recipient-email}).
     *
     * @param paymentId the payment for which the OTP should be sent
     */
    public void sendOtpForPayment(Long paymentId) {
        getPaymentById(paymentId); // ensure payment exists
        otpVerificationService.sendOtp(paymentId, otpRecipientEmail);
    }

    @Transactional
    public Payment updatePaymentStatus(Long id, PaymentStatus newStatus) {
        Payment payment = getPaymentById(id);
        return transitionWithHistory(payment, newStatus, "Manual status update");
    }

    /**
     * Processes a payment from {@code CREATED} through to {@code COMPLETED}:
     *
     * <ol>
     *   <li>Runs all payment validations; on success transitions to {@code VALIDATED}
     *       and records the change in {@code payment_history}.</li>
     *   <li>Verifies the supplied OTP; on success transitions to {@code SENT} and
     *       records the change in history.</li>
     *   <li>Debits the source account and credits the destination account; on success
     *       transitions to {@code COMPLETED} and records the change in history.</li>
     * </ol>
     *
     * <p>Rules enforced:
     * <ul>
     *   <li>The payment must be in {@code CREATED} status when processing starts.</li>
     *   <li>If validation or OTP verification fails, the payment is marked {@code FAILED}
     *       and no account balance is touched.</li>
     *   <li>If the balance update fails after OTP success, the payment is marked
     *       {@code FAILED} from {@code SENT} status.</li>
     * </ul>
     */
    @Transactional(noRollbackFor = { OtpVerificationFailedException.class, PaymentProcessingException.class })
    public Payment processPayment(Long paymentId, String otpCode) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.CREATED) {
            throw new InvalidStatusTransitionException(
                    "Payment " + paymentId + " must be in CREATED status to be processed, but is "
                            + payment.getStatus());
        }

        try {
            paymentValidator.validateAmount(payment.getAmount());
            paymentValidator.validateCurrency(payment.getCurrency());
            Account fromAccount = paymentValidator.validateAccounts(payment.getAccountFrom(), payment.getAccountTo());
            paymentValidator.validateSufficientFunds(fromAccount, payment.getAmount());
            transitionWithHistory(payment, PaymentStatus.VALIDATED, "All payment validations passed");
        } catch (ResponseStatusException ex) {
            transitionWithHistory(payment, PaymentStatus.FAILED, "Validation failed: " + ex.getReason());
            throw new PaymentProcessingException("Payment validation failed: " + ex.getReason(), ex);
        }

        if (!otpVerificationService.isOtpValid(paymentId, otpCode)) {
            transitionWithHistory(payment, PaymentStatus.FAILED,
                    "OTP verification failed. User must restart payment from the beginning.");
            throw new OtpVerificationFailedException(
                    "OTP verification failed for payment " + paymentId
                            + ". No account balance was updated; please restart the payment process.");
        }

        transitionWithHistory(payment, PaymentStatus.SENT, "OTP verification succeeded");

        try {
            transferFunds(payment);
            return transitionWithHistory(payment, PaymentStatus.COMPLETED,
                    "Account balance update completed successfully");
        } catch (Exception ex) {
            transitionWithHistory(payment, PaymentStatus.FAILED, "Balance update failed: " + ex.getMessage());
            throw new PaymentProcessingException("Payment processing failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Debits the source account and credits the destination account for the given
     * payment. Both accounts are locked (in a deterministic order to avoid deadlocks
     * between concurrent, opposite-direction transfers) and the source balance is
     * re-validated at the moment of transfer, since time may have passed since the
     * payment was first validated.
     */
    private void transferFunds(Payment payment) {
        String accountFrom = payment.getAccountFrom();
        String accountTo = payment.getAccountTo();
        boolean fromFirst = accountFrom.compareTo(accountTo) <= 0;

        Account firstLocked = lockAccount(fromFirst ? accountFrom : accountTo);
        Account secondLocked = lockAccount(fromFirst ? accountTo : accountFrom);

        Account fromAccount = fromFirst ? firstLocked : secondLocked;
        Account toAccount = fromFirst ? secondLocked : firstLocked;

        paymentValidator.validateSufficientFunds(fromAccount, payment.getAmount());

        fromAccount.setBalance(fromAccount.getBalance() - payment.getAmount());
        toAccount.setBalance(toAccount.getBalance() + payment.getAmount());

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    private Account lockAccount(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
    }

    private Payment transitionWithHistory(Payment payment, PaymentStatus newStatus, String remarks) {
        PaymentStatus oldStatus = payment.getStatus();
        paymentValidator.validateStatusTransition(oldStatus, newStatus);

        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());
        Payment updated = paymentRepository.save(payment);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(payment.getId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        history.setRemarks(remarks);
        history.setType(payment.getType());
        paymentHistoryRepository.save(history);

        return updated;
    }
}