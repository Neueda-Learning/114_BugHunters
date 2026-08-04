package com.example.paymentprocessing.service;

/**
 * Contract for generating, delivering, and verifying the one-time-password (OTP)
 * challenge that must succeed before a payment's account balances are updated.
 */
public interface OtpVerificationService {

    /**
     * Generates an OTP for the given payment and sends it to the recipient email.
     *
     * @param paymentId      the payment being processed
     * @param recipientEmail the email address to deliver the OTP to
     */
    void sendOtp(Long paymentId, String recipientEmail);

    /**
     * Verifies the OTP code supplied by the user for the given payment.
     *
     * @param paymentId the payment being processed
     * @param otpCode   the OTP code supplied by the caller
     * @return {@code true} if the OTP is valid and not expired, {@code false} otherwise
     */
    boolean isOtpValid(Long paymentId, String otpCode);
}
