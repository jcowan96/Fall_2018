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
				//Synchronize on orders to make sure you can actually grab the element
				synchronized (Simulation.orders) {
					currentOrder = Simulation.orders.poll();
				}
				//Gotta check if you actually got an order, otherwise just continue
				if (currentOrder != null) {
					Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, currentOrder.food, currentOrder.orderNumber));

					//Create array of threads
					Thread[] threads = new Thread[currentOrder.food.size()];

					//Next, send each item off to the appropriate machine
					for (int i = 0; i < currentOrder.food.size(); i++) {
						threads[i] = new Thread(new CookWorker(this, currentOrder.food.get(i), currentOrder.orderNumber));
						threads[i].start();
						//sendToMachine(currentOrder.food.get(i));
					}

					//Wait for all the threads to finish
					for (int i = 0; i < threads.length; i++)
						threads[i].join();

					Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, currentOrder.orderNumber));
					synchronized (currentOrder) {
						currentOrder.notify(); //Notify the customer their order has been completed
					}
				}
				//Check to see when Cook gets interrupted: if so, break out of the while loop
				if (Thread.interrupted()) {
					Simulation.logEvent(SimulationEvent.cookEnding(this));
					break;
				}
			}

		}
		catch(InterruptedException e) {
			//System.out.println(toString() + ": InterruptedException here");
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}

	//Thread class to cook multiple items in parallel
	//Cook is allowed to have unlimited threads, can do as much as it wants in as little time as it needs
	private class CookWorker implements Runnable {
		public Food foodCooking;
		public int orderNumber;
		public Cook currentCook;

		public CookWorker(Cook cook, Food food, int num) {
			this.currentCook = cook;
			this.foodCooking = food;
			this.orderNumber = num;
		}

		public void run() {
			try {
				sendToMachine();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		private void sendToMachine() throws InterruptedException {
			switch(foodCooking.name)
			{
				case "wings":
					Simulation.fryerSem.acquire();
					Simulation.logEvent(SimulationEvent.cookStartedFood(currentCook, foodCooking, orderNumber));
					Simulation.fryer.makeFood(foodCooking);
					Simulation.fryerSem.release();
					Simulation.logEvent(SimulationEvent.cookFinishedFood(currentCook, foodCooking, orderNumber));
					break;
				case "pizza":
					Simulation.ovenSem.acquire();
					Simulation.logEvent(SimulationEvent.cookStartedFood(currentCook, foodCooking, orderNumber));
					Simulation.oven.makeFood(foodCooking);
					Simulation.ovenSem.release();
					Simulation.logEvent(SimulationEvent.cookFinishedFood(currentCook, foodCooking, orderNumber));
					break;
				case "sub":
					Simulation.grillPressSem.acquire();
					Simulation.logEvent(SimulationEvent.cookStartedFood(currentCook, foodCooking, orderNumber));
					Simulation.grillPress.makeFood(foodCooking);
					Simulation.grillPressSem.release();
					Simulation.logEvent(SimulationEvent.cookFinishedFood(currentCook, foodCooking, orderNumber));
					break;
				case "soda":
					Simulation.fountainSem.acquire();
					Simulation.logEvent(SimulationEvent.cookStartedFood(currentCook, foodCooking, orderNumber));
					Simulation.fountain.makeFood(foodCooking);
					Simulation.fountainSem.release();
					Simulation.logEvent(SimulationEvent.cookFinishedFood(currentCook, foodCooking, orderNumber));
					break;
			}
		}
	}
}
