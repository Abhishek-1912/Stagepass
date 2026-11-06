package com.stagepass.payment.service;

/** A failed Razorpay call. {@code retryable} is true for network errors and 5xx answers. */
public class RazorpayException extends RuntimeException {

    private final boolean retryable;
    private final int status;

    public RazorpayException(String message, int status, boolean retryable) {
        super(message);
        this.status = status;
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    public int status() {
        return status;
    }
}
