package com.HyperLocalSystem.app.config;

import com.HyperLocalSystem.app.notification.push.PushSender;
import com.HyperLocalSystem.app.payment.gateway.PaymentGateway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails startup when no {@link PaymentGateway} or {@link PushSender} is configured. The fake gateway and
 * the logging push sender exist only in the "dev" profile, so every other environment must pick real
 * ones (e.g. {@code SPRING_PROFILES_ACTIVE=prod,razorpay,fcm}). Runs on the bean definitions, before
 * anything is created, so the error names the missing integration instead of whichever bean needed it.
 */
@Configuration(proxyBeanMethods = false)
public class RequiredIntegrationsConfig {

    @Bean
    static BeanFactoryPostProcessor requiredIntegrationsCheck() {
        return beanFactory -> {
            List<String> missing = new ArrayList<>();
            if (beanFactory.getBeanNamesForType(PaymentGateway.class, true, false).length == 0) {
                missing.add("PaymentGateway (activate the \"razorpay\" profile)");
            }
            if (beanFactory.getBeanNamesForType(PushSender.class, true, false).length == 0) {
                missing.add("PushSender (activate the \"fcm\" profile)");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("No " + String.join(" and no ", missing)
                        + " is configured. Fakes are only available with the \"dev\" profile, which is never on by default.");
            }
        };
    }
}
