package com.HyperLocalSystem.app.auth.service;

import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** DEVELOPMENT ONLY: writes the OTP to the server log instead of sending an SMS. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "log")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(String phone, String code, OtpPurpose purpose) {
        log.warn("[DEV OTP] {} for {} is {}", purpose, phone, code);
    }
}
