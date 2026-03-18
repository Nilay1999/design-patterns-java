package com.lld.moviebooking.concurrency;

import com.lld.moviebooking.models.Booking;
import com.lld.moviebooking.service.BookingService;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Run this after implementing BookingService to verify your locking.
 *
 * 10 users race for the same seat simultaneously.
 * Exactly 1 must win, 9 must be rejected.
 */
public class ConcurrencyTestHarness {

    public static void test(BookingService bookingService, String showId) {
        System.out.println("\n=== CONCURRENCY TEST: 10 users racing for seat A1 ===\n");

        int numUsers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(numUsers);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < numUsers; i++) {
            String userId = "user" + i;
            executor.submit(() -> {
                try {
                    startGun.await(); // all threads wait here, then fire at once
                    Booking booking = bookingService.lockSeats(userId, showId, List.of("A1"));
                    System.out.println("  SUCCESS: " + userId + " got booking " + booking.getId());
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    System.out.println("  REJECTED: " + userId + " — " + e.getMessage());
                    failures.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("  ERROR: " + userId + " — " + e.getMessage());
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown(); // fire!
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        System.out.println("\n--- RESULTS ---");
        System.out.println("Successes: " + successes.get());
        System.out.println("Failures:  " + failures.get());

        if (successes.get() == 1) {
            System.out.println("PASS");
        } else if (successes.get() > 1) {
            System.out.println("FAIL — DOUBLE BOOKING");
        } else {
            System.out.println("FAIL — zero bookings, possible deadlock");
        }
    }
}
