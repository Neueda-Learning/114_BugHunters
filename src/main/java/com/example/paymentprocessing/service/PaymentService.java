package com.example.paymentprocessing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.example.paymentprocessing.enums.PaymentStatus;
import com.example.paymentprocessing.model.Payment;
import com.example.paymentprocessing.model.PaymentHistory;
import com.example.paymentprocessing.repository.PaymentHistoryRepository;
import com.example.paymentprocessing.repository.PaymentRepository;
import com.example.paymentprocessing.validation.PaymentValidator;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentValidator paymentValidator;

    public PaymentService(PaymentRepository paymentRepository, PaymentHistoryRepository paymentHistoryRepository,
            PaymentValidator paymentValidator) {
        this.paymentRepository = paymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.paymentValidator = paymentValidator;
    }

    public Payment createPayment(Payment payment) {
        paymentValidator.validateNewPayment(payment);

        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        payment.setStatus(PaymentStatus.CREATED);

        // Idempotency key is generated server-side; clients cannot supply their own.
        payment.setKey(UUID.randomUUID().toString());

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

    public Payment updatePaymentStatus(Long id, PaymentStatus newStatus) {
        Payment payment = getPaymentById(id);
        PaymentStatus oldStatus = payment.getStatus();

        paymentValidator.validateStatusTransition(oldStatus, newStatus);

        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());
        Payment updated = paymentRepository.save(payment);

        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(id);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        paymentHistoryRepository.save(history);

        return updated;
    }
}