package com.example.paymentprocessing.service;

import com.example.paymentprocessing.enums.PaymentStatus;
import com.example.paymentprocessing.exception.AccountNotFoundException;
import com.example.paymentprocessing.exception.InvalidStatusTransitionException;
import com.example.paymentprocessing.exception.OtpVerificationFailedException;
import com.example.paymentprocessing.model.Account;
import com.example.paymentprocessing.model.Payment;
import com.example.paymentprocessing.model.PaymentHistory;
import com.example.paymentprocessing.repository.AccountRepository;
import com.example.paymentprocessing.repository.PaymentHistoryRepository;
import com.example.paymentprocessing.repository.PaymentRepository;
import com.example.paymentprocessing.validation.PaymentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentValidator paymentValidator;

    @Mock
    private OtpVerificationService otpVerificationService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment buildPayment(Long id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmount(100.0);
        p.setCurrency("USD");
        p.setAccountFrom("ACC001");
        p.setAccountTo("ACC002");
        p.setStatus(status);
        p.setType("TRANSFER");
        return p;
    }

    private Account buildAccount(String accountNumber, double balance) {
        Account a = new Account();
        a.setAccountNumber(accountNumber);
        a.setBalance(balance);
        a.setCurrency("USD");
        return a;
    }

    // --- createPayment ---

    @Test
    void createPayment_setsDefaultCreatedStatus_whenStatusIsNull() {
        Payment payment = buildPayment(null, null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void createPayment_forcesCreatedStatus_evenWhenClientSuppliesDifferentStatus() {
        Payment payment = buildPayment(null, PaymentStatus.VALIDATED);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void createPayment_setsCreatedAtAndUpdatedAt() {
        Payment payment = buildPayment(null, null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(payment);

        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void createPayment_savesToRepository() {
        Payment payment = buildPayment(null, null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(payment);

        verify(paymentRepository).save(payment);
    }

    // --- getAllPayments ---

    @Test
    void getAllPayments_returnsAllPayments() {
        List<Payment> payments = List.of(
                buildPayment(1L, PaymentStatus.CREATED),
                buildPayment(2L, PaymentStatus.SENT));
        when(paymentRepository.findAll()).thenReturn(payments);

        List<Payment> result = paymentService.getAllPayments();

        assertThat(result).hasSize(2);
        verify(paymentRepository).findAll();
    }

    @Test
    void getAllPayments_returnsEmptyList_whenNoPaymentsExist() {
        when(paymentRepository.findAll()).thenReturn(List.of());

        List<Payment> result = paymentService.getAllPayments();

        assertThat(result).isEmpty();
    }

    // --- getPaymentById ---

    @Test
    void getPaymentById_whenFound_returnsPayment() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void getPaymentById_whenNotFound_throws404ResponseStatusException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- getPaymentsByStatus ---

    @Test
    void getPaymentsByStatus_returnsOnlyMatchingPayments() {
        List<Payment> created = List.of(buildPayment(1L, PaymentStatus.CREATED));
        when(paymentRepository.findByStatus(PaymentStatus.CREATED)).thenReturn(created);

        List<Payment> result = paymentService.getPaymentsByStatus(PaymentStatus.CREATED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void getPaymentsByStatus_returnsEmpty_whenNoMatchingPayments() {
        when(paymentRepository.findByStatus(PaymentStatus.FAILED)).thenReturn(List.of());

        List<Payment> result = paymentService.getPaymentsByStatus(PaymentStatus.FAILED);

        assertThat(result).isEmpty();
    }

    // --- getPaymentHistoryByPaymentId ---

    @Test
    void getPaymentHistoryByPaymentId_whenPaymentExists_returnsHistory() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(1L);
        history.setOldStatus(PaymentStatus.CREATED);
        history.setNewStatus(PaymentStatus.VALIDATED);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentHistoryRepository.findByPaymentId(1L)).thenReturn(List.of(history));

        List<PaymentHistory> result = paymentService.getPaymentHistoryByPaymentId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOldStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.get(0).getNewStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }

    @Test
    void getPaymentHistoryByPaymentId_whenPaymentNotFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentHistoryByPaymentId(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- updatePaymentStatus ---

    @Test
    void updatePaymentStatus_updatesStatusOnPayment() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.updatePaymentStatus(1L, PaymentStatus.VALIDATED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
    }

    @Test
    void updatePaymentStatus_setsUpdatedAt() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.updatePaymentStatus(1L, PaymentStatus.VALIDATED);

        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatePaymentStatus_savesHistoryRecordWithCorrectStatuses() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.updatePaymentStatus(1L, PaymentStatus.VALIDATED);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());

        PaymentHistory saved = historyCaptor.getValue();
        assertThat(saved.getPaymentId()).isEqualTo(1L);
        assertThat(saved.getOldStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(saved.getNewStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    void updatePaymentStatus_whenPaymentNotFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(99L, PaymentStatus.VALIDATED))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- processPayment ---

    @Test
    void processPayment_whenOtpValid_debitsSenderCreditsReceiverAndCompletesPayment() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        payment.setAmount(100.0);
        Account fromAccount = buildAccount("ACC001", 500.0);
        Account toAccount = buildAccount("ACC002", 200.0);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate("ACC002")).thenReturn(Optional.of(toAccount));

        Payment result = paymentService.processPayment(1L, "123456");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(fromAccount.getBalance()).isEqualTo(400.0);
        assertThat(toAccount.getBalance()).isEqualTo(300.0);
        verify(accountRepository).save(fromAccount);
        verify(accountRepository).save(toAccount);
        verify(paymentValidator).validateSufficientFunds(fromAccount, 100.0);
    }

    @Test
    void processPayment_persistsSentThenCompletedHistoryTransitions() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        Account fromAccount = buildAccount("ACC001", 500.0);
        Account toAccount = buildAccount("ACC002", 200.0);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate("ACC002")).thenReturn(Optional.of(toAccount));

        paymentService.processPayment(1L, "123456");

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository, org.mockito.Mockito.times(2)).save(historyCaptor.capture());
        List<PaymentHistory> savedHistories = historyCaptor.getAllValues();
        assertThat(savedHistories.get(0).getNewStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(savedHistories.get(1).getNewStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void processPayment_whenOtpInvalid_marksPaymentFailedAndThrowsWithoutTouchingBalances() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "999999")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processPayment(1L, "999999"))
                .isInstanceOf(OtpVerificationFailedException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void processPayment_whenPaymentNotValidated_throwsInvalidStatusTransitionException() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verifyNoInteractions(otpVerificationService, accountRepository);
    }

    @Test
    void processPayment_whenInsufficientFundsAtTransferTime_doesNotSaveAccountsOrCompletePayment() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        payment.setAmount(1000.0);
        Account fromAccount = buildAccount("ACC001", 50.0);
        Account toAccount = buildAccount("ACC002", 200.0);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate("ACC002")).thenReturn(Optional.of(toAccount));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS"))
                .when(paymentValidator).validateSufficientFunds(fromAccount, 1000.0);

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(ResponseStatusException.class);

        verify(accountRepository, never()).save(any(Account.class));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SENT);
    }

    @Test
    void processPayment_whenSourceAccountMissing_throwsAccountNotFoundException() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).save(any(Account.class));
    }
}
