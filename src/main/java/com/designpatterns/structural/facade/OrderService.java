package com.designpatterns.structural.facade;

public class OrderService {
  public OrderService() {
  }

  public Order takeOrder(int orderId, String itemName, int quantity) {
    Order order = new Order(orderId, itemName, quantity);
    order.setStatus("Ordered");
    System.out.println("Order taken: " + itemName);
    return order;
  }
}
