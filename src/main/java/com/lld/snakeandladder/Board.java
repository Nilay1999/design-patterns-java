package com.lld.snakeandladder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lld.snakeandladder.model.Cell;

public class Board {
    private int size;
    private List<Cell> cells;

    public Board(int size, Map<Integer, Jump> jumps) {
        this.size = size;
        this.cells = new ArrayList<>(size + 1);
        for (int i = 0; i <= size; i++) {
            cells.add(new Cell(i));
        }
        initializeJumps(jumps);
    }

    private void initializeJumps(Map<Integer, Jump> jumps) {
        for (Map.Entry<Integer, Jump> entry : jumps.entrySet()) {
            int startCellNumber = entry.getKey();
            Jump jump = entry.getValue();
            cells.get(startCellNumber).setJump(jump);
        }
    }

    public int getFinalPosition(int currentPosition, int diceValue) {
        int newPosition = currentPosition + diceValue;

        // Check if the new position is beyond the board
        if (newPosition > size) {
            System.out.println("Can't move, roll " + (size - currentPosition) + " or less to win.");
            return currentPosition;
        }

        // Check if the new cell has a jump (snake or ladder)
        Cell cell = cells.get(newPosition);
        if (cell.hasJump()) {
            Jump jump = cell.getJump();
            System.out.println(jump.getMessage());
            newPosition = jump.getEnd();
        }
        return newPosition;
    }

    public int getSize() {
        return size;
    }
}
