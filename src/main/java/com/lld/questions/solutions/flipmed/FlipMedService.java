package com.lld.questions.solutions.flipmed;

import com.lld.questions.solutions.flipmed.model.Appointment;
import com.lld.questions.solutions.flipmed.model.Doctor;
import com.lld.questions.solutions.flipmed.model.Patient;
import com.lld.questions.solutions.flipmed.model.Speciality;
import com.lld.questions.solutions.flipmed.strategy.SlotRankingStrategy;
import com.lld.questions.solutions.flipmed.strategy.SlotRankingStrategy.AvailableSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

// All in-memory state and business rules live here: no DB, no UI, just plain maps.
public class FlipMedService {

    public record CancellationResult(Appointment cancelled, Appointment promoted) {
    }

    private final Map<String, Doctor> doctors = new HashMap<>();
    private final Map<String, Patient> patients = new HashMap<>();
    private final Map<Long, Appointment> appointments = new LinkedHashMap<>();
    private final Map<String, LinkedList<Long>> waitlist = new HashMap<>(); // key: "doctor#hour", FIFO
    private final AtomicLong idGenerator = new AtomicLong(1233); // first id -> 1234

    public Doctor registerDoctor(String name, Speciality speciality, double rating) {
        if (doctors.containsKey(name)) {
            throw new IllegalArgumentException("Doctor already registered: " + name);
        }
        Doctor doctor = new Doctor(name, speciality, rating);
        doctors.put(name, doctor);
        return doctor;
    }

    public Patient registerPatient(String name) {
        if (patients.containsKey(name)) {
            throw new IllegalArgumentException("Patient already registered: " + name);
        }
        Patient patient = new Patient(name);
        patients.put(name, patient);
        return patient;
    }

    public void markAvailability(String doctorName, String... ranges) {
        Doctor doctor = getDoctor(doctorName);
        for (String range : ranges) {
            doctor.addAvailableHour(parseSlotStartHour(range));
        }
    }

    public List<AvailableSlot> findAvailability(Speciality speciality, SlotRankingStrategy strategy) {
        List<AvailableSlot> available = new ArrayList<>();
        for (Doctor doctor : doctors.values()) {
            if (doctor.getSpeciality() != speciality) {
                continue;
            }
            for (int hour : doctor.getAvailableHours()) {
                if (!isConfirmed(doctor, hour)) {
                    available.add(new AvailableSlot(doctor, hour));
                }
            }
        }
        return strategy.rank(available);
    }

    public Appointment bookAppointment(String patientName, String doctorName, String startTime, boolean joinWaitlist) {
        Patient patient = getPatient(patientName);
        Doctor doctor = getDoctor(doctorName);
        int hour = parseHour(startTime);

        if (!doctor.offers(hour)) {
            throw new IllegalStateException("Dr. " + doctorName + " is not available at " + startTime);
        }
        if (patientHasConfirmedAt(patient, hour)) {
            throw new IllegalStateException(patientName + " already has an appointment at " + startTime);
        }

        Appointment.Status status = Appointment.Status.CONFIRMED;
        if (isConfirmed(doctor, hour)) {
            if (!joinWaitlist) {
                throw new IllegalStateException(
                        "Slot " + startTime + " with Dr. " + doctorName + " is already booked");
            }
            status = Appointment.Status.WAITLISTED;
        }

        Appointment appointment = new Appointment(idGenerator.incrementAndGet(), patient, doctor, hour, status);
        appointments.put(appointment.getId(), appointment);
        if (status == Appointment.Status.WAITLISTED) {
            waitlist.computeIfAbsent(waitlistKey(doctorName, hour), k -> new LinkedList<>()).add(appointment.getId());
        }
        return appointment;
    }

    public CancellationResult cancelAppointment(long bookingId) {
        Appointment appointment = appointments.get(bookingId);
        if (appointment == null) {
            throw new NoSuchElementException("No booking found with id: " + bookingId);
        }
        if (appointment.getStatus() == Appointment.Status.CANCELLED) {
            throw new IllegalStateException("Booking " + bookingId + " is already cancelled");
        }

        boolean freedConfirmedSlot = appointment.getStatus() == Appointment.Status.CONFIRMED;
        appointment.setStatus(Appointment.Status.CANCELLED);

        Appointment promoted = null;
        if (freedConfirmedSlot) {
            promoted = promoteFromWaitlist(appointment.getDoctor().getName(), appointment.getHour());
        }
        return new CancellationResult(appointment, promoted);
    }

    public List<Appointment> appointmentsForPatient(String patientName) {
        getPatient(patientName);
        return appointments.values().stream()
                .filter(a -> a.getStatus() == Appointment.Status.CONFIRMED)
                .filter(a -> a.getPatient().getName().equals(patientName))
                .sorted(Comparator.comparingInt(Appointment::getHour))
                .collect(Collectors.toList());
    }

    public List<Appointment> appointmentsForDoctor(String doctorName) {
        getDoctor(doctorName);
        return appointments.values().stream()
                .filter(a -> a.getStatus() == Appointment.Status.CONFIRMED)
                .filter(a -> a.getDoctor().getName().equals(doctorName))
                .sorted(Comparator.comparingInt(Appointment::getHour))
                .collect(Collectors.toList());
    }

    private Appointment promoteFromWaitlist(String doctorName, int hour) {
        LinkedList<Long> waiting = waitlist.get(waitlistKey(doctorName, hour));
        if (waiting == null || waiting.isEmpty()) {
            return null;
        }
        Appointment next = appointments.get(waiting.removeFirst());
        next.setStatus(Appointment.Status.CONFIRMED);
        return next;
    }

    private boolean isConfirmed(Doctor doctor, int hour) {
        return appointments.values().stream().anyMatch(a -> a.getStatus() == Appointment.Status.CONFIRMED
                && a.getDoctor().getName().equals(doctor.getName())
                && a.getHour() == hour);
    }

    private boolean patientHasConfirmedAt(Patient patient, int hour) {
        return appointments.values().stream().anyMatch(a -> a.getStatus() == Appointment.Status.CONFIRMED
                && a.getPatient().getName().equals(patient.getName())
                && a.getHour() == hour);
    }

    private Doctor getDoctor(String name) {
        Doctor doctor = doctors.get(name);
        if (doctor == null) {
            throw new NoSuchElementException("No doctor registered with name: " + name);
        }
        return doctor;
    }

    private Patient getPatient(String name) {
        Patient patient = patients.get(name);
        if (patient == null) {
            throw new NoSuchElementException("No patient registered with name: " + name);
        }
        return patient;
    }

    private static String waitlistKey(String doctorName, int hour) {
        return doctorName + "#" + hour;
    }

    // Slots always start on the hour, so minutes just need to be validated as
    // ":00".
    private static int parseHour(String time) {
        String[] parts = time.trim().split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        if (minute != 0) {
            throw new IllegalArgumentException("slots must start on the hour");
        }
        return hour;
    }

    private static int parseSlotStartHour(String range) {
        String[] parts = range.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid slot range: " + range);
        }
        int start = parseHour(parts[0]);
        int end = parseHour(parts[1]);
        if (end - start != 1) {
            throw new IllegalArgumentException("slots are 60 mins only");
        }
        if (start < 9 || end > 21) {
            throw new IllegalArgumentException("slots must be within 9:00 to 21:00");
        }
        return start;
    }
}
