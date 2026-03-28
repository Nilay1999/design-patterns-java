package com.lld.notificationsystem.event;

public class OrderEvent {

    public enum Type {
        ORDER_PLACED,
        ORDER_SHIPPED,
        ORDER_CANCELLED
    }

    private final String orderId;
    private final Type type;
    private final double amount;

    public OrderEvent(String orderId, Type type, double amount) {
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public Type getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "[OrderEvent | id=" + orderId + " | type=" + type + " | amount=₹" + amount + "]";
    }
}
