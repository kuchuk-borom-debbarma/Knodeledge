package dev.kuku.knodeledge.services.rag;

import java.util.List;

public interface TextEmbeddingService {
    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
