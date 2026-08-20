package com.lld.questions.solutions.elevatorsystem;

import java.util.TreeSet;

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private ElevatorStatus status;
    // Floors we still need to stop at, sorted. A car button and a hall call
    // are both just "stop here", so we keep them in one set.
    private final TreeSet<Integer> stops;

    public Elevator(int id) {
        this.id = id;
        this.currentFloor = 0;
        this.direction = Direction.IDLE;
        this.status = ElevatorStatus.IDLE;
        this.stops = new TreeSet<>();
    }

    public void addRequest(int floor) {
        stops.add(floor);
    }

    public void step() {
        if (stops.isEmpty()) {
            direction = Direction.IDLE;
            status = ElevatorStatus.IDLE;
            return;
        }

        // Reached a requested floor: stop and serve it.
        if (stops.contains(currentFloor)) {
            stops.remove(currentFloor);
            status = ElevatorStatus.DOOR_OPEN;
            return;
        }

        // Otherwise move one floor toward the next stop. Keep going the current
        // way while there's still something ahead; reverse when there isn't.
        Integer above = stops.ceiling(currentFloor);
        Integer below = stops.floor(currentFloor);
        boolean goUp;
        if (direction == Direction.UP && above != null) {
            goUp = true; // 1. Already going up, still a stop above → keep going up
        } else if (direction == Direction.DOWN && below != null) {
            goUp = false; // 2. Already going down, still a stop below → keep going down
        } else if (above == null) {
            goUp = false; // 3. Nothing above → must go down
        } else if (below == null) {
            goUp = true; // 4. Nothing below → must go up
        } else {
            goUp = (above - currentFloor) <= (currentFloor - below); // 5. Pick the closer one
        }

        direction = goUp ? Direction.UP : Direction.DOWN;
        currentFloor += goUp ? 1 : -1;
        status = ElevatorStatus.MOVING;
    }

    public int getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public Direction getDirection() {
        return direction;
    }

    public ElevatorStatus getStatus() {
        return status;
    }

    public TreeSet<Integer> getStops() {
        return stops;
    }
}
