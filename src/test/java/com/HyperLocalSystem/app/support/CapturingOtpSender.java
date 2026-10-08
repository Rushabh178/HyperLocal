package com.HyperLocalSystem.app.support;

import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.HyperLocalSystem.app.auth.service.OtpSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only OtpSender that remembers the last code sent to each phone so tests can sign in. */
public class CapturingOtpSender implements OtpSender {

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String code, OtpPurpose purpose) {
        lastCodes.put(phone + "|" + purpose, code);
    }

    /** @param phone E.164, e.g. +919876543210 */
    public String last(String phone, OtpPurpose purpose) {
        String code = lastCodes.get(phone + "|" + purpose);
        if (code == null) {
            throw new IllegalStateException("No OTP was sent to " + phone + " for " + purpose);
        }
        return code;
    }
}
