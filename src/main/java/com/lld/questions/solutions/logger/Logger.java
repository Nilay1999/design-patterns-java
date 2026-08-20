package com.lld.questions.solutions.logger;

import java.util.Map;

class Logger {
    private final String name;
    private final LogManager manager;

    Logger(String name, LogManager manager) {
        this.name = name;
        this.manager = manager;
    }

    /* convenience methods for ordinary logs */
    public void debug(String msg) {
        log(new LogMessage.Builder(LogLevel.DEBUG, msg).source(name).build());
    }

    public void info(String msg) {
        log(new LogMessage.Builder(LogLevel.INFO, msg).source(name).build());
    }

    public void warn(String msg) {
        log(new LogMessage.Builder(LogLevel.WARN, msg).source(name).build());
    }

    public void error(String msg) {
        log(new LogMessage.Builder(LogLevel.ERROR, msg).source(name).build());
    }

    /** Emit an event-tagged log -> printed AND routed to transports. */
    public void event(String eventType, LogLevel level, String msg, Map<String, String> meta) {
        LogMessage.Builder b = new LogMessage.Builder(level, msg).source(name).eventType(eventType);
        if (meta != null)
            meta.forEach(b::meta);
        log(b.build());
    }

    private void log(LogMessage m) {
        // read config from the manager so threshold/appender changes stay live
        if (m.getLevel().ordinal() < manager.getThreshold().ordinal())
            return; // level filter
        for (LogAppender appender : manager.getAppenders()) {
            if (appender.supports(m)) {
                appender.append(m);
            }
        }
    }
}