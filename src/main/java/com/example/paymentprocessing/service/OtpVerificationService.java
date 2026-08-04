package com.example.paymentprocessing.service;

/**
 * Contract for verifying the one-time-password (OTP) challenge that must succeed
 * before a payment's account balances are updated.
 *
 * <p>The real OTP generation/delivery mechanism (e.g. email OTP) is owned by a
 * separate workstream. This interface is the integration seam that
 * {@link PaymentService} depends on. Once the real implementation is registered
 * as a Spring bean, it automatically replaces the temporary fallback provided by
 * {@code com.example.paymentprocessing.config.OtpVerificationConfig}.
 */
public interface OtpVerificationService {

    /**
     * Verifies the OTP code supplied by the user for the given payment.
     *
     * @param paymentId the payment being processed
     * @param otpCode   the OTP code supplied by the caller
     * @return {@code true} if the OTP is valid, {@code false} otherwise
     */
    boolean isOtpValid(Long paymentId, String otpCode);
}
