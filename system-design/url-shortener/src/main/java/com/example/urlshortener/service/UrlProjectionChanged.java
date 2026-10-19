package com.example.urlshortener.service;

import com.example.urlshortener.storage.UrlLookupRecord;

/** Event emitted inside the database transaction and handled only after commit. */
public record UrlProjectionChanged(String eventId, UrlLookupRecord record) {
}
