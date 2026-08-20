package com.lld.questions.solutions.logger;

import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        LogManager.getInstance()
                .addAppender(new ConsoleAppender())
                .addAppender(new KafkaAppender("orders"))
                .addAppender(new SqsAppender("https://sqs/orders"))
                .setThreshold(LogLevel.DEBUG);

        Logger log = LogManager.getInstance().getLogger("OrderService");

        log.info("service started");
        log.warn("cache miss for key session:abc");

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("orderId", "ord-9981");
        log.event("ORDER_CREATED", LogLevel.INFO, "order created", meta);
        log.event("PAYMENT_SETTLED", LogLevel.INFO, "payment settled", meta);
    }
}