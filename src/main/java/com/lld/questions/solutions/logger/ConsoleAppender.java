package com.lld.questions.solutions.logger;

class ConsoleAppender implements LogAppender {
    @Override
    public void append(LogMessage m) {
        System.out.printf("%s [%s] %s - %s%s%n",
                m.getTimestamp(), m.getLevel(), m.getSource(), m.getContent(),
                m.getMetadata().isEmpty() ? "" : " " + m.getMetadata());
    }
}