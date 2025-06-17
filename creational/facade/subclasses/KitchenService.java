package creational.facade.subclasses;

import creational.facade.interfaces.Order;
import java.util.HashMap;
import java.util.Map;

public class KitchenService {
    private Map<Integer, Order> preparedOrders = new HashMap<>();
    private Map<Integer, Order> servedOrders = new HashMap<>();

    public KitchenService() {
    }

    public void prepareOrder(Order order) {
        order.setStatus("Prepared");
        preparedOrders.put(order.getOrderId(), order);
    }

    public void serveOrder(Order order) {
        order.setStatus("Served");
        servedOrders.put(order.getOrderId(), order);
    }

    public Order getPreparedOrder(int orderId) {
        return preparedOrders.get(orderId);
    }

    public Order getServedOrder(int orderId) {
        return servedOrders.get(orderId);
    }
}
