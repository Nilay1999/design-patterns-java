package com.lld.notificationsystem.interfaces;

import com.lld.notificationsystem.event.OrderEvent;

@FunctionalInterface
public interface OrderEventSubscriber {
    void onOrderEvent(OrderEvent event);
}