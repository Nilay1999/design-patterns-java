package com.lld.snakeandladder.model;

import java.util.Random;

public class Dice {
    private int minValue;
    private int maxValue;
    private Random random;

    public Dice(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.random = new Random();
    }

    public int roll() {
        return random.nextInt(maxValue - minValue + 1) + minValue;
    }
}
