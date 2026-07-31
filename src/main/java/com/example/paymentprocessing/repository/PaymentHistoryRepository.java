package com.example.paymentprocessing.repository;

import java.util.List;
import com.example.paymentprocessing.model.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findByPaymentId(Long paymentId);
}
