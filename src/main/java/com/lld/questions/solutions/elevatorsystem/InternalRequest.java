package com.lld.questions.solutions.elevatorsystem;

public class InternalRequest extends Request {
    private final int targetFloor;

    public InternalRequest(int targetFloor) {
        super(RequestType.INTERNAL);
        this.targetFloor = targetFloor;
    }

    public int getTargetFloor() {
        return targetFloor;
    }
}
