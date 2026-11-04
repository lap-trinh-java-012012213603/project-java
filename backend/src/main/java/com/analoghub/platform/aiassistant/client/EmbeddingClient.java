package com.analoghub.platform.aiassistant.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface EmbeddingClient {

    int REQUIRED_DIMENSIONS = 768;

    ModelProvider provider();

    String modelName();

    default int configuredDimensions() {
        return REQUIRED_DIMENSIONS;
    }

    float[] embed(String text, EmbeddingTaskType taskType);

    List<float[]> embedBatch(List<String> texts, EmbeddingTaskType taskType);

    static float[] normalizeL2(float[] rawVector, int targetDimensions) {
        float[] aligned = new float[targetDimensions];
        if (rawVector != null) {
            int copyLen = Math.min(rawVector.length, targetDimensions);
            System.arraycopy(rawVector, 0, aligned, 0, copyLen);
        }

        double sumSquares = 0.0;
        for (float v : aligned) {
            sumSquares += (double) v * v;
        }

        double norm = Math.sqrt(sumSquares);
        if (norm < 1e-12) {
            aligned[0] = 1.0f;
            return aligned;
        }

        for (int i = 0; i < aligned.length; i++) {
            aligned[i] = (float) (aligned[i] / norm);
        }
        return aligned;
    }

    static float[] deterministicEmbedding(String text, EmbeddingTaskType taskType, int dimensions) {
        String seedInput = (taskType != null ? taskType.name() : "DEFAULT") + ":" + (text != null ? text : "");
        float[] raw = new float[dimensions];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[0];
            int counter = 0;
            for (int i = 0; i < dimensions; i++) {
                if (i % 8 == 0) {
                    digest.reset();
                    digest.update(seedInput.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) (counter >>> 8));
                    digest.update((byte) counter);
                    block = digest.digest();
                    counter++;
                }
                int offset = (i % 8) * 4;
                int bits = ((block[offset] & 0xFF) << 24)
                        | ((block[offset + 1] & 0xFF) << 16)
                        | ((block[offset + 2] & 0xFF) << 8)
                        | (block[offset + 3] & 0xFF);
                raw[i] = (bits / (float) Integer.MAX_VALUE);
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
        return normalizeL2(raw, dimensions);
    }
}

