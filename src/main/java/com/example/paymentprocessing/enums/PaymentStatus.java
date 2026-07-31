package com.example.paymentprocessing.enums;

/**
 * Represents the lifecycle states of a payment.
 *
 * CREATED -> VALIDATED -> SENT -> COMPLETED
 *                    \-> FAILED (can occur at any stage)
 */
public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED
}
