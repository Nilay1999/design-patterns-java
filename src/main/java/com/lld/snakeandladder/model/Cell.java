package com.lld.snakeandladder.model;

import com.lld.snakeandladder.Jump;

public class Cell {
    private int number;
    private Jump jump;

    public Cell(int number) {
        this.number = number;
        this.jump = null;
    }

    public boolean hasJump() {
        return this.jump != null;
    }

    public Jump getJump() {
        return jump;
    }

    public void setJump(Jump jump) {
        this.jump = jump;
    }

    public int getNumber() {
        return this.number;
    }
}
