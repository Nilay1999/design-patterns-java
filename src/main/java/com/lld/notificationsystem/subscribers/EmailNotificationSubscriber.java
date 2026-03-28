package com.lld.notificationsystem.subscribers;

import com.lld.notificationsystem.event.OrderEvent;
import com.lld.notificationsystem.interfaces.OrderEventSubscriber;

public class EmailNotificationSubscriber implements OrderEventSubscriber {

    @Override
    public void onOrderEvent(OrderEvent event) {
        switch (event.getType()) {
            case ORDER_PLACED -> System.out.printf(
                    "[Email] Order confirmation sent for %s (₹%.2f)%n",
                    event.getOrderId(), event.getAmount());
            case ORDER_SHIPPED -> System.out.printf(
                    "[Email] Shipping notification sent for %s%n",
                    event.getOrderId());
            case ORDER_CANCELLED -> System.out.printf(
                    "[Email] Cancellation email sent for %s%n",
                    event.getOrderId());
        }
    }
}