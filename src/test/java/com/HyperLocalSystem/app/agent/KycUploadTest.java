package com.HyperLocalSystem.app.agent;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 8: KYC upload through a signed link; files are private to the partner and admins. */
class KycUploadTest extends IntegrationTest {

    @Test
    void partnerUploadsThroughSignedLink() throws Exception {
        Session rider = signUpAgent();
        String link = post("/api/v1/agent/me/documents/PAN/upload-url", rider.token(), "{\"contentType\":\"image/png\"}");
        String uploadUrl = read(link, "$.uploadUrl");
        String fileUrl = read(link, "$.fileUrl");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        // Wrong content type and tampered signatures are refused; the signed PUT works without a token.
        assertThat(mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl)).contentType("image/jpeg").content(png)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl.replace("sig=", "sig=x"))).contentType("image/png").content(png))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(MockMvcRequestBuilders.put(URI.create(uploadUrl)).contentType("image/png").content(png)).andReturn()
                .getResponse().getStatus()).isEqualTo(204);

        put("/api/v1/agent/me/documents/PAN", rider.token(), "{\"documentNumber\":\"ABCDE1234F\",\"fileUrl\":\"%s\"}".formatted(fileUrl));

        String path = URI.create(fileUrl).getPath();
        MvcResult own = mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + rider.token())).andReturn();
        assertThat(own.getResponse().getContentAsByteArray()).isEqualTo(png);
        MvcResult admin = mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + adminToken())).andReturn();
        assertThat(admin.getResponse().getStatus()).isEqualTo(200);
        call(HttpMethod.GET, path, signUpAgent().token(), null, 403);
    }
}
