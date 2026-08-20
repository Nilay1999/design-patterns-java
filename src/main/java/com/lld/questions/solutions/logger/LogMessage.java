package com.lld.questions.solutions.logger;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class LogMessage {
    private final LogLevel level;
    private final String content;
    private final String source;
    private final String eventType;
    private final Instant timestamp;
    private final Map<String, String> metadata;

    private LogMessage(Builder b) {
        this.level = b.level;
        this.content = b.content;
        this.source = b.source;
        this.eventType = b.eventType;
        this.timestamp = Instant.now();
        this.metadata = Collections.unmodifiableMap(b.metadata);
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean hasEvent() {
        return eventType != null;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    static class Builder {
        private final LogLevel level;
        private final String content;
        private String source;
        private String eventType;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        Builder(LogLevel level, String content) {
            this.level = level;
            this.content = content;
        }

        Builder source(String s) {
            this.source = s;
            return this;
        }

        Builder eventType(String e) {
            this.eventType = e;
            return this;
        }

        Builder meta(String k, String v) {
            this.metadata.put(k, v);
            return this;
        }

        LogMessage build() {
            return new LogMessage(this);
        }
    }
}