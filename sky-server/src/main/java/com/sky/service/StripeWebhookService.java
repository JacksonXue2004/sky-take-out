package com.sky.service;

public interface StripeWebhookService {

    enum Outcome {
        PROCESSED,
        /** Stripe delivered an event that was already handled. */
        DUPLICATE,
        IGNORED,
        REJECTED,
        /** Processing failed; answer with an error so that Stripe delivers the event again. */
        FAILED
    }

    /** Handles a Stripe event whose signature has already been verified. */
    Outcome handle(String payload);
}
