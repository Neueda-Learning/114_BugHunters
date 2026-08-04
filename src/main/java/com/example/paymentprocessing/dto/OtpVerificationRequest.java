package com.example.paymentprocessing.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/payments/{id}/process}.
 */
public class OtpVerificationRequest {

    @NotBlank(message = "OTP code is required")
    private String otpCode;

    public OtpVerificationRequest() {
    }

    public OtpVerificationRequest(String otpCode) {
        this.otpCode = otpCode;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
