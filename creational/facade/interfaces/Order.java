package creational.facade.interfaces;

public class Order {
  private int orderId;
  private String itemName;
  private int quantity;
  private String status;

  public Order(int orderId2, String itemName, int quantity) {
    this.orderId = orderId2;
    this.itemName = itemName;
    this.quantity = quantity;
    this.status = "Ordered";
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return "Order{id='" + this.orderId + "', item='" + this.itemName + "', quantity=" + this.quantity + "}";
  }

  public Integer getOrderId() {
    return this.orderId;
  }
}
