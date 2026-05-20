package com.gamecenter.app.ai.data;

public enum TaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskStatus fromValue(String value) {
        if (value == null) return PENDING;
        for (TaskStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return PENDING;
    }
}
