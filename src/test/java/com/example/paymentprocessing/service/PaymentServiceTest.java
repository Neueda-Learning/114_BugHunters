package com.example.paymentprocessing.service;

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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void createPayment_setsDefaultCreatedStatusAndTimestamps() {
        Payment payment = buildPayment(null, null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(payment);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(paymentValidator).validateNewPayment(payment);
    }

    @Test
    void updatePaymentStatus_rejectsInvalidTransition() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        doThrow(new InvalidStatusTransitionException("Cannot transition payment from CREATED to COMPLETED"))
                .when(paymentValidator).validateStatusTransition(PaymentStatus.CREATED, PaymentStatus.COMPLETED);

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(1L, PaymentStatus.COMPLETED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
    }

    @Test
    void validatePayment_happyPath_marksCreatedToValidated() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.validatePayment(1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.VALIDATED);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        PaymentHistory history = historyCaptor.getValue();
        assertThat(history.getOldStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(history.getRemarks()).contains("All payment validations passed");
    }

    @Test
    void validatePayment_validationFailure_marksCreatedToFailed() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT: Amount must be greater than 0"))
                .when(paymentValidator).validateAmount(anyDouble());

        assertThatThrownBy(() -> paymentService.validatePayment(1L))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("Payment validation failed");

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        PaymentHistory failedHistory = historyCaptor.getValue();
        assertThat(failedHistory.getOldStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(failedHistory.getNewStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failedHistory.getRemarks()).contains("Validation failed");
    }

    @Test
    void validatePayment_rejectsInvalidStartState() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.validatePayment(1L))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be in CREATED status");

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
    }

    @Test
    void processPayment_happyPath_validatedToSentToCompleted() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
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

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository, times(2)).save(historyCaptor.capture());
        List<PaymentHistory> history = historyCaptor.getAllValues();
        assertThat(history.get(0).getOldStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(history.get(0).getNewStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(history.get(0).getRemarks()).contains("OTP verification succeeded");
        assertThat(history.get(1).getOldStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(history.get(1).getNewStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(history.get(1).getRemarks()).contains("Account balance update completed successfully");
    }

    @Test
    void processPayment_validationFailure_rejectsNonValidatedPayment() {
        Payment payment = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be in VALIDATED status");

        verifyNoInteractions(otpVerificationService);
        verify(accountRepository, never()).findByAccountNumberForUpdate(anyString());
        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
    }

    @Test
    void processPayment_otpFailure_marksValidatedToFailedWithoutBalanceUpdate() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "999999")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processPayment(1L, "999999"))
                .isInstanceOf(OtpVerificationFailedException.class);

        verify(accountRepository, never()).findByAccountNumberForUpdate(anyString());

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(historyCaptor.capture());
        PaymentHistory history = historyCaptor.getValue();
        assertThat(history.getOldStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(history.getNewStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void processPayment_balanceUpdateFailure_marksSentToFailed() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        doThrow(new RuntimeException("Balance update failure"))
                .when(accountRepository).findByAccountNumberForUpdate("ACC001");

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("Payment processing failed");

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository, times(2)).save(historyCaptor.capture());
        List<PaymentHistory> history = historyCaptor.getAllValues();
        assertThat(history.get(0).getNewStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(history.get(1).getOldStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(history.get(1).getNewStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(history.get(1).getRemarks()).contains("Balance update failed");
    }

    @Test
    void processPayment_unexpectedErrorAfterBalanceUpdate_marksSentToFailed() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        Account fromAccount = buildAccount("ACC001", 500.0);
        Account toAccount = buildAccount("ACC002", 200.0);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberForUpdate("ACC002")).thenReturn(Optional.of(toAccount));

        final int[] historySaveCount = {0};
        doAnswer(invocation -> {
            historySaveCount[0]++;
            if (historySaveCount[0] == 2) {
                throw new RuntimeException("Post-balance-update failure");
            }
            return invocation.getArgument(0);
        }).when(paymentHistoryRepository).save(any(PaymentHistory.class));

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(PaymentProcessingException.class);

        ArgumentCaptor<PaymentHistory> historyCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository, times(2)).save(historyCaptor.capture());
        List<PaymentHistory> history = historyCaptor.getAllValues();
        PaymentHistory last = history.get(history.size() - 1);
        assertThat(last.getOldStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(last.getNewStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void processPayment_rejectsInvalidStartState() {
        Payment payment = buildPayment(1L, PaymentStatus.COMPLETED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("must be in VALIDATED status");

        verifyNoInteractions(otpVerificationService);
        verify(accountRepository, never()).findByAccountNumberForUpdate(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
    }

    @Test
    void processPayment_whenAccountMissing_throwsAccountNotFoundException() {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpVerificationService.isOtpValid(1L, "123456")).thenReturn(true);
        when(accountRepository.findByAccountNumberForUpdate("ACC001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(1L, "123456"))
                .isInstanceOf(PaymentProcessingException.class);
    }
}
