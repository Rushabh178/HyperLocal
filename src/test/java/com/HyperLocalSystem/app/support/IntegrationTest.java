package com.HyperLocalSystem.app.support;

import com.HyperLocalSystem.app.auth.entity.OtpPurpose;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Base class for end-to-end API tests: full Spring context, real PostGIS (Testcontainers), MockMvc.
 * Every test class gets its own random spot on the map so geo queries from different classes
 * sharing the database never see each other's merchants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public record Session(UUID id, String token, String phone) {
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected CapturingOtpSender otps;

    @Autowired
    protected JdbcTemplate jdbc;

    /** A random point in India, far enough from other test classes' points to never be within 2 km. */
    protected final double baseLat = 10 + ThreadLocalRandom.current().nextDouble() * 15;
    protected final double baseLng = 73 + ThreadLocalRandom.current().nextDouble() * 10;

    // ----------------------------------------------------------------- HTTP helpers

    protected String call(HttpMethod method, String url, String token, String json, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            builder.content(json);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(method + " " + url + " -> " + body).isEqualTo(expectedStatus);
        return body;
    }

    protected String get(String url, String token) throws Exception {
        return call(HttpMethod.GET, url, token, null, 200);
    }

    protected String post(String url, String token, String json) throws Exception {
        return call(HttpMethod.POST, url, token, json, 200);
    }

    protected String put(String url, String token, String json) throws Exception {
        return call(HttpMethod.PUT, url, token, json, 200);
    }

    protected static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    // ----------------------------------------------------------------- accounts

    protected static String randomPhone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    /** Offsets {@code (baseLat, baseLng)} by roughly {@code northMeters} / {@code eastMeters}. */
    protected String point(double northMeters, double eastMeters) {
        double lat = baseLat + northMeters / 111_320.0;
        double lng = baseLng + eastMeters / (111_320.0 * Math.cos(Math.toRadians(baseLat)));
        return "{\"lat\":%s,\"lng\":%s}".formatted(lat, lng);
    }

    /** The same offset as {@link #point} but as {@code lat=..&lng=..} query parameters. */
    protected String latLng(double northMeters, double eastMeters) {
        double lat = baseLat + northMeters / 111_320.0;
        double lng = baseLng + eastMeters / (111_320.0 * Math.cos(Math.toRadians(baseLat)));
        return "lat=%s&lng=%s".formatted(lat, lng);
    }

    protected Session signUpCustomer() throws Exception {
        String phone = randomPhone();
        String signup = call(HttpMethod.POST, "/api/v1/customer/auth/signup", null, """
                {"fullName":"Test Customer","phone":"%s","password":"secret-pass-1"}""".formatted(phone), 201);
        return verify("/api/v1/customer/auth/verify-phone", phone, read(signup, "$.userId"));
    }

    /** Signs a store up at {@code location} (a {@link #point} JSON) and, if asked, approves and opens it. */
    protected Session signUpMerchant(String type, String storeName, String location, boolean approveAndOpen) throws Exception {
        String phone = randomPhone();
        String signup = call(HttpMethod.POST, "/api/v1/merchant/auth/signup", null, """
                {"type":"%s","storeName":"%s","ownerName":"Owner","phone":"%s","password":"secret-pass-1",
                 "address":"1 Market Road","pincode":"411001","location":%s,"fssaiNumber":"12345678901234",
                 "cuisines":["North Indian","Chinese"]}""".formatted(type, storeName, phone, location), 201);
        Session session = verify("/api/v1/merchant/auth/verify-phone", phone, read(signup, "$.userId"));
        if (approveAndOpen) {
            jdbc.update("update merchants set approval_status = 'APPROVED' where merchant_id = ?", session.id());
            put("/api/v1/merchant/me/store-status", session.token(), "{\"open\":true}");
        }
        return session;
    }

    /** Adds an available item to the merchant's menu/shelf and returns its id. */
    protected String createItem(String merchantToken, String name, String price, String category, String zone) throws Exception {
        String item = call(HttpMethod.POST, "/api/v1/merchant/items", merchantToken, """
                {"name":"%s","price":%s,"category":"%s","zone":"%s","veg":true,"available":true,
                 "packSize":"500 g","brand":"Acme","mrp":%s}""".formatted(name, price, category, zone, price), 201);
        return read(item, "$.id");
    }

    private Session verify(String url, String phone, String userId) throws Exception {
        String otp = otps.last("+91" + phone, OtpPurpose.SIGNUP_VERIFY);
        String auth = post(url, null, "{\"phone\":\"%s\",\"otp\":\"%s\"}".formatted(phone, otp));
        return new Session(UUID.fromString(userId), read(auth, "$.accessToken"), phone);
    }
}
