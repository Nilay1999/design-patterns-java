package com.lld.moviebooking.api;

import com.lld.moviebooking.repository.BookingRepository;
import com.lld.moviebooking.repository.MovieRepository;
import com.lld.moviebooking.repository.ShowRepository;
import com.lld.moviebooking.service.BookingService;

import io.javalin.Javalin;

import java.util.Map;

public class BookingController {

    private final BookingService bookingService;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;

    public BookingController(BookingService bookingService, MovieRepository movieRepository,
                             ShowRepository showRepository, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.bookingRepository = bookingRepository;
    }

    public void registerRoutes(Javalin app) {

        // --- Read-only routes (no locking needed) ---

        app.get("/api/movies", ctx -> {
            ctx.json(movieRepository.getAll());
        });

        app.get("/api/movies/{movieId}/shows", ctx -> {
            String movieId = ctx.pathParam("movieId");
            ctx.json(showRepository.getByMovie(movieId));
        });

        app.get("/api/shows/{showId}/seats", ctx -> {
            String showId = ctx.pathParam("showId");
            try {
                ctx.json(bookingService.getAvailableSeats(showId));
            } catch (IllegalArgumentException e) {
                ctx.status(404).json(Map.of("error", e.getMessage()));
            }
        });

        app.get("/api/users/{userId}/bookings", ctx -> {
            String userId = ctx.pathParam("userId");
            ctx.json(bookingRepository.getByUser(userId));
        });

        // --- Booking routes (YOUR locking logic gets called here) ---

        app.post("/api/bookings/lock", ctx -> {
            LockRequest req = ctx.bodyAsClass(LockRequest.class);
            try {
                var booking = bookingService.lockSeats(req.userId, req.showId, req.seatIds);
                ctx.json(booking);
            } catch (IllegalStateException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        app.post("/api/bookings/{bookingId}/confirm", ctx -> {
            String bookingId = ctx.pathParam("bookingId");
            try {
                var booking = bookingService.confirmBooking(bookingId);
                ctx.json(booking);
            } catch (IllegalStateException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            }
        });

        app.post("/api/bookings/{bookingId}/cancel", ctx -> {
            String bookingId = ctx.pathParam("bookingId");
            try {
                var booking = bookingService.cancelBooking(bookingId);
                ctx.json(booking);
            } catch (IllegalStateException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            }
        });
    }
}
