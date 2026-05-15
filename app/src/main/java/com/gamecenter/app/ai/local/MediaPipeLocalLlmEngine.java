package com.gamecenter.app.ai.local;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;

public final class MediaPipeLocalLlmEngine implements AutoCloseable {
    private LlmInference llmInference;
    private String loadedModelPath = "";

    public synchronized void load(Context context, File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            throw new IllegalStateException("Local model file is missing");
        }
        String modelPath = modelFile.getAbsolutePath();
        if (llmInference != null && modelPath.equals(loadedModelPath)) {
            return;
        }
        close();
        LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(384)
                .setMaxTopK(20)
                .build();
        llmInference = LlmInference.createFromOptions(context.getApplicationContext(), options);
        loadedModelPath = modelPath;
    }

    public synchronized String generate(String prompt) {
        if (llmInference == null) {
            throw new IllegalStateException("Local LLM is not loaded");
        }
        return llmInference.generateResponse(prompt);
    }

    @Override
    public synchronized void close() {
        if (llmInference != null) {
            llmInference.close();
            llmInference = null;
        }
        loadedModelPath = "";
    }
}
