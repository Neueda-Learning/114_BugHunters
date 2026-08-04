package com.example.paymentprocessing.exception;

public class OtpVerificationFailedException extends RuntimeException {
    public OtpVerificationFailedException(String message) {
        super(message);
    }
}
