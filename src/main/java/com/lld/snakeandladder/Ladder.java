package com.lld.snakeandladder;

public class Ladder extends Snake {
    public Ladder(int start, int end) {
        super(start, end);
    }

    @Override
    public String getMessage() {
        return "Wow! You got a ladder, climbed to " + getEnd();
    }
}
