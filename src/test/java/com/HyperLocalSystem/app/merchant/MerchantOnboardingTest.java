package com.HyperLocalSystem.app.merchant;

import com.HyperLocalSystem.app.merchant.service.StoreQrService;
import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 1: a Kirana signs up, gets approved, opens the store and shows its pickup QR. */
class MerchantOnboardingTest extends IntegrationTest {

    @Autowired
    StoreQrService storeQrService;

    @Test
    void kiranaSignsUpGetsApprovedOpensAndShowsQr() throws Exception {
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(0, 0), false);

        String me = get("/api/v1/merchant/me", kirana.token());
        assertThat((String) read(me, "$.verificationStatus")).isEqualTo("PENDING");
        assertThat((Boolean) read(me, "$.open")).isFalse();

        // Can't open before approval.
        String refused = call(HttpMethod.PUT, "/api/v1/merchant/me/store-status", kirana.token(), "{\"open\":true}", 403);
        assertThat((String) read(refused, "$.code")).isEqualTo("MERCHANT_NOT_APPROVED");

        jdbc.update("update merchants set approval_status = 'APPROVED' where merchant_id = ?", kirana.id());
        String opened = put("/api/v1/merchant/me/store-status", kirana.token(), "{\"open\":true}");
        assertThat((Boolean) read(opened, "$.open")).isTrue();

        String qr = get("/api/v1/merchant/store-qr", kirana.token());
        String payload = read(qr, "$.qrPayload");
        assertThat(storeQrService.verify(payload)).isEqualTo(kirana.id());

        // Password login works with the phone number.
        String login = post("/api/v1/merchant/auth/login", null,
                "{\"identifier\":\"%s\",\"password\":\"secret-pass-1\"}".formatted(kirana.phone()));
        assertThat((String) read(login, "$.role")).isEqualTo("MERCHANT");
        assertThat(UUID.fromString(read(login, "$.user.id"))).isEqualTo(kirana.id());
    }

    @Test
    void restaurantNeedsFssaiAndOtherRolesAreLockedOut() throws Exception {
        String noFssai = call(HttpMethod.POST, "/api/v1/merchant/auth/signup", null, """
                {"type":"RESTAURANT","storeName":"Spice Hub","ownerName":"Owner","phone":"%s","password":"secret-pass-1",
                 "address":"2 Market Road","pincode":"411001","location":%s}""".formatted(randomPhone(), point(0, 0)), 400);
        assertThat((String) read(noFssai, "$.code")).isEqualTo("FSSAI_REQUIRED");

        Session customer = signUpCustomer();
        call(HttpMethod.GET, "/api/v1/merchant/me", customer.token(), null, 403);
    }
}
