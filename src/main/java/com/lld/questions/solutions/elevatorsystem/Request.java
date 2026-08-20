package com.lld.questions.solutions.elevatorsystem;

public abstract class Request {
    private final RequestType type;

    protected Request(RequestType type) {
        this.type = type;
    }

    public RequestType getType() {
        return type;
    }
}