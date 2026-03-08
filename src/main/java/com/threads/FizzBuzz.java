package com.threads;

public class FizzBuzz {
    private static final int MAX = 20;
    private static int counter = 1;
    private static final Object lock = new Object();

    public static void main(String[] args) {

        Thread fizz = new Thread(() -> {
            while (true) {
                synchronized (lock) {

                    while (counter <= MAX && !(counter % 3 == 0 && counter % 5 != 0)) {
                        waitSafely();
                    }

                    if (counter > MAX)
                        break;

                    System.out.println("Fizz");
                    counter++;
                    lock.notifyAll();
                }
            }
        });

        Thread buzz = new Thread(() -> {
            while (true) {
                synchronized (lock) {

                    while (counter <= MAX && !(counter % 5 == 0 && counter % 3 != 0)) {
                        waitSafely();
                    }

                    if (counter > MAX)
                        break;

                    System.out.println("Buzz");
                    counter++;
                    lock.notifyAll();
                }
            }
        });

        Thread fizzbuzz = new Thread(() -> {
            while (true) {
                synchronized (lock) {

                    while (counter <= MAX && !(counter % 3 == 0 && counter % 5 == 0)) {
                        waitSafely();
                    }

                    if (counter > MAX)
                        break;

                    System.out.println("FizzBuzz");
                    counter++;
                    lock.notifyAll();
                }
            }
        });

        Thread number = new Thread(() -> {
            while (true) {
                synchronized (lock) {

                    while (counter <= MAX && (counter % 3 == 0 || counter % 5 == 0)) {
                        waitSafely();
                    }

                    if (counter > MAX)
                        break;

                    System.out.println(counter);
                    counter++;
                    lock.notifyAll();
                }
            }
        });

        fizz.start();
        buzz.start();
        fizzbuzz.start();
        number.start();
    }

    private static void waitSafely() {
        try {
            lock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}