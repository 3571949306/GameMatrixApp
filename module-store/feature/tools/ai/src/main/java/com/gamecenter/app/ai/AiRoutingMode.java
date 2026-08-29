package com.gamecenter.app.ai;

/**
 * A snapshot of the routing decision for one AI request.
 *
 * <p>The mode is captured when a request is submitted.  A request must not
 * silently change from local processing to a network call because a
 * preference changes while it is running.</p>
 */
public enum AiRoutingMode {
    /** Never access the network; return a typed result when local processing is unavailable. */
    LOCAL_ONLY,
    /** Skip local processing and use the cloud path after the caller has obtained consent. */
    CLOUD_ONLY
}
