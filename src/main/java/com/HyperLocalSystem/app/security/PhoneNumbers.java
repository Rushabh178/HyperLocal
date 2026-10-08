package com.HyperLocalSystem.app.security;

/** Indian mobile numbers, stored in E.164 form (+91XXXXXXXXXX) so lookups are consistent. */
public final class PhoneNumbers {

    /** Accepts 9876543210, +919876543210, 919876543210, "+91 98765 43210", "98765-43210". Use on request DTOs. */
    public static final String INDIAN_MOBILE_REGEX = "^(\\+?91)?[\\s-]*[6-9](?:[\\s-]*\\d){9}$";

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        return "+91" + digits;
    }
}
