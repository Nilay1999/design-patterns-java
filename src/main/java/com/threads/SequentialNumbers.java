package com.threads;

public class SequentialNumbers {

    private static final Object lock = new Object();
    private static final int MAX = 20;
    private static final int NUMBER_OF_THREADS = 3;
    private static int currentNumber = 1;

    public static void main(String[] args) {

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            int threadIndex = i;

            new Thread(() -> {
                while (true) {
                    synchronized (lock) {
                        while (currentNumber <= MAX &&
                                (currentNumber - 1) % NUMBER_OF_THREADS != threadIndex) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        if (currentNumber > MAX) {
                            lock.notifyAll();
                            return;
                        }
                        System.out.println(Thread.currentThread().getName() + ": " + currentNumber);
                        currentNumber++;
                        lock.notifyAll();
                    }
                }
            }, "T" + (i + 1)).start();
        }
    }
}