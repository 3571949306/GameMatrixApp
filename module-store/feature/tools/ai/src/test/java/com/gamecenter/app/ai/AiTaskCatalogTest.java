package com.gamecenter.app.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AiTaskCatalogTest {

    @Test
    public void chatIsTheSafeDefault() {
        assertEquals(AiTaskCatalog.CHAT, AiTaskCatalog.normalize(null));
        assertEquals(AiTaskCatalog.CHAT, AiTaskCatalog.normalize(""));
        assertEquals(AiTaskCatalog.CHAT, AiTaskCatalog.normalize("not-a-task"));
    }

    @Test
    public void historicOcrIdentifierIsNormalized() {
        assertEquals(AiTaskCatalog.OCR_CLEAN, AiTaskCatalog.normalize("ocr"));
        assertEquals(AiTaskCatalog.OCR_CLEAN, AiTaskCatalog.normalize("ocr_clean"));
    }
}
