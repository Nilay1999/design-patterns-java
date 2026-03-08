package com.threads;

public class PingPong {
    private static boolean isPingTurn = true;
    private static final Object lock = new Object();
    private static final int MAX = 10;
    private static int count = 0;

    public static void main(String[] args) {
        Thread ping = new Thread(() -> {
            while (count < MAX) {
                synchronized (lock) {
                    if (count < MAX && !isPingTurn) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    System.out.println("Ping");
                    isPingTurn = false;
                    count++;
                    lock.notify();
                }
            }
        });

        Thread pong = new Thread(() -> {
            while (count < MAX) {
                synchronized (lock) {
                    if (count < MAX && isPingTurn) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    System.out.println("Pong");
                    isPingTurn = true;
                    count++;
                    lock.notify();
                }
            }
        });

        ping.start();
        pong.start();
    }
}
