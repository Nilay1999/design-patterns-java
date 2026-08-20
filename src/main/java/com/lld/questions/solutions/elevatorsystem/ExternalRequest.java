package com.lld.questions.solutions.elevatorsystem;

public class ExternalRequest extends Request {
    private final int floor;
    private final Direction direction;

    public ExternalRequest(int floor, Direction direction) {
        super(RequestType.EXTERNAL);
        this.floor = floor;
        this.direction = direction;
    }

    public int getFloor() {
        return floor;
    }

    public Direction getDirection() {
        return direction;
    }
}