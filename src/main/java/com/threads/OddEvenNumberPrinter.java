package com.threads;

public class OddEvenNumberPrinter {
    private static final Object lock = new Object();
    private static boolean isOddTurn = true; // odd thread goes first
    private static final int MAX = 10;

    public static void main(String[] args) {
        Thread oddThread = new Thread(() -> {
            for (int i = 1; i <= MAX; i += 2) {
                synchronized (lock) {
                    while (!isOddTurn) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    System.out.printf("Odd Number: %d\n", i);
                    isOddTurn = false;
                    lock.notify();
                }
            }
        });

        Thread evenThread = new Thread(() -> {
            for (int i = 2; i <= MAX; i += 2) {
                synchronized (lock) {
                    while (isOddTurn) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    System.out.printf("Even Number: %d\n", i);
                    isOddTurn = true;
                    lock.notify();
                }
            }
        });

        oddThread.start();
        evenThread.start();
    }
}
