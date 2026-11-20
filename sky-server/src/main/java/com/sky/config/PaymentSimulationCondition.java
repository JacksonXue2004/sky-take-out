package com.sky.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Decides whether the simulated checkout exists. It marks orders paid without taking any money,
 * so it must never be switched on by accident.
 * <p>
 * The property sky.payment.simulation-enabled decides whenever it is set. When it is not set,
 * only the dev profile turns the simulation on, which keeps local development working as before.
 */
public class PaymentSimulationCondition implements Condition {

    static final String PROPERTY = "sky.payment.simulation-enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment().getProperty(PROPERTY);
        if (value != null && !value.trim().isEmpty()) {
            return Boolean.parseBoolean(value.trim());
        }
        return context.getEnvironment().acceptsProfiles(Profiles.of("dev"));
    }
}
