package com.example.paymentprocessing.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email-based OTP implementation. Generates a cryptographically random 6-digit
 * code, stores it in memory with an expiry, and delivers it via SMTP.
 *
 * <p>Each OTP is single-use: it is removed from the store immediately upon a
 * successful verification. Expired entries are also purged on access.
 */
@Service
public class EmailOtpVerificationService implements OtpVerificationService {

    private record OtpEntry(String code, LocalDateTime expiresAt) {}

    private final ConcurrentHashMap<Long, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    public EmailOtpVerificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendOtp(Long paymentId, String recipientEmail) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpStore.put(paymentId, new OtpEntry(code, LocalDateTime.now().plusMinutes(expiryMinutes)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject("Payment OTP Verification – Payment #" + paymentId);
        message.setText(
                "Your one-time password for payment #" + paymentId + " is:\n\n"
                + "    " + code + "\n\n"
                + "This code expires in " + expiryMinutes + " minutes.\n"
                + "If you did not initiate this payment, please contact support immediately.");
        mailSender.send(message);
    }

    @Override
    public boolean isOtpValid(Long paymentId, String otpCode) {
        OtpEntry entry = otpStore.get(paymentId);
        if (entry == null || LocalDateTime.now().isAfter(entry.expiresAt())) {
            otpStore.remove(paymentId);
            return false;
        }
        boolean valid = entry.code().equals(otpCode);
        if (valid) {
            otpStore.remove(paymentId); // single-use: remove after successful verification
        }
        return valid;
    }
}
