package com.lld.questions.solutions.logger;

import java.util.ArrayList;
import java.util.List;

class LogManager {
    private static final LogManager INSTANCE = new LogManager();
    private final List<LogAppender> appenders = new ArrayList<>();
    private LogLevel threshold = LogLevel.DEBUG;

    private LogManager() {
    }

    public static LogManager getInstance() {
        return INSTANCE;
    }

    public LogManager addAppender(LogAppender appender) {
        appenders.add(appender);
        return this;
    }

    public LogManager setThreshold(LogLevel level) {
        this.threshold = level;
        return this;
    }

    List<LogAppender> getAppenders() {
        return appenders;
    }

    LogLevel getThreshold() {
        return threshold;
    }

    public Logger getLogger(String name) {
        // loggers read config back from the manager, so threshold/appender
        // changes after creation stay live
        return new Logger(name, this);
    }
}