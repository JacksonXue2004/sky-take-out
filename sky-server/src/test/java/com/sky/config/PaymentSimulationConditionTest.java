package com.sky.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSimulationConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SimulationConfig.class);

    @Test
    void theFlagTurnsTheSimulationOnInAnyProfile() {
        runner.withPropertyValues("sky.payment.simulation-enabled=true", "spring.profiles.active=prod")
                .run(context -> assertThat(context).hasSingleBean(SimulatedCheckout.class));
    }

    @Test
    void theFlagTurnsTheSimulationOffEvenInTheDevProfile() {
        runner.withPropertyValues("sky.payment.simulation-enabled=false", "spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(SimulatedCheckout.class));
    }

    @Test
    void withoutTheFlagOnlyTheDevProfileHasTheSimulation() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(SimulatedCheckout.class));
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(SimulatedCheckout.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(SimulatedCheckout.class));
    }

    @Configuration
    static class SimulationConfig {
        @Bean
        @Conditional(PaymentSimulationCondition.class)
        SimulatedCheckout simulatedCheckout() {
            return new SimulatedCheckout();
        }
    }

    static class SimulatedCheckout {
    }
}
