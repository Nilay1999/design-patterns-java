package com.threads;

import java.util.concurrent.atomic.AtomicInteger;

class Counter {
    private AtomicInteger count = new AtomicInteger();

    public void increment() {
        count.incrementAndGet();
    }

    public int getCount() {
        return count.get();
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        int numberOfThreads = 100;

        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    counter.increment();
                }
            });

            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("Final count = " + counter.getCount());
    }
}
