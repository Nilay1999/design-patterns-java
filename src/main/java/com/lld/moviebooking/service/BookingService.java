package com.lld.moviebooking.service;

import com.lld.moviebooking.models.Booking;
import com.lld.moviebooking.models.Seat;
import com.lld.moviebooking.models.SeatStatus;
import com.lld.moviebooking.models.Show;
import com.lld.moviebooking.repository.BookingRepository;
import com.lld.moviebooking.repository.ShowRepository;

import java.util.List;
import java.util.stream.Collectors;

public class BookingService {

    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;

    public BookingService(ShowRepository showRepository, BookingRepository bookingRepository) {
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
    }

    public Booking lockSeats(String userId, String showId, List<String> seatIds) {
        throw new UnsupportedOperationException("TODO");
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
