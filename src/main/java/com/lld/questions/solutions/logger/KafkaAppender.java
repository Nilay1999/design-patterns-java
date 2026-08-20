package com.lld.questions.solutions.logger;

class KafkaAppender implements LogAppender {
    private final String topic;

    KafkaAppender(String topic) {
        this.topic = topic;
    }

    @Override
    public boolean supports(LogMessage m) {
        return m.hasEvent();
    }

    @Override
    public void append(LogMessage m) {
        System.out.printf("  -> KAFKA publish topic=%s event=%s payload=%s%n",
                topic, m.getEventType(), m.getContent());
    }
}
