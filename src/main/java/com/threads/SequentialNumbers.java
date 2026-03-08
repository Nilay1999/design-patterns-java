package com.threads;

public class SequentialNumbers {
    private static final Object lock = new Object();
    private static final int MAX = 20;
    private static final int NUMBER_OF_THREADS = 3;
    private static int currentNumber = 1;
    private static int turn = 0;

    public static void main(String[] args) {
        Thread[] threads = new Thread[NUMBER_OF_THREADS];

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            int threadIndex = i;
            threads[i] = new Thread(() -> {
                while (true) {
                    synchronized (lock) {
                        while (currentNumber <= MAX && turn != threadIndex) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }

                        if (currentNumber > MAX) {
                            lock.notifyAll();
                            return;
                        }

                        System.out.printf("%s: %d%n", Thread.currentThread().getName(), currentNumber);
                        currentNumber++;
                        turn = (turn + 1) % NUMBER_OF_THREADS;
                        lock.notifyAll();
                    }
                }
            }, "T" + (i + 1));
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
