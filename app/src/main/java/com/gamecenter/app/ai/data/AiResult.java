package com.gamecenter.app.ai.data;

/**
 * AI 统一返回结果。
 */
public final class AiResult {

    public final boolean success;
    public final String message;
    public final String content;
    public final String source;       // "local" / "cloud"
    public final String errorCode;    // 错误码，空表示无错误

    private AiResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.content = builder.content;
        this.source = builder.source;
        this.errorCode = builder.errorCode;
    }

    public static Builder success(String content) {
        return new Builder(true, content);
    }

    public static Builder fail(String message) {
        return new Builder(false, message);
    }

    public static class Builder {
        private boolean success;
        private String message;
        private String content;
        private String source = "cloud";
        private String errorCode = "";

        private Builder(boolean success, String text) {
            this.success = success;
            if (success) {
                this.content = text;
            } else {
                this.message = text;
            }
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder errorCode(String code) {
            this.errorCode = code;
            return this;
        }

        public AiResult build() {
            return new AiResult(this);
        }
    }
}