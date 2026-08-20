package com.lld.questions.solutions.logger;

public interface LogAppender {
    /** Write/publish the message to this destination. */
    void append(LogMessage message);

    /** Whether this appender should handle the given message. */
    default boolean supports(LogMessage message) {
        return true;
    }
}