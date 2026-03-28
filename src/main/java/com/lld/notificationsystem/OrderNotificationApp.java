package com.lld.notificationsystem;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lld.notificationsystem.event.OrderEvent;
import com.lld.notificationsystem.subscribers.EmailNotificationSubscriber;
import com.lld.notificationsystem.subscribers.InventoryUpdateSubscriber;

public class OrderNotificationApp {

    public static void main(String[] args) throws InterruptedException {

        // 1. Create the event bus with a capacity of 5
        OrderEventBus eventBus = new OrderEventBus(5);

        // 2. Register subscribers — decoupled, bus doesn't know their internals
        eventBus.subscribe(new EmailNotificationSubscriber());
        eventBus.subscribe(new InventoryUpdateSubscriber());

        // 3. Start the internal dispatcher thread
        eventBus.startDispatching();

        // 4. Simulate two concurrent publishers (e.g., two API server instances)
        ExecutorService publisherPool = Executors.newFixedThreadPool(2);

        publisherPool.submit(() -> {
            try {
                eventBus.publish(new OrderEvent("ORD-101", OrderEvent.Type.ORDER_PLACED, 1299.00));
                eventBus.publish(new OrderEvent("ORD-102", OrderEvent.Type.ORDER_PLACED, 899.00));
                eventBus.publish(new OrderEvent("ORD-101", OrderEvent.Type.ORDER_SHIPPED, 1299.00));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        publisherPool.submit(() -> {
            try {
                eventBus.publish(new OrderEvent("ORD-103", OrderEvent.Type.ORDER_PLACED, 450.00));
                eventBus.publish(new OrderEvent("ORD-103", OrderEvent.Type.ORDER_CANCELLED, 450.00));
                eventBus.publish(new OrderEvent("ORD-104", OrderEvent.Type.ORDER_PLACED, 3200.00));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 5. Let publishers finish, then shut down
        publisherPool.shutdown();
        publisherPool.awaitTermination(5, TimeUnit.SECONDS);

        Thread.sleep(500); // give dispatcher time to drain the queue
        eventBus.shutdown();

        System.out.println("\n[App] All events processed. Shutting down.");
    }
}
