package com.lld.questions.solutions.flipmed.strategy;

import com.lld.questions.solutions.flipmed.model.Doctor;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

// Strategy pattern: default ranking is start time, but callers can plug in
// any other Comparator-backed strategy (e.g. rating) without touching the search code.
@FunctionalInterface
public interface SlotRankingStrategy {

        record AvailableSlot(Doctor doctor, int hour) {
                @Override
                public String toString() {
                        return "Dr." + doctor.getName() + ": (" + hour + ":00-" + (hour + 1) + ":00)";
                }
        }

        List<AvailableSlot> rank(List<AvailableSlot> slots);

        SlotRankingStrategy BY_START_TIME = slots -> slots.stream()
                        .sorted(Comparator.comparingInt(AvailableSlot::hour)
                                        .thenComparing(s -> s.doctor().getName()))
                        .collect(Collectors.toList());

        SlotRankingStrategy BY_RATING = slots -> slots.stream()
                        .sorted(Comparator.comparingDouble((AvailableSlot s) -> s.doctor().getRating()).reversed()
                                        .thenComparingInt(AvailableSlot::hour))
                        .collect(Collectors.toList());
}
