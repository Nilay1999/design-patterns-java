package com.lld.moviebooking;

import com.lld.moviebooking.api.BookingController;
import com.lld.moviebooking.concurrency.ConcurrencyTestHarness;
import com.lld.moviebooking.models.Movie;
import com.lld.moviebooking.models.Show;
import com.lld.moviebooking.repository.BookingRepository;
import com.lld.moviebooking.repository.MovieRepository;
import com.lld.moviebooking.repository.ShowRepository;
import com.lld.moviebooking.repository.UserRepository;
import com.lld.moviebooking.service.BookingService;

import io.javalin.Javalin;

public class App {

    public static void main(String[] args) {
        // Repositories
        MovieRepository movieRepo = new MovieRepository();
        ShowRepository showRepo = new ShowRepository();
        BookingRepository bookingRepo = new BookingRepository();
        UserRepository userRepo = new UserRepository();

        // Seed data
        Movie inception = new Movie();
        inception.setId("movie1");
        inception.setMovieName("Inception");
        inception.setGenre("Sci-Fi");
        inception.setRatings("8.8");
        movieRepo.add(inception);

        Movie interstellar = new Movie();
        interstellar.setId("movie2");
        interstellar.setMovieName("Interstellar");
        interstellar.setGenre("Sci-Fi");
        interstellar.setRatings("8.7");
        movieRepo.add(interstellar);

        Show show1 = new Show();
        show1.setId("show1");
        show1.setMovie(inception);
        show1.setStartTime("2024-03-20 18:00");
        show1.setEndTime("2024-03-20 20:30");
        showRepo.add(show1);

        Show show2 = new Show();
        show2.setId("show2");
        show2.setMovie(interstellar);
        show2.setStartTime("2024-03-20 21:00");
        show2.setEndTime("2024-03-20 23:45");
        showRepo.add(show2);

        // Services
        BookingService bookingService = new BookingService(showRepo, bookingRepo);

        // HTTP server
        Javalin app = Javalin.create().start(8080);
        new BookingController(bookingService, movieRepo, showRepo, bookingRepo)
                .registerRoutes(app);

        System.out.println("\n=== Movie Booking System Started on :8080 ===");

        // Run concurrency test with --test flag
        if (args.length > 0 && args[0].equals("--test")) {
            ConcurrencyTestHarness.test(bookingService, "show1");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }
}
