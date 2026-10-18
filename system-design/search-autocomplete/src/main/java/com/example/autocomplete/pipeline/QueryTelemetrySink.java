package com.example.autocomplete.pipeline;

/** Production boundary dla Kafki/stream processingu. */
public interface QueryTelemetrySink {
    void record(QueryEvent event);
}
