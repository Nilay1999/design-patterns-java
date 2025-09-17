package com.lld.snakeandladder;

public class Snake extends Jump {
    public Snake(int start, int end) {
        super(start, end);
    }

    @Override
    public String getMessage() {
        return "Oops! Swallowed by a Snake! Slither down to " + getEnd();
    }
}
