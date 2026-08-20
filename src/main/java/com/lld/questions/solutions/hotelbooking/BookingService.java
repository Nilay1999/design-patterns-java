package com.lld.questions.solutions.hotelbooking;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BookingService {
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public Booking book(User user, Room room, Instant startDate, Instant endDate) {

        Object lock = locks.computeIfAbsent(room.getRoomId(), k -> new Object());
        synchronized (lock) {
            if (!room.isAvailable(startDate, endDate)) {
                throw new RuntimeException("Room not available in given time range");
            }
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
            String bookingId = UUID.randomUUID().toString();
            Booking booking = new Booking(bookingId, BookingStatus.HELD, startDate,
                    endDate, expiresAt, user, room,
                    null);
            room.getBookings().add(booking);
            user.getBookings().add(booking);
            return booking;
        }
    }

    public Booking confirmBooking(Booking booking, double amount, PaymentType type) {
        if (booking.getStatus() != BookingStatus.HELD
                || booking.getHoldExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("hold expired or not held");
        }
        Payment payment = new Payment(booking.getBookingId(), amount, type);
        payment.makePayment();
        booking.setPayment(payment);
        booking.setStatus(BookingStatus.BOOKED);
        return booking;
    }

    public void cancelBooking(Booking booking) {
        if (booking.getStatus() == BookingStatus.BOOKED) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.getPayment().refundPayment();
        }
    }
}
