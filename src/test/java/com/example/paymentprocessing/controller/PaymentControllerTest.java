package com.example.paymentprocessing.controller;

import com.example.paymentprocessing.enums.PaymentStatus;
import com.example.paymentprocessing.model.Payment;
import com.example.paymentprocessing.model.PaymentHistory;
import com.example.paymentprocessing.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.HttpStatus;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final String PAYMENT_JSON =
            "{\"amount\":250.0,\"currency\":\"USD\",\"accountFrom\":\"ACC001\",\"accountTo\":\"ACC002\",\"type\":\"TRANSFER\"}";

    private Payment buildPayment(Long id, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(id);
        p.setAmount(250.00);
        p.setCurrency("USD");
        p.setAccountFrom("ACC001");
        p.setAccountTo("ACC002");
        p.setStatus(status);
        p.setType("TRANSFER");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    // --- POST /api/payments ---

    @Test
    void createPayment_returns201WithCreatedPayment() throws Exception {
        Payment created = buildPayment(1L, PaymentStatus.CREATED);
        when(paymentService.createPayment(any(Payment.class))).thenReturn(created);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    // --- GET /api/payments ---

    @Test
    void getAllPayments_noStatusFilter_returns200WithFullList() throws Exception {
        List<Payment> payments = List.of(
                buildPayment(1L, PaymentStatus.CREATED),
                buildPayment(2L, PaymentStatus.SENT));
        when(paymentService.getAllPayments()).thenReturn(payments);

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllPayments_withStatusFilter_returns200WithFilteredList() throws Exception {
        List<Payment> payments = List.of(buildPayment(1L, PaymentStatus.CREATED));
        when(paymentService.getPaymentsByStatus(PaymentStatus.CREATED)).thenReturn(payments);

        mockMvc.perform(get("/api/payments").param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
    }

    @Test
    void getAllPayments_returnsEmptyList_whenNoPaymentsExist() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of());

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- GET /api/payments/{id} ---

    @Test
    void getPaymentById_returns200WithPayment() throws Exception {
        Payment payment = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentService.getPaymentById(1L)).thenReturn(payment);

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void getPaymentById_whenNotFound_returns404() throws Exception {
        when(paymentService.getPaymentById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with id: 99"));

        mockMvc.perform(get("/api/payments/99"))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/payments/{id}/history ---

    @Test
    void getPaymentHistory_returns200WithHistoryList() throws Exception {
        PaymentHistory history = new PaymentHistory();
        history.setPaymentId(1L);
        history.setOldStatus(PaymentStatus.CREATED);
        history.setNewStatus(PaymentStatus.VALIDATED);

        when(paymentService.getPaymentHistoryByPaymentId(1L)).thenReturn(List.of(history));

        mockMvc.perform(get("/api/payments/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oldStatus").value("CREATED"))
                .andExpect(jsonPath("$[0].newStatus").value("VALIDATED"));
    }

    @Test
    void getPaymentHistory_whenPaymentNotFound_returns404() throws Exception {
        when(paymentService.getPaymentHistoryByPaymentId(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with id: 99"));

        mockMvc.perform(get("/api/payments/99/history"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/payments/{id}/status ---

    @Test
    void updatePaymentStatus_returns200WithUpdatedPayment() throws Exception {
        Payment updated = buildPayment(1L, PaymentStatus.VALIDATED);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.VALIDATED))).thenReturn(updated);

        mockMvc.perform(patch("/api/payments/1/status").param("status", "VALIDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void updatePaymentStatus_throughFullLifecycle_sendsToCompleted() throws Exception {
        Payment sent = buildPayment(1L, PaymentStatus.SENT);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.SENT))).thenReturn(sent);

        mockMvc.perform(patch("/api/payments/1/status").param("status", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        Payment completed = buildPayment(1L, PaymentStatus.COMPLETED);
        when(paymentService.updatePaymentStatus(eq(1L), eq(PaymentStatus.COMPLETED))).thenReturn(completed);

        mockMvc.perform(patch("/api/payments/1/status").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void updatePaymentStatus_whenPaymentNotFound_returns404() throws Exception {
        when(paymentService.updatePaymentStatus(eq(99L), any(PaymentStatus.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with id: 99"));

        mockMvc.perform(patch("/api/payments/99/status").param("status", "VALIDATED"))
                .andExpect(status().isNotFound());
    }
}
