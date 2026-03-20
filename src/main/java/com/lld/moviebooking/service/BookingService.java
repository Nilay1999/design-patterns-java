package com.lld.moviebooking.service;

import com.lld.moviebooking.models.Booking;
import com.lld.moviebooking.models.Seat;
import com.lld.moviebooking.models.SeatStatus;
import com.lld.moviebooking.models.Show;
import com.lld.moviebooking.models.User;
import com.lld.moviebooking.repository.BookingRepository;
import com.lld.moviebooking.repository.ShowRepository;
import com.lld.moviebooking.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class BookingService {
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ConcurrentHashMap<String, ReentrantLock> showLocks = new ConcurrentHashMap<>();

    private ReentrantLock getShowLock(String showId) {
        return showLocks.computeIfAbsent(showId, k -> new ReentrantLock());
    }

    public BookingService(
            ShowRepository showRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository) {
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    public Booking lockSeats(String userId, String showId, List<String> seatIds) {
        // 1. Fetch data OUTSIDE the lock — no need to hold lock during I/O
        User user = userRepository.get(userId);
        Show show = showRepository.get(showId);

        // 2. Acquire per-show lock with timeout
        ReentrantLock showLock = getShowLock(showId);
        boolean acquired;
        try {
            acquired = showLock.tryLock(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for lock on show " + showId);
        }

        if (!acquired) {
            throw new IllegalStateException("Show " + showId + " is busy, please try again");
        }

        // 3. Lock acquired — validate and lock seats inside try-finally
        List<Seat> lockedSeats = new ArrayList<>();
        try {
            List<Seat> seats = show.seats.stream()
                    .filter(s -> seatIds.contains(s.getId()))
                    .toList();

            if (seats.size() != seatIds.size()) {
                throw new IllegalArgumentException("Some requested seats not found in show");
            }

            for (Seat s : seats) {
                if (s.getStatus() != SeatStatus.AVAILABLE) {
                    // rollback seats we already locked in this request
                    for (Seat locked : lockedSeats) {
                        locked.setStatus(SeatStatus.AVAILABLE);
                        locked.setLockedAt(null);
                    }
                    throw new IllegalStateException("Seat " + s.getId() + " is not available");
                } else {
                    s.setStatus(SeatStatus.LOCKED);
                    s.setLockedAt(Instant.now());
                    lockedSeats.add(s);
                }
            }
        } finally {
            showLock.unlock(); // always release, even on exception
        }

        // 4. Create and persist booking OUTSIDE the lock
        Booking booking = new Booking(UUID.randomUUID().toString(),
                show.getMovie(), user, lockedSeats);
        bookingRepository.add(booking);
        return booking;
    }

    public Booking confirmBooking(String bookingId) {
        throw new UnsupportedOperationException("TODO");
    }

    public Booking cancelBooking(String bookingId) {
        throw new UnsupportedOperationException("TODO");
    }

    public List<Seat> getAvailableSeats(String showId) {
        Show show = showRepository.get(showId);
        return show.getSeats().stream().filter((a) -> a.status == SeatStatus.AVAILABLE)
                .collect(Collectors.toList());
    }
}
