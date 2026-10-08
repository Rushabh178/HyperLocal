package com.HyperLocalSystem.app.admin;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 7: the seeded admin signs in and reviews a delivery partner's KYC and new stores. */
class AdminReviewTest extends IntegrationTest {

    @Test
    void adminReviewsPartnerAndStores() throws Exception {
        String admin = adminToken();

        Session rider = signUpAgent();
        for (String type : List.of("AADHAAR", "PAN", "PROFILE_PHOTO")) {
            put("/api/v1/agent/me/documents/" + type, rider.token(), "{\"documentNumber\":\"ABCDE1234F\",\"fileUrl\":\"https://files/x\"}");
        }
        assertThat((String) read(get("/api/v1/agent/me", rider.token()), "$.verificationStatus")).isEqualTo("UNDER_REVIEW");
        String queue = get("/api/v1/admin/agents?status=UNDER_REVIEW&size=100", admin);
        assertThat((List<String>) read(queue, "$.items[*].id")).contains(rider.id().toString());

        // A bad PAN goes back to the partner, who re-uploads it.
        String sentBack = post("/api/v1/admin/agents/" + rider.id() + "/documents/PAN/reject", admin, "{\"reason\":\"Blurry photo\"}");
        assertThat((String) read(sentBack, "$.verificationStatus")).isEqualTo("PENDING_DOCUMENTS");
        assertThat((List<String>) read(sentBack, "$.missingDocuments")).containsExactly("PAN");
        put("/api/v1/agent/me/documents/PAN", rider.token(), "{\"documentNumber\":\"ABCDE1234F\",\"fileUrl\":\"https://files/y\"}");

        String approved = post("/api/v1/admin/agents/" + rider.id() + "/approve", admin, null);
        assertThat((String) read(approved, "$.verificationStatus")).isEqualTo("APPROVED");
        assertThat((String) read(approved, "$.thermalBoxId")).startsWith("TB-");
        assertThat((String) read(put("/api/v1/agent/me/duty", rider.token(), "{\"online\":true}"), "$.dutyStatus")).isEqualTo("AVAILABLE");

        // Stores: one approved (can open), one rejected (can't).
        Session good = signUpMerchant("KIRANA", "Good Store", point(0, 0), false);
        Session bad = signUpMerchant("KIRANA", "Bad Store", point(0, 100), false);
        assertThat((List<String>) read(get("/api/v1/admin/merchants?status=PENDING&size=100", admin), "$.items[*].id"))
                .contains(good.id().toString(), bad.id().toString());
        post("/api/v1/admin/merchants/" + good.id() + "/approve", admin, null);
        post("/api/v1/admin/merchants/" + bad.id() + "/reject", admin, "{\"reason\":\"Address could not be verified\"}");
        assertThat((Boolean) read(put("/api/v1/merchant/me/store-status", good.token(), "{\"open\":true}"), "$.open")).isTrue();
        call(HttpMethod.PUT, "/api/v1/merchant/me/store-status", bad.token(), "{\"open\":true}", 403);
        assertThat((String) read(get("/api/v1/merchant/me", bad.token()), "$.rejectionReason")).isEqualTo("Address could not be verified");

        // Roles stay separate in both directions.
        call(HttpMethod.GET, "/api/v1/admin/agents", rider.token(), null, 403);
        call(HttpMethod.GET, "/api/v1/customer/me", admin, null, 403);
    }
}
