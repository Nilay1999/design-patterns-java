package com.lld.questions.solutions.atmmachine;

import java.util.EnumMap;
import java.util.Map;

public class CashDispenser {

    private final Map<Denomination, Integer> notes = new EnumMap<>(Denomination.class);

    public CashDispenser() {
        notes.put(Denomination.TWO_THOUSAND, 50);
        notes.put(Denomination.FIVE_HUNDRED, 100);
        notes.put(Denomination.TWO_HUNDRED, 100);
        notes.put(Denomination.ONE_HUNDRED, 200);
    }

    public Map<Denomination, Integer> dispenseCash(int amount) {
        if (!canDispense(amount)) {
            throw new IllegalArgumentException("Cannot dispense amount: " + amount);
        }

        Map<Denomination, Integer> dispensed = new EnumMap<>(Denomination.class);

        int remaining = amount;
        for (Denomination denomination : Denomination.values()) {
            int available = notes.get(denomination);
            int required = remaining / denomination.getValue();
            int dispense = Math.min(required, available);
            if (dispense > 0) {
                dispensed.put(denomination, dispense);
                notes.put(denomination, available - dispense);
                remaining -= dispense * denomination.getValue();
            }
        }

        return dispensed;
    }

    public boolean canDispense(int amount) {
        int remaining = amount;
        for (Denomination denomination : Denomination.values()) {
            int available = notes.get(denomination);
            int required = remaining / denomination.getValue();
            int dispense = Math.min(required, available);
            remaining -= dispense * denomination.getValue();
        }
        return remaining == 0;
    }

    public int getNotes(Denomination denomination) {
        return notes.getOrDefault(denomination, 0);
    }
}