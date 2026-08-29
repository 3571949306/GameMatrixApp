package com.gamecenter.app.ai.model;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AiModelDownloadValidatorTest {

    private static final String PRIMARY = "https://hk-update.example.test:2083/";
    private static final String FALLBACK = "https://cdn.example.test/";
    private static final String SHA256 = "a".repeat(64);

    @Test
    public void acceptsSafeBasenameAndKeepsItBelowRoot() throws Exception {
        File root = Files.createTempDirectory("ai-model-root").toFile();

        File resolved = AiModelDownloadValidator.resolveContainedFile(root, "qwen-0.5b.task");

        assertEquals(root.getCanonicalFile(), resolved.getParentFile().getCanonicalFile());
    }

    @Test
    public void rejectsNullRoot() throws Exception {
        try {
            AiModelDownloadValidator.resolveContainedFile(null, "model.task");
            fail("Expected null root rejection");
        } catch (java.io.IOException expected) {
            // expected
        }
    }

    @Test
    public void rejectsTraversalAndAbsolutePathRepresentations() {
        String[] unsafe = {
                "../outside.task",
                "..\\outside.task",
                "/tmp/outside.task",
                "C:\\outside.task",
                "C:/outside.task",
                ".",
                "..",
                "model/child.task",
                "model\\child.task",
                "model\u0000.task",
                ".hidden.task",
                ""
        };
        for (String fileName : unsafe) {
            try {
                AiModelDownloadValidator.validateFileName(fileName);
                fail("Expected rejection for: " + fileName);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void requiresStrongChecksumAndBoundedPositiveSize() {
        AiModelDownloadValidator.validateModelMetadata("model.task", SHA256, 1);

        assertRejectedMetadata("model.task", "", 1);
        assertRejectedMetadata("model.task", "not-a-sha", 1);
        assertRejectedMetadata("model.task", SHA256, 0);
        assertRejectedMetadata("model.task", SHA256, AiModelDownloadValidator.MAX_MODEL_SIZE_BYTES + 1);
    }

    @Test
    public void allowsOnlyConfiguredHttpsOrigins() {
        AiModelDownloadValidator.validateDownloadUrl(
                "https://hk-update.example.test:2083/ai-models/model.task", PRIMARY, FALLBACK);
        AiModelDownloadValidator.validateDownloadUrl(
                "https://cdn.example.test/model.task?token=opaque", PRIMARY, FALLBACK);

        assertRejectedUrl("http://hk-update.example.test:2083/model.task");
        assertRejectedUrl("https://evil.example.test/model.task");
        assertRejectedUrl("https://user:pass@hk-update.example.test:2083/model.task");
        assertRejectedUrl("https://hk-update.example.test:2083/model.task#fragment");
        assertRejectedUrl("https://hk-update.example.test/model.task");
        assertRejectedUrl("https://hk-update.example.test:0/model.task");
    }

    private static void assertRejectedMetadata(String fileName, String sha256, long sizeBytes) {
        try {
            AiModelDownloadValidator.validateModelMetadata(fileName, sha256, sizeBytes);
            fail("Expected metadata rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertRejectedUrl(String url) {
        try {
            AiModelDownloadValidator.validateDownloadUrl(url, PRIMARY, FALLBACK);
            fail("Expected URL rejection for: " + url);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
