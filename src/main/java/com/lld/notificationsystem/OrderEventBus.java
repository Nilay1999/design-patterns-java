package com.lld.notificationsystem;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.lld.notificationsystem.event.OrderEvent;
import com.lld.notificationsystem.interfaces.OrderEventSubscriber;

public class OrderEventBus {

    private final int capacity;
    private final Queue<OrderEvent> eventQueue;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition spaceAvailable = lock.newCondition(); // publishers wait here
    private final Condition eventAvailable = lock.newCondition(); // dispatcher waits here

    private final List<OrderEventSubscriber> subscribers = new ArrayList<>();
    private volatile boolean isRunning = true;

    public OrderEventBus(int capacity) {
        this.capacity = capacity;
        this.eventQueue = new LinkedList<>();
    }

    // ── Publisher side ──────────────────────────────────────────────────────

    public void publish(OrderEvent event) throws InterruptedException {
        lock.lock();
        try {
            // Block publisher if bus is at capacity (backpressure)
            while (eventQueue.size() == capacity) {
                System.out.println("[Bus] Full — publisher waiting: " + event.getOrderId());
                spaceAvailable.await();
            }
            eventQueue.add(event);
            System.out.println("[Bus] Accepted: " + event);
            eventAvailable.signal(); // wake the dispatcher
        } finally {
            lock.unlock();
        }
    }

    // ── Subscriber registration ─────────────────────────────────────────────

    public void subscribe(OrderEventSubscriber subscriber) {
        lock.lock();
        try {
            subscribers.add(subscriber);
        } finally {
            lock.unlock();
        }
    }

    // ── Dispatcher (internal consumer thread) ───────────────────────────────

    public void startDispatching() {
        Thread dispatcherThread = new Thread(() -> {
            while (isRunning || !eventQueue.isEmpty()) {
                OrderEvent event = dequeue();
                if (event != null) {
                    dispatchToSubscribers(event);
                }
            }
            System.out.println("[Bus] Dispatcher stopped.");
        }, "order-event-dispatcher");

        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    private OrderEvent dequeue() {
        lock.lock();
        try {
            // Block dispatcher while queue is empty
            while (eventQueue.isEmpty() && isRunning) {
                eventAvailable.await();
            }
            if (eventQueue.isEmpty())
                return null;

            OrderEvent event = eventQueue.poll();
            spaceAvailable.signal(); // wake a blocked publisher
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void dispatchToSubscribers(OrderEvent event) {
        List<OrderEventSubscriber> snapshot;
        lock.lock();
        try {
            snapshot = new ArrayList<>(subscribers); // snapshot to avoid holding lock during callbacks
        } finally {
            lock.unlock();
        }
        for (OrderEventSubscriber subscriber : snapshot) {
            try {
                subscriber.onOrderEvent(event);
            } catch (Exception e) {
                System.err.println("[Bus] Subscriber threw exception: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            isRunning = false;
            eventAvailable.signalAll(); // wake dispatcher so it can exit
        } finally {
            lock.unlock();
        }
    }
}