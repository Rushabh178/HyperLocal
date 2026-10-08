package com.HyperLocalSystem.app.config;

import com.HyperLocalSystem.app.security.JsonAuthErrorHandler;
import com.HyperLocalSystem.app.security.Role;
import com.HyperLocalSystem.app.security.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Stateless JWT security. Each app gets its own URL space and role:
 * <pre>
 *   /api/v1/customer/auth/**  public      /api/v1/customer/**  ROLE_CUSTOMER
 *   /api/v1/agent/auth/**     public      /api/v1/agent/**     ROLE_DELIVERY_AGENT
 *   /api/v1/merchant/auth/**  public      /api/v1/merchant/**  ROLE_MERCHANT
 *   /api/v1/admin/auth/**     public      /api/v1/admin/**     ROLE_ADMIN
 * </pre>
 * A customer token can never call a delivery-partner endpoint and vice versa.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthErrorHandler authErrors,
                                           JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/customer/auth/**", "/api/v1/agent/auth/**", "/api/v1/merchant/auth/**",
                                "/api/v1/admin/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        // Payment gateway callback: authenticated by its signature header, not a JWT.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // STOMP CONNECT frames carry the JWT and are checked in WebSocketConfig.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // KYC uploads: authorised by the signed, short-lived URL itself (see FileStorage).
                        .requestMatchers(HttpMethod.PUT, "/api/v1/files/upload").permitAll()
                        .requestMatchers("/api/v1/customer/**", "/api/v1/payments/**").hasRole(Role.CUSTOMER.name())
                        .requestMatchers("/api/v1/agent/**").hasRole(Role.DELIVERY_AGENT.name())
                        .requestMatchers("/api/v1/merchant/**").hasRole(Role.MERCHANT.name())
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authErrors)
                        .accessDeniedHandler(authErrors))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authErrors)
                        .accessDeniedHandler(authErrors));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.jwt().secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be Base64 of at least 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    /** Maps the JWT "role" claim to ROLE_* authorities. Shared with the WebSocket CONNECT handler. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
