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
		//Dont need to synchronize because reading size does not modify state
		while (Simulation.tables.size() >= Simulation.maxTables) {
			try {
				Thread.sleep(10); //Wait 10ms for a table to become available
			}
			catch (InterruptedException e) {
				//This should never happen, Customers will not be interrupted
				e.printStackTrace();
			}
		}	//Customer has now entered the restaurant

		//TODO: Synchronize this definitely
		synchronized(Simulation.tables) {
			Simulation.tables.add(new Object()); //Represents 1 filled table while customer is inside
		}
		Simulation.logEvent(SimulationEvent.customerEnteredRatsies(this));

		//Place an order here
		Simulation.logEvent(SimulationEvent.customerPlacedOrder(this, order, orderNum));
		Order placeOrder = new Order(order, orderNum); //Create new order object
		synchronized(Simulation.orders) {
			Simulation.orders.add(placeOrder); //TODO: Synchronize this definitely
		}

		//After the order is placed, wait() until notified that the order is ready
		try {
			placeOrder.wait(); //wait on the order object, will be released by Cook
		}
		catch (InterruptedException e) {
			//This should never happen, Customers will not be interrupted
			e.printStackTrace();
		}

		//At this point the thread is notified that the order is ready, and it can wake up
		//Order should have been removed from orders list by Cook processing it
		Simulation.logEvent(SimulationEvent.customerReceivedOrder(this, order, orderNum));

		//TODO: Synchronize this definitely
		synchronized(Simulation.tables) {
			Simulation.tables.poll(); //Remove 1 object from tables to represent a free table
		}
		Simulation.logEvent(SimulationEvent.customerLeavingRatsies(this));
	}
}
