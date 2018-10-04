package cmsc433.p2;


/**
 * Cooks are simulation actors that have at least one field, a name.
 * When running, a cook attempts to retrieve outstanding orders placed
 * by Eaters and process them.
 */
public class Cook implements Runnable {
	private final String name;
	private Order currentOrder;

	/**
	 * You can feel free to modify this constructor.  It must
	 * take at least the name, but may take other parameters
	 * if you would find adding them useful. 
	 *
	 * @param: the name of the cook
	 */
	public Cook(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}

	/**
	 * This method executes as follows.  The cook tries to retrieve
	 * orders placed by Customers.  For each order, a List<Food>, the
	 * cook submits each Food item in the List to an appropriate
	 * Machine, by calling makeFood().  Once all machines have
	 * produced the desired Food, the order is complete, and the Customer
	 * is notified.  The cook can then go to process the next order.
	 * If during its execution the cook is interrupted (i.e., some
	 * other thread calls the interrupt() method on it, which could
	 * raise InterruptedException if the cook is blocking), then it
	 * terminates.
	 */
	public void run() {

		Simulation.logEvent(SimulationEvent.cookStarting(this));
		try {
			while(true) {
				//First, poll an order from the orders queue
				if (Simulation.orders.peek() != null) {
					//Synchronize on orders to make sure you can actually grab the element
					synchronized (Simulation.orders) {
						System.out.println("Cook poll() order here");
						currentOrder = Simulation.orders.poll();
					}
					Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, currentOrder.food, currentOrder.orderNumber));

					//Next, send each item off to the appropriate machine
					for (int i = 0; i < currentOrder.food.size(); i++) {
						System.out.println(currentOrder.food.get(i));
						sendToMachine(currentOrder.food.get(i));
						System.out.println("[Cook] sent to machine");
					}
					//TODO: Do more stuff here

					//TODO: Finish doing more stuff here
					Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, currentOrder.orderNumber));
					synchronized (currentOrder) {
						currentOrder.notify();
					}
				}
				else {
					//If there are no orders in the queue, just continue busy-waiting until there is one
				}
				//Check to see when Cook gets interrupted: if so, break out of the while loop
				if (Thread.interrupted()) {
					System.out.println("[Cook] has been interrupted");
					break;
				}
			}
		}
		catch(InterruptedException e) {
			System.out.println("[Cook] InterruptedException here");
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}

	private void sendToMachine(Food food) throws InterruptedException {
		switch(food.name)
		{
			case "wings":
				System.out.println("[Cook] Sending order to fryer");
				Simulation.fryer.makeFood(food);
				break;
			case "pizza":
				System.out.println("[Cook] Sending order to oven");
				Simulation.oven.makeFood(food);
				break;
			case "sub":
				System.out.println("[Cook] Sending order to grillPress");
				Simulation.grillPress.makeFood(food);
				break;
			case "soda":
				System.out.println("[Cook] Sending order to fountain");
				Simulation.fountain.makeFood(food);
				break;
		}
	}
}
