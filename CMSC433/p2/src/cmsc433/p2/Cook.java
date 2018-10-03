package cmsc433.p2;


/**
 * Cooks are simulation actors that have at least one field, a name.
 * When running, a cook attempts to retrieve outstanding orders placed
 * by Eaters and process them.
 */
public class Cook implements Runnable {
	private final String name;

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
				Order currentOrder;
				synchronized(Simulation.orders) {
					currentOrder = Simulation.orders.poll(); //TODO: Synchronize this definitely
				}
				Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, currentOrder.food, currentOrder.orderNumber));

				//Next, send each item off to a machine
				//TODO: was tired when i wrote this so make sure it actually makes sense
				for (int i = 0; i < currentOrder.food.size(); i++) {
					switch (currentOrder.food.get(i).name)
					{
						case "wings":
							Simulation.fryer.makeFood(currentOrder.food.get(i));
							break;
						case "pizza":
							Simulation.oven.makeFood(currentOrder.food.get(i));
							break;
						case "sub":
							Simulation.grillPress.makeFood(currentOrder.food.get(i));
							break;
						case "soda":
							Simulation.fountain.makeFood(currentOrder.food.get(i));
							break;
					}
				}
				//TODO: Do more stuff here

				//TODO: Finish doing more stuff here
				Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, currentOrder.orderNumber));
				currentOrder.notify();
				throw new InterruptedException(); //TODO: REMOVE THIS
			}
		}
		catch(InterruptedException e) {
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}

	private void sendToMachine(Food food) {
		switch (food.name)
		{
			case "wings":
				Simulation.fryer.makeFood(food);
				break;
			case "pizza":
				Simulation.oven.makeFood(food);
				break;
			case "sub":
				Simulation.grillPress.makeFood(food);
				break;
			case "soda":
				Simulation.fountain.makeFood(food);
				break;

		}
	}
}
