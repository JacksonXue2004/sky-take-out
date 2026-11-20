package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A Stripe event whose signature checked out, kept for de-duplication and auditing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    /** Stored before processing starts; still RECEIVED afterwards means the process died midway. */
    public static final String RECEIVED = "RECEIVED";
    /** The event changed an order. */
    public static final String PROCESSED = "PROCESSED";
    /** Valid, but there was nothing to do (another event type, a payment not cleared yet, a repeat). */
    public static final String IGNORED = "IGNORED";
    /** Signed by Stripe but unusable (wrong amount, unknown order, malformed); needs a person to look at it. */
    public static final String REJECTED = "REJECTED";
    /** Processing threw, for example because the database was down. Stripe retries these. */
    public static final String FAILED = "FAILED";

    private Long id;

    /** Stripe's evt_... id; null only when the payload could not be parsed at all. */
    private String eventId;

    private String type;

    private String status;

    private String orderNumber;

    private String error;

    private String payload;

    /** How many deliveries of this event reached processing. */
    private Integer attempts;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
