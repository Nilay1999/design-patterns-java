package com.lld.questions.solutions.librarymanagement;

import java.time.Duration;

public class FlatRateFineStrategy implements FineStrategy {
    private final int flatDayRate = 3;

    @Override
    public double calculate(Holding holding) {
        if (holding.getGivenBack().isBefore(holding.getDeadline())) {
            return 0;
        }
        int days = (int) Duration.between(holding.getDeadline(), holding.getGivenBack()).toDays();
        double fine = days * flatDayRate;
        return fine;
    }
}
