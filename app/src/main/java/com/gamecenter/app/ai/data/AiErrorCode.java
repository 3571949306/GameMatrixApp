package com.gamecenter.app.ai.data;

public final class AiErrorCode {
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String NO_API_KEY = "NO_API_KEY";
    public static final String LOCAL_LLM_LOW_MEMORY = "LOCAL_LLM_LOW_MEMORY";
    public static final String LOCAL_LLM_DEGENERATED_OUTPUT = "LOCAL_LLM_DEGENERATED_OUTPUT";
    public static final String LOCAL_LLM_ERROR = "LOCAL_LLM_ERROR";
    public static final String HTTP_ERROR = "HTTP_ERROR";

    private AiErrorCode() {}
}
