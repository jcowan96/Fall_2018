package cmsc433.p2;

import java.util.List;

/**
 * An order is what Customers submit to the simulation, then wait() for the Cooks on.
 * Cooks pull it from the queue, use machines to process it, and notify() on it for the Customer to resume operating
 */
public class Order {
    public final List<Food> food;
    public final int orderNumber;

    public Order(List<Food> foodOrder, int orderNum) {
        this.food = foodOrder;
        this.orderNumber = orderNum;
    }

    public String toString() {
        return orderNumber + ": " + food.toString();
    }
}
