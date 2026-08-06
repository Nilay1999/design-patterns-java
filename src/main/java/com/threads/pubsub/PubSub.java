package com.threads.pubsub;

import java.util.concurrent.LinkedBlockingQueue;

public class PubSub {
    class Consumer implements Runnable {
        private LinkedBlockingQueue<Integer> queue;

        Consumer(LinkedBlockingQueue<Integer> queue) {
            this.queue = queue;
        }

        public void run() {
            try {
                while (true) {
                    int value = queue.take();
                    System.out.println("Consumed: " + value);
                }
            } catch (Exception e) {
            }
        }
    }

    class Producer implements Runnable {
        private LinkedBlockingQueue<Integer> queue;

        Producer(LinkedBlockingQueue<Integer> queue) {
            this.queue = queue;
        }

        public void run() {
            try {
                while (true) {
                    int value = queue.take();
                    System.out.println("Consumed: " + value);
                }
            } catch (Exception e) {
            }
        }
    }
}
