package com.lld.snakeandladder;

abstract public class Jump {
    private int start;
    private int end;

    public Jump(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return this.start;
    }

    public int getEnd() {
        return this.end;
    }

    abstract String getMessage();
}
