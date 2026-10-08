package com.HyperLocalSystem.app.auth.service;

import com.HyperLocalSystem.app.auth.entity.OtpPurpose;

/** Delivery channel for OTPs. Add an SMS gateway implementation (MSG91, Twilio, ...) for production. */
public interface OtpSender {

    void send(String phone, String code, OtpPurpose purpose);
}
