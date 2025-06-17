package creational.facade;

import creational.facade.interfaces.Order;
import creational.facade.subclasses.OrderService;
import creational.facade.subclasses.KitchenService;

public class Restraunt {
  private final OrderService orderService;
  private final KitchenService kitchenService;

  public Restraunt() {
    this.orderService = new OrderService();
    this.kitchenService = new KitchenService();
  }

  public Order placeOrder(int orderId, String itemName, int quantity) {
    Order order = orderService.takeOrder(orderId, itemName, quantity);
    kitchenService.prepareOrder(order);
    return order;
  }

  public void completeOrder(Order order) {
    kitchenService.serveOrder(order);
  }

  public String getOrderStatus(Order order) {
    return order.getStatus();
  }
}
