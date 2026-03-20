package com.lld.moviebooking.concurrency;

import com.lld.moviebooking.models.Booking;
import com.lld.moviebooking.service.BookingService;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyTestHarness {

    public static void test(BookingService bookingService, String showId) {
        testSameSeat(bookingService, showId);
        testOverlappingSeats(bookingService, showId);
        testDifferentSeats(bookingService, showId);
    }

    /**
     * Test 1: 10 users race for the exact same seats [A1, A2].
     * Exactly 1 must win, 9 must be rejected.
     */
    private static void testSameSeat(BookingService bookingService, String showId) {
        System.out.println("\n=== TEST 1: 10 users racing for same seats [A1, A2] ===\n");

        int numUsers = 10;
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        runConcurrent(numUsers, userId -> {
            Booking booking = bookingService.lockSeats(userId, showId, List.of("A1", "A2"));
            System.out.println("  SUCCESS: " + userId + " got booking " + booking.getId()
                    + " for seats [A1, A2]");
            successes.incrementAndGet();
        }, (userId, e) -> {
            System.out.println("  REJECTED: " + userId + " — " + e.getMessage());
            failures.incrementAndGet();
        });

        System.out.println("\n--- RESULTS ---");
        System.out.println("Successes: " + successes.get());
        System.out.println("Failures:  " + failures.get());
        printVerdict(successes.get(), 1, "TEST 1");
    }

    /**
     * Test 2: Users request overlapping seat ranges.
     * User 0-4 want [A3, A4], User 5-9 want [A4, A5].
     * Seat A4 is the contention point — at most one group can win A4.
     * So at most 2 total successes (one from each group), but both groups
     * sharing A4 means at most 1 from each group, and the second group
     * to grab A4 blocks the other.
     */
    private static void testOverlappingSeats(BookingService bookingService, String showId) {
        System.out.println("\n=== TEST 2: 10 users with overlapping seats (A4 is contested) ===\n");

        int numUsers = 10;
        AtomicInteger groupASuccess = new AtomicInteger(0);
        AtomicInteger groupBSuccess = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        runConcurrent(numUsers, userId -> {
            int userNum = Integer.parseInt(userId.replace("user", ""));
            List<String> seats;
            if (userNum < 5) {
                seats = List.of("A3", "A4"); // group A
            } else {
                seats = List.of("A4", "A5"); // group B — overlaps on A4
            }

            Booking booking = bookingService.lockSeats(userId, showId, seats);
            System.out.println("  SUCCESS: " + userId + " got booking " + booking.getId()
                    + " for seats " + seats);

            if (userNum < 5) {
                groupASuccess.incrementAndGet();
            } else {
                groupBSuccess.incrementAndGet();
            }
        }, (userId, e) -> {
            System.out.println("  REJECTED: " + userId + " — " + e.getMessage());
            failures.incrementAndGet();
        });

        System.out.println("\n--- RESULTS ---");
        System.out.println("Group A [A3,A4] successes: " + groupASuccess.get());
        System.out.println("Group B [A4,A5] successes: " + groupBSuccess.get());
        System.out.println("Failures: " + failures.get());

        int totalSuccess = groupASuccess.get() + groupBSuccess.get();
        // Both groups share A4 — only one group can lock A4
        // So exactly 1 winner from either group A or group B
        if (totalSuccess == 1) {
            System.out.println("TEST 2: PASS — only one group got the contested seat A4");
        } else if (groupASuccess.get() == 1 && groupBSuccess.get() == 1) {
            System.out.println("TEST 2: FAIL — DOUBLE BOOKING on seat A4");
        } else if (totalSuccess == 0) {
            System.out.println("TEST 2: FAIL — zero bookings, possible deadlock");
        } else {
            System.out.println("TEST 2: FAIL — unexpected " + totalSuccess + " successes");
        }
    }

    /**
     * Test 3: Users book completely different seats on the same show.
     * User 0 wants [B1], User 1 wants [B2], etc. No overlap.
     * All should succeed — tests that the lock doesn't over-reject.
     */
    private static void testDifferentSeats(BookingService bookingService, String showId) {
        System.out.println("\n=== TEST 3: 5 users booking different seats [B1]..[B5] — all should win ===\n");

        int numUsers = 5;
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ConcurrentHashMap<String, String> bookedSeats = new ConcurrentHashMap<>();

        runConcurrent(numUsers, userId -> {
            int userNum = Integer.parseInt(userId.replace("user", ""));
            String seatId = "B" + (userNum + 1);

            Booking booking = bookingService.lockSeats(userId, showId, List.of(seatId));
            System.out.println("  SUCCESS: " + userId + " got booking " + booking.getId()
                    + " for seat [" + seatId + "]");
            bookedSeats.put(seatId, userId);
            successes.incrementAndGet();
        }, (userId, e) -> {
            System.out.println("  REJECTED: " + userId + " — " + e.getMessage());
            failures.incrementAndGet();
        });

        System.out.println("\n--- RESULTS ---");
        System.out.println("Successes: " + successes.get());
        System.out.println("Failures:  " + failures.get());
        System.out.println("Seats booked: " + bookedSeats);
        printVerdict(successes.get(), numUsers, "TEST 3");
    }

    // ---- helper ----

    private static void runConcurrent(int numUsers, UserAction action, UserError errorHandler) {
        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(numUsers);

        for (int i = 0; i < numUsers; i++) {
            String userId = "user" + i;
            executor.submit(() -> {
                try {
                    startGun.await();
                    action.run(userId);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    errorHandler.handle(userId, e);
                } catch (Exception e) {
                    System.out.println("  ERROR: " + userId + " — " + e.getClass().getSimpleName()
                            + ": " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
    }

    private static void printVerdict(int actual, int expected, String testName) {
        if (actual == expected) {
            System.out.println(testName + ": PASS");
        } else if (actual > expected) {
            System.out.println(testName + ": FAIL — DOUBLE BOOKING (expected " + expected
                    + ", got " + actual + ")");
        } else {
            System.out.println(testName + ": FAIL — expected " + expected + " successes, got " + actual);
        }
    }

    @FunctionalInterface
    interface UserAction {
        void run(String userId) throws Exception;
    }

    @FunctionalInterface
    interface UserError {
        void handle(String userId, Exception e);
    }
}
