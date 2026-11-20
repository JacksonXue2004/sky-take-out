package com.sky.exception;

/**
 * A Stripe API call that did not succeed.
 * <p>
 * Retryable failures (timeouts, network errors, 429 and 5xx responses) may succeed later, and
 * because every call carries an Idempotency-Key, repeating one cannot charge or refund twice.
 * Other 4xx responses mean the request itself is wrong and will fail again.
 */
public class StripeApiException extends RuntimeException {

    /** HTTP status of Stripe's response, or 0 when no response arrived. */
    private final int status;
    private final boolean retryable;

    public StripeApiException(int status, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public StripeApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.retryable = true;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
