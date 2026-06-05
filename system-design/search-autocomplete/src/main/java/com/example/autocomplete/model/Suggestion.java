package com.example.autocomplete.model;

import java.util.Objects;

public final class Suggestion {
    private final String text;
    private final String type;
    private final int popularity;

    public Suggestion(String text, String type, int popularity) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Suggestion text cannot be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Suggestion type cannot be blank");
        }
        if (popularity < 0) {
            throw new IllegalArgumentException("Popularity cannot be negative");
        }

        this.text = text;
        this.type = type;
        this.popularity = popularity;
    }

    public String text() {
        return text;
    }

    public String type() {
        return type;
    }

    public int popularity() {
        return popularity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Suggestion that)) {
            return false;
        }
        return popularity == that.popularity
                && Objects.equals(text, that.text)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, type, popularity);
    }

    @Override
    public String toString() {
        return "Suggestion{" +
                "text='" + text + '\'' +
                ", type='" + type + '\'' +
                ", popularity=" + popularity +
                '}';
    }
}
