package com.lld.questions.solutions.logger;

class SqsAppender implements LogAppender {
    private final String queueUrl;

    SqsAppender(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    @Override
    public boolean supports(LogMessage m) {
        return m.hasEvent();
    }

    @Override
    public void append(LogMessage m) {
        System.out.printf("  -> SQS send queue=%s event=%s payload=%s%n",
                queueUrl, m.getEventType(), m.getContent());
    }
}