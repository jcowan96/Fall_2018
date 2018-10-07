package cmsc433.p2;

import java.util.List;

/**
 * Customers are simulation actors that have two fields: a name, and a list
 * of Food items that constitute the Customer's order.  When running, an
 * customer attempts to enter the Ratsie's (only successful if the
 * Ratsie's has a free table), place its order, and then leave the
 * Ratsie's when the order is complete.
 */
public class Customer implements Runnable {
	//JUST ONE SET OF IDEAS ON HOW TO SET THINGS UP...
	private final String name;
	private final List<Food> order;
	private final int orderNum;    
	
	private static int runningCounter = 0;

	//Global non-static boolean to determine if the customer has entered Ratsie's
	private boolean hasEntered = false;

	/**
	 * You can feel free modify this constructor.  It must take at
	 * least the name and order but may take other parameters if you
	 * would find adding them useful.
	 */
	public Customer(String name, List<Food> order) {
		this.name = name;
		this.order = order;
		this.orderNum = ++runningCounter;
	}

	public String toString() {
		return name;
	}

	/** 
	 * This method defines what an Customer does: The customer attempts to
	 * enter the Ratsie's (only successful when the Ratsie's has a
	 * free table), place its order, and then leave the Ratsie's
	 * when the order is complete.
	 */
	public void run() {
		Simulation.logEvent(SimulationEvent.customerStarting(this));
		//Attempt to enter the restaurant, can only do so if not all tables are full
		while (!hasEntered) {
			attemptToEnterRatsies();
		}	//Customer has now entered the restaurant



		//Place an order here
		Simulation.logEvent(SimulationEvent.customerPlacedOrder(this, order, orderNum));
		Order placeOrder = new Order(order, orderNum); //Create new order object
		synchronized(Simulation.orders) {
			Simulation.orders.add(placeOrder);
			//System.out.println(toString() + " Placed order: " + Simulation.orders);
		}

		//After the order is placed, wait() until notified that the order is ready
		try {
			//Have to synchronize on object in order to wait()
			synchronized(placeOrder) {
				placeOrder.wait(); //wait on the order object, will be released by Cook
			}
		}
		catch (InterruptedException e) {
			//This should never happen, Customers will not be interrupted
			e.printStackTrace();
		}

		//At this point the thread is notified that the order is ready, and it can wake up
		//Order should have been removed from orders list by Cook processing it
		Simulation.logEvent(SimulationEvent.customerReceivedOrder(this, order, orderNum));

		//Synchronize leaving Ratsie's, freeing up space for another customer to enter
		synchronized(Simulation.tables) {
			Simulation.tables.poll(); //Remove 1 object from tables to represent a free table
		}
		Simulation.logEvent(SimulationEvent.customerLeavingRatsies(this));
	}

	//Checks if there is room at Ratsies to get a table, and allows the Customer
	//to enter in a synchronized way
	public void attemptToEnterRatsies() {
		synchronized(Simulation.tables) {
			if (Simulation.tables.size() < Simulation.maxTables) {
				Simulation.tables.add(new Object());
				Simulation.logEvent(SimulationEvent.customerEnteredRatsies(this));
				//Make sure to set global boolean
				hasEntered = true;
			}
		}
	}
}
