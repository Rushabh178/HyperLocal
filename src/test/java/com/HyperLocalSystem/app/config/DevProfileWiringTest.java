package com.HyperLocalSystem.app.config;

import com.HyperLocalSystem.app.notification.push.LoggingPushSender;
import com.HyperLocalSystem.app.notification.push.PushSender;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import com.HyperLocalSystem.app.payment.gateway.FakePaymentGateway;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The fakes exist only when "dev" is explicitly active; anywhere else real integrations are required. */
class DevProfileWiringTest {

    private static final AppProperties PROPERTIES = new AppProperties(null, null, null, null,
            new AppProperties.Payment("test-webhook-secret", Duration.ofMinutes(10), null), null, null, null, null);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RequiredIntegrationsConfig.class, FakePaymentGateway.class, LoggingPushSender.class)
            .withBean(AppProperties.class, () -> PROPERTIES)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void devProvidesTheFakes() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FakePaymentGateway.class).hasSingleBean(LoggingPushSender.class);
        });
    }

    @Test
    void withoutDevStartupFailsWhenNoRealIntegrationIsConfigured() {
        runner.run(context -> assertThat(context).hasFailed().getFailure()
                .hasMessageContaining("No PaymentGateway").hasMessageContaining("no PushSender"));
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void withoutDevRealIntegrationsAreUsedAndNoFakesExist() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .withBean("realGateway", PaymentGateway.class, () -> mock(PaymentGateway.class))
                .withBean("realPush", PushSender.class, () -> mock(PushSender.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FakePaymentGateway.class).doesNotHaveBean(LoggingPushSender.class);
                });
    }

    @Test
    void xFakePaymentIsIgnoredWithoutTheDevProfile() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FakePaymentGateway.OUTCOME_HEADER, "FAIL");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Even a fake gateway wired up outside dev ignores the header and uses the configured default...
        assertThat(holdStatus(gateway("prod"))).isEqualTo(PaymentStatus.HELD);
        assertThat(holdStatus(gateway())).isEqualTo(PaymentStatus.HELD);
        // ...while the same request in dev gets the outcome it asked for.
        assertThat(holdStatus(gateway("dev"))).isEqualTo(PaymentStatus.FAILED);
    }

    private static FakePaymentGateway gateway(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new FakePaymentGateway(PROPERTIES, new ObjectMapper(), environment, FakePaymentGateway.Outcome.SUCCESS);
    }

    private static PaymentStatus holdStatus(FakePaymentGateway gateway) {
        return gateway.createHold(UUID.randomUUID(), new BigDecimal("100.00")).status();
    }
}
