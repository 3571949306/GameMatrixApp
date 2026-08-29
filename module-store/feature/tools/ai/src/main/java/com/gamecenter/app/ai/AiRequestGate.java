package com.gamecenter.app.ai;

import java.util.UUID;

/**
 * Guards the single active request of the assistant conversation.
 *
 * <p>A UI lifecycle callback is not a cancellation primitive.  This gate
 * gives each request an immutable token and invalidates old tokens whenever
 * the conversation is cleared or its view is recreated.  Late results may
 * still arrive from a shared executor, but they can no longer mutate the
 * current conversation.</p>
 */
public final class AiRequestGate {

    private long conversationGeneration;
    private RequestToken activeToken;

    /**
     * Acquires the only active request slot.
     *
     * @return a token for the new request, or {@code null} if one is active
     */
    public synchronized RequestToken tryAcquire() {
        if (activeToken != null) {
            return null;
        }
        activeToken = new RequestToken(UUID.randomUUID().toString(), conversationGeneration);
        return activeToken;
    }

    /**
     * Invalidates the current request and advances the conversation identity.
     * This is used for clear/new conversation and view teardown.
     */
    public synchronized void invalidateConversation() {
        conversationGeneration++;
        activeToken = null;
    }

    /**
     * Returns whether the token still belongs to the current conversation.
     */
    public synchronized boolean isActive(RequestToken token) {
        return token != null && token == activeToken && token.generation == conversationGeneration;
    }

    /** Returns whether a request currently owns the single active slot. */
    public synchronized boolean isBusy() {
        return activeToken != null;
    }

    /**
     * Releases the slot only for the currently active token.
     * A stale callback cannot release a newer request.
     */
    public synchronized boolean finish(RequestToken token) {
        if (!isActive(token)) {
            return false;
        }
        activeToken = null;
        return true;
    }

    /** Immutable request identity passed through asynchronous callbacks. */
    public static final class RequestToken {
        private final String requestId;
        private final long generation;

        private RequestToken(String requestId, long generation) {
            this.requestId = requestId;
            this.generation = generation;
        }

        public String getRequestId() {
            return requestId;
        }

        public long getGeneration() {
            return generation;
        }
    }
}
