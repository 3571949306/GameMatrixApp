package com.gamecenter.app.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Canonical task identifiers exposed by the assistant UI.
 *
 * <p>Labels are localized by the host UI, but identifiers must not be
 * duplicated in several arrays.  Unknown or missing UI values intentionally
 * resolve to {@link #CHAT}, because chat is the safe default for a
 * chat-first surface.</p>
 */
public final class AiTaskCatalog {

    public static final String CHAT = "chat";
    public static final String OCR_CLEAN = "ocr_clean";
    public static final String SUMMARY = "summary";
    public static final String TRANSLATE = "translate";
    public static final String REWRITE = "rewrite";
    public static final String QA = "qa";
    public static final String KEYWORDS = "keywords";
    public static final String CLASSIFY = "classify";
    public static final String MINI_GAME = "mini-game";

    private static final List<String> TYPES = Collections.unmodifiableList(Arrays.asList(
            CHAT, OCR_CLEAN, SUMMARY, TRANSLATE, REWRITE, QA, KEYWORDS, CLASSIFY, MINI_GAME
    ));

    private AiTaskCatalog() {
    }

    public static List<String> getTypes() {
        return TYPES;
    }

    /**
     * Normalizes values coming from localized labels, old saved state, or
     * callers that still use the historic {@code ocr} identifier.
     */
    public static String normalize(String taskType) {
        if (taskType == null || taskType.trim().isEmpty()) {
            return CHAT;
        }
        String normalized = taskType.trim();
        if ("ocr".equals(normalized)) {
            return OCR_CLEAN;
        }
        return TYPES.contains(normalized) ? normalized : CHAT;
    }
}
