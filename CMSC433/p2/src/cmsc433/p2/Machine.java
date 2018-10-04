package cmsc433.p2;

import java.util.ArrayList;

/**
 * A Machine is used to make a particular Food.  Each Machine makes
 * just one kind of Food.  Each machine has a capacity: it can make
 * that many food items in parallel; if the machine is asked to
 * produce a food item beyond its capacity, the requester blocks.
 * Each food item takes at least item.cookTimeS seconds to produce.
 */

public class Machine {
	
	// Types of machines used in Ratsie's.  Recall that enum types are
	// effectively "static" and "final", so each instance of Machine
	// will use the same MachineType.
	
	public enum MachineType { fountain, fryer, grillPress, oven };
	
	// Converts Machine instances into strings based on MachineType.
	
	public String toString() {
		switch (machineType) {
		case fountain: 		return "Fountain";
		case fryer:			return "Fryer";
		case grillPress:	return "Grill Press";
		case oven:			return "Oven";
		default:			return "INVALID MACHINE";
		}
	}
	
	public final MachineType machineType;
	public final Food machineFoodType;
	public final int capacity;

	//YOUR CODE GOES HERE...
	//ArrayList to represent food in the oven
	ArrayList<Food> foodInMachine;

	/**
	 * The constructor takes at least the type of the machine,
	 * the Food item it makes, and its capacity.  You may extend
	 * it with other arguments, if you wish.  Notice that the
	 * constructor currently does nothing with the capacity; you
	 * must add code to make use of this field (and do whatever
	 * initialization etc. you need).
	 */
	public Machine(MachineType machineType, Food food, int capacityIn) {
		this.machineType = machineType;
		this.machineFoodType = food;
		this.capacity = capacityIn;
		
		//YOUR CODE GOES HERE...
		//Initialize foodInMachine
		foodInMachine = new ArrayList<Food>();
		//Log on starting machine
		Simulation.logEvent(SimulationEvent.machineStarting(this, food, capacityIn));
	}

	/**
	 * This method is called by a Cook in order to make the Machine's
	 * food item.  You can extend this method however you like, e.g.,
	 * you can have it take extra parameters or return something other
	 * than Object.  It should block if the machine is currently at full
	 * capacity.  If not, the method should return, so the Cook making
	 * the call can proceed.  You will need to implement some means to
	 * notify the calling Cook when the food item is finished.
	 */
	public Object makeFood(Food food) throws InterruptedException {
		if (atCapacity()) {
			//Block if machine is at full capacity
			//Sleep for 20ms, then recur to try again and see if some room has been made available
			Thread.sleep(20);
			return makeFood(food);
		}
		else {
			//Add food to internal storage and log
			foodInMachine.add(food);
			Simulation.logEvent(SimulationEvent.machineCookingFood(this, food));

			//Start thread working on cooking food
			Thread worker = new Thread(new CookAnItem(food));
			worker.start();
			//wait on result of thread cooking food
			synchronized(food) {
				food.wait();
			}
			//Notified at this point, remove the object from machine
			//Also log that the food has been completed
			foodInMachine.remove(0);
			Simulation.logEvent(SimulationEvent.machineDoneFood(this, food));
			//TODO: Signal to Cook somehow that their food is ready
		}
		//YOUR CODE GOES HERE...
		return new Object();
	}

	//THIS MIGHT BE A USEFUL METHOD TO HAVE AND USE BUT IS JUST ONE IDEA
	private class CookAnItem implements Runnable {
		public Food foodCooking;

		public CookAnItem(Food food) {
			this.foodCooking = food;
		}

		public void run() {
			try {
				//Sleep for the amount of time that this food requires
				Thread.sleep(foodCooking.cookTimeS);
				synchronized(foodCooking) {
					foodCooking.notify();
				}

			}
			catch(InterruptedException e) {
				//Come here if thread gets interrupted while executing
				e.printStackTrace();
			}
		}
	}

	//Returns true if there is room for more food in the machine
	private boolean atCapacity() {
		if (foodInMachine.size() >= this.capacity) {
			System.out.println(toString() + ": is at capacity");
			return true;
		}
		else {
			System.out.println(toString() + ": is not at capacity");
			return false;
		}
	}
}
