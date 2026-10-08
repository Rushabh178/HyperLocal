package com.HyperLocalSystem.app.merchant;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** §6: uploads (public photos, private documents), profile PATCH, documents and admin review. */
class MerchantUploadsTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 4, 5, 6};

    @Test
    void storeUploadsPhotoAndDocuments() throws Exception {
        // Uploads need a token, so they come right after verify-phone, before approval.
        Session store = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), false);

        // Store photo: public, saved on the profile with the hours.
        String photo = post("/api/v1/merchant/uploads", store.token(), "{\"purpose\":\"STORE_PHOTO\",\"contentType\":\"image/png\"}");
        assertThat((Boolean) read(photo, "$.publicRead")).isTrue();
        assertThat((String) read(photo, "$.fileKey")).startsWith("public/merchants/" + store.id());
        upload(read(photo, "$.uploadUrl"), "image/png");
        String photoUrl = read(photo, "$.fileUrl");
        String profile = call(HttpMethod.PATCH, "/api/v1/merchant/me", store.token(),
                "{\"imageUrl\":\"%s\",\"openingHours\":\"09:00-23:00\",\"storeName\":\"Spice Hub Express\"}".formatted(photoUrl), 200);
        assertThat((String) read(profile, "$.imageUrl")).isEqualTo(photoUrl);
        assertThat((String) read(profile, "$.openingHours")).isEqualTo("09:00-23:00");
        assertThat(mvc.perform(MockMvcRequestBuilders.get(URI.create(photoUrl).getPath())).andReturn().getResponse().getStatus())
                .as("anyone can see store photos").isEqualTo(200);

        // FSSAI certificate: private document.
        String doc = post("/api/v1/merchant/uploads", store.token(), "{\"purpose\":\"DOCUMENT\",\"contentType\":\"application/pdf\"}");
        assertThat((Boolean) read(doc, "$.publicRead")).isFalse();
        upload(read(doc, "$.uploadUrl"), "application/pdf");
        String docUrl = read(doc, "$.fileUrl");
        String docs = put("/api/v1/merchant/me/documents/FSSAI_CERT", store.token(),
                "{\"documentNumber\":\"12345678901234\",\"fileUrl\":\"%s\"}".formatted(docUrl));
        assertThat((List<String>) read(docs, "$.missingDocuments")).containsExactlyInAnyOrder("SHOP_PHOTO", "OWNER_ID");
        assertThat((String) read(docs, "$.documents[0].documentNumberMasked")).isEqualTo("XXXXXXXXXX1234");

        String docPath = URI.create(docUrl).getPath();
        call(HttpMethod.GET, docPath, null, null, 401);
        call(HttpMethod.GET, docPath, signUpMerchant("KIRANA", "Other", point(0, 50), false).token(), null, 403);
        call(HttpMethod.GET, docPath, store.token(), null, 200);

        // Admin reviews the documents and opens the file, then approves.
        String admin = adminToken();
        String review = get("/api/v1/admin/merchants/" + store.id() + "/documents", admin);
        assertThat((String) read(review, "$.documents[0].type")).isEqualTo("FSSAI_CERT");
        call(HttpMethod.GET, docPath, admin, null, 200);
        post("/api/v1/admin/merchants/" + store.id() + "/approve", admin, null);
        assertThat((String) read(get("/api/v1/merchant/me/documents", store.token()), "$.documents[0].status")).isEqualTo("VERIFIED");
    }

    private void upload(String uploadUrl, String contentType) throws Exception {
        int status = mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl)).contentType(contentType).content(PNG))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(204);
    }
}
