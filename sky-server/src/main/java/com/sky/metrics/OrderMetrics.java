package com.sky.metrics;

import com.sky.utils.StripePayUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics, readable at /actuator/metrics/&lt;name&gt; on the management port.
 * <ul>
 *     <li>orders.placed: orders created at checkout (an idempotent replay does not count)</li>
 *     <li>stripe.webhook.events, tagged outcome: processed, duplicate, ignored, rejected, failed,
 *     invalid_signature</li>
 *     <li>refunds, tagged outcome: succeeded, retry_scheduled, failed</li>
 *     <li>stripe.circuit.open: 1 while the circuit breaker is holding Stripe calls back</li>
 * </ul>
 * A rise in rejected webhooks, failed refunds or an open circuit is what someone should be alerted on.
 */
@Component
public class OrderMetrics {

    private final MeterRegistry registry;
    private final Counter ordersPlaced;

    public OrderMetrics(MeterRegistry registry, StripePayUtil stripePayUtil) {
        this.registry = registry;
        this.ordersPlaced = Counter.builder("orders.placed")
                .description("Orders created at checkout")
                .register(registry);
        Gauge.builder("stripe.circuit.open", stripePayUtil, stripe -> "OPEN".equals(stripe.circuitBreakerState()) ? 1 : 0)
                .description("1 while Stripe calls are paused by the circuit breaker")
                .strongReference(true)
                .register(registry);
    }

    public void orderPlaced() {
        ordersPlaced.increment();
    }

    public void webhookEvent(String outcome) {
        registry.counter("stripe.webhook.events", "outcome", outcome).increment();
    }

    public void refund(String outcome) {
        registry.counter("refunds", "outcome", outcome).increment();
    }
}
