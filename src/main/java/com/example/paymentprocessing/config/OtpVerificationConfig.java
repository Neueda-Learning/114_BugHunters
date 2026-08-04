package com.example.paymentprocessing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.paymentprocessing.service.OtpVerificationService;

/**
 * Provides a temporary fallback {@link OtpVerificationService} bean so the
 * payment-processing flow is functional before real SMTP credentials are configured.
 *
 * <p>As soon as {@code EmailOtpVerificationService} is active (i.e. the
 * {@code JavaMailSender} bean is present), Spring will use that bean instead,
 * thanks to {@link ConditionalOnMissingBean}.
 *
 * <p><b>IMPORTANT:</b> this fallback only accepts a fixed development code and
 * must never be relied on in production.
 */
@Configuration
public class OtpVerificationConfig {

    private static final String DEV_FALLBACK_OTP_CODE = "000000";

    @Bean
    @ConditionalOnMissingBean(OtpVerificationService.class)
    public OtpVerificationService otpVerificationService() {
        return new OtpVerificationService() {
            @Override
            public void sendOtp(Long paymentId, String recipientEmail) {
                // dev stub: OTP is always DEV_FALLBACK_OTP_CODE — nothing is sent
            }

            @Override
            public boolean isOtpValid(Long paymentId, String otpCode) {
                return DEV_FALLBACK_OTP_CODE.equals(otpCode);
            }
        };
    }
}
