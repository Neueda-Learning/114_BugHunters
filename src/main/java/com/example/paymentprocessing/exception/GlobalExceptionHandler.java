package com.example.paymentprocessing.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayment(DuplicatePaymentException ex) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", ex.getMessage());
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessing(PaymentProcessingException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_PROCESSING_ERROR", ex.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", ex.getMessage());
    }

    @ExceptionHandler(OtpVerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handleOtpVerificationFailed(OtpVerificationFailedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "OTP_VERIFICATION_FAILED", ex.getMessage());
    }

    // Preserves the status/reason carried by ResponseStatusException (e.g. thrown by
    // PaymentValidator) instead of letting it fall through to the generic 500 handler.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String reason = ex.getReason() != null ? ex.getReason() : status.toString();
        return buildResponse(HttpStatus.valueOf(status.value()), "REQUEST_FAILED", reason);
    }

    // Bean Validation (@Valid) failures on @RequestBody payloads -> 400 instead of 500.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorCode, String message) {
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message, LocalDateTime.now());
        return ResponseEntity.status(status).body(errorResponse);
    }
}

