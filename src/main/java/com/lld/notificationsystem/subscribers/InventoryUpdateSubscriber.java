package com.lld.notificationsystem.subscribers;

import com.lld.notificationsystem.event.OrderEvent;
import com.lld.notificationsystem.interfaces.OrderEventSubscriber;

public class InventoryUpdateSubscriber implements OrderEventSubscriber {

    @Override
    public void onOrderEvent(OrderEvent event) {
        switch (event.getType()) {
            case ORDER_PLACED -> System.out.printf(
                    "[Inventory] Reserved stock for order %s%n",
                    event.getOrderId());
            case ORDER_CANCELLED -> System.out.printf(
                    "[Inventory] Released reserved stock for %s%n",
                    event.getOrderId());
            case ORDER_SHIPPED -> System.out.printf(
                    "[Inventory] Marked as dispatched: %s%n",
                    event.getOrderId());
        }
    }
}