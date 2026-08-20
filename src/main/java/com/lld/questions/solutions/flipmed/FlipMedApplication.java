package com.lld.questions.solutions.flipmed;

import com.lld.questions.solutions.flipmed.model.Appointment;
import com.lld.questions.solutions.flipmed.model.Speciality;
import com.lld.questions.solutions.flipmed.strategy.SlotRankingStrategy;
import com.lld.questions.solutions.flipmed.strategy.SlotRankingStrategy.AvailableSlot;

import java.util.List;

// Driver: wires up the service and runs a scripted demo, printing output.
public class FlipMedApplication {

    private final FlipMedService service = new FlipMedService();

    public static void main(String[] args) {
        new FlipMedApplication().runDemo();
    }

    private void runDemo() {
        section("1. Doctor registration & availability");
        registerDoctor("Curious", Speciality.CARDIOLOGIST, 4.5);
        markAvailability("Curious", "9:00-10:30"); // invalid: not 60 mins
        markAvailability("Curious", "9:00-10:00", "12:00-13:00", "16:00-17:00");
        registerDoctor("Dreadful", Speciality.DERMATOLOGIST, 4.2);
        markAvailability("Dreadful", "9:00-10:00", "11:00-12:00", "13:00-14:00");

        section("2. Search by speciality (default: rank by start time)");
        showAvailability(Speciality.CARDIOLOGIST, SlotRankingStrategy.BY_START_TIME);

        section("3. Patient registration & booking");
        registerPatient("PatientA");
        long b1 = bookAppointment("PatientA", "Curious", "12:00", false);
        showAvailability(Speciality.CARDIOLOGIST, SlotRankingStrategy.BY_START_TIME);

        section("4. Cancellation frees the slot");
        cancelAppointment(b1);
        showAvailability(Speciality.CARDIOLOGIST, SlotRankingStrategy.BY_START_TIME);

        section("5. More doctors + rank by rating (pluggable strategy)");
        registerDoctor("Daring", Speciality.DERMATOLOGIST, 4.9);
        markAvailability("Daring", "11:00-12:00", "14:00-15:00");
        System.out.println("-- ranked by start time --");
        showAvailability(Speciality.DERMATOLOGIST, SlotRankingStrategy.BY_START_TIME);
        System.out.println("-- same data, ranked by doctor rating --");
        showAvailability(Speciality.DERMATOLOGIST, SlotRankingStrategy.BY_RATING);

        section("6. Booking rules & the waitlist");
        registerPatient("PatientC");
        registerPatient("PatientD");
        registerPatient("PatientF");
        bookAppointment("PatientF", "Daring", "11:00", false);
        bookAppointment("PatientF", "Curious", "9:00", false);
        long confirmed16 = bookAppointment("PatientC", "Curious", "16:00", false);
        showAvailability(Speciality.CARDIOLOGIST, SlotRankingStrategy.BY_START_TIME);

        System.out.println("-- PatientD wants the full 16:00 slot, joins the waitlist --");
        bookAppointment("PatientD", "Curious", "16:00", true);

        System.out.println("-- PatientC cancels 16:00 -> PatientD auto-promoted --");
        cancelAppointment(confirmed16);

        section("7. Rule checks (expected failures, handled gracefully)");
        System.out.println("-- double-booking the same time slot with another doctor --");
        bookAppointment("PatientF", "Dreadful", "9:00", false); // F already has Curious 9:00
        System.out.println("-- booking an unknown patient --");
        bookAppointment("Ghost", "Curious", "12:00", false);
        System.out.println("-- booking a slot the doctor never offered --");
        bookAppointment("PatientC", "Curious", "10:00", false);

        section("8. View booked appointments");
        showAppointmentsForPatient("PatientF");
        showAppointmentsForPatient("PatientD");
        showAppointmentsForDoctor("Curious");
    }

    private void registerDoctor(String name, Speciality speciality, double rating) {
        try {
            service.registerDoctor(name, speciality, rating);
            System.out.println("Welcome Dr. " + name + " !!");
        } catch (RuntimeException e) {
            System.out.println("[error] " + e.getMessage());
        }
    }

    private void markAvailability(String doctorName, String... ranges) {
        try {
            service.markAvailability(doctorName, ranges);
            System.out.println("Done Doc! (Dr. " + doctorName + ": " + String.join(", ", ranges) + ")");
        } catch (RuntimeException e) {
            System.out.println("Sorry Dr. " + doctorName + " " + e.getMessage());
        }
    }

    private void registerPatient(String name) {
        try {
            service.registerPatient(name);
            System.out.println(name + " registered successfully.");
        } catch (RuntimeException e) {
            System.out.println("[error] " + e.getMessage());
        }
    }

    private void showAvailability(Speciality speciality, SlotRankingStrategy strategy) {
        List<AvailableSlot> slots = service.findAvailability(speciality, strategy);
        if (slots.isEmpty()) {
            System.out.println("No slots available for " + speciality);
            return;
        }
        slots.forEach(System.out::println);
    }

    private long bookAppointment(String patient, String doctor, String start, boolean waitlist) {
        try {
            Appointment a = service.bookAppointment(patient, doctor, start, waitlist);
            if (a.getStatus() == Appointment.Status.WAITLISTED) {
                System.out.println("Added to the waitlist. Booking id: " + a.getId());
            } else {
                System.out.println("Booked. Booking id: " + a.getId());
            }
            return a.getId();
        } catch (RuntimeException e) {
            System.out.println("[booking failed] " + e.getMessage());
            return -1;
        }
    }

    private void cancelAppointment(long bookingId) {
        try {
            FlipMedService.CancellationResult result = service.cancelAppointment(bookingId);
            System.out.println("Booking Cancelled (id: " + result.cancelled().getId() + ")");
            if (result.promoted() != null) {
                System.out.println("Booking confirmed for Booking id: " + result.promoted().getId());
            }
        } catch (RuntimeException e) {
            System.out.println("[cancel failed] " + e.getMessage());
        }
    }

    private void showAppointmentsForPatient(String patientName) {
        List<Appointment> appts = service.appointmentsForPatient(patientName);
        System.out.println("Appointments for " + patientName + ":");
        if (appts.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        appts.forEach(a -> System.out.println("  Booking id: " + a.getId()
                + ", Dr " + a.getDoctor().getName() + " " + a.slotLabel()));
    }

    private void showAppointmentsForDoctor(String doctorName) {
        List<Appointment> appts = service.appointmentsForDoctor(doctorName);
        System.out.println("Appointments for Dr. " + doctorName + ":");
        if (appts.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        appts.forEach(a -> System.out.println("  Booking id: " + a.getId()
                + ", " + a.getPatient().getName() + " " + a.slotLabel()));
    }

    private void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
