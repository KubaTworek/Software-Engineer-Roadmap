package com.example.newsfeed.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {
    private static final String MODEL_VERSION = "hash-embedding-v1";
    private final EmbeddingRepository repository;
    private final int dimensions;

    public EmbeddingService(EmbeddingRepository repository, @Value("${newsfeed.recommendation.embedding-dimensions:16}") int dimensions) {
        this.repository = repository;
        this.dimensions = dimensions;
    }

    public double[] embedText(String text) {
        double[] vector = new double[dimensions];
        if (text == null || text.isBlank()) return vector;
        for (String token : text.toLowerCase().split("\\W+")) {
            int index = Math.floorMod(token.hashCode(), dimensions);
            vector[index] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    @Transactional
    public void savePostEmbedding(UUID postId, String text) {
        repository.save(new Embedding("post", postId, MODEL_VERSION, encode(embedText(text)), Instant.now()));
    }

    @Transactional
    public void saveUserEmbedding(UUID userId, String profileText) {
        repository.save(new Embedding("user", userId, MODEL_VERSION, encode(embedText(profileText)), Instant.now()));
    }

    @Transactional(readOnly = true)
    public Optional<double[]> getEmbedding(String type, UUID id) {
        return repository.findByEntityTypeAndEntityIdAndModelVersion(type, id, MODEL_VERSION).map(e -> decode(e.getVector()));
    }

    @Transactional(readOnly = true)
    public List<Embedding> recentPostEmbeddings() {
        return repository.findTop200ByEntityTypeAndModelVersion("post", MODEL_VERSION);
    }

    public double cosine(double[] a, double[] b) {
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i];
        }
        if (aa == 0 || bb == 0) return 0;
        return dot / (Math.sqrt(aa) * Math.sqrt(bb));
    }

    private void normalize(double[] v) {
        double sum = 0;
        for (double d : v) sum += d * d;
        if (sum == 0) return;
        double norm = Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) v[i] = v[i] / norm;
    }

    private String encode(double[] vector) {
        return Arrays.stream(vector).mapToObj(Double::toString).collect(Collectors.joining(","));
    }

    private double[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return new double[dimensions];
        String[] parts = encoded.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i]);
        return result;
    }
}
