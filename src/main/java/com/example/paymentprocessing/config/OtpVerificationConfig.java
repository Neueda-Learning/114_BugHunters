package com.example.paymentprocessing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.paymentprocessing.service.OtpVerificationService;

/**
 * Provides a temporary fallback {@link OtpVerificationService} bean so the
 * payment-processing flow is functional before the real email-OTP feature
 * (owned by another workstream) is merged.
 *
 * <p>As soon as a teammate registers their own {@code OtpVerificationService}
 * bean, Spring will use that one instead of this fallback, thanks to
 * {@link ConditionalOnMissingBean}.
 *
 * <p><b>IMPORTANT:</b> this fallback only accepts a fixed development code and
 * must never be relied on in production. Remove this class once real OTP
 * verification is implemented.
 */
@Configuration
public class OtpVerificationConfig {

    private static final String DEV_FALLBACK_OTP_CODE = "000000";

    @Bean
    @ConditionalOnMissingBean(OtpVerificationService.class)
    public OtpVerificationService otpVerificationService() {
        return (paymentId, otpCode) -> DEV_FALLBACK_OTP_CODE.equals(otpCode);
    }
}
