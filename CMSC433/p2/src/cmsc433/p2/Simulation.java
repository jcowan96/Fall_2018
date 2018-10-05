package cmsc433.p2;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Queue;

import java.util.concurrent.Semaphore;

/**
 * Simulation is the main class used to run the simulation.  You may
 * add any fields (static or instance) or any methods you wish.
 */
public class Simulation {
	// List to track simulation events during simulation
	public static List<SimulationEvent> events;
	public static Queue<Object> tables;
	public static Queue<Order> orders;

	//Expose numTables to outside classes
	public static int maxTables;

	//Exactly 4 static machines to be accessed by Cooks
	public static Machine fryer;
	public static Machine oven;
	public static Machine grillPress;
	public static Machine fountain;

	//Static semaphores to represent access to Machines
	public static Semaphore fryerSem;
	public static Semaphore ovenSem;
	public static Semaphore grillPressSem;
	public static Semaphore fountainSem;

	/**
	 * Used by other classes in the simulation to log events
	 * @param event
	 */
	public static void logEvent(SimulationEvent event) {
		events.add(event);
		System.out.println(event);
	}

	/**
	 * 	Function responsible for performing the simulation. Returns a List of 
	 *  SimulationEvent objects, constructed any way you see fit. This List will
	 *  be validated by a call to Validate.validateSimulation. This method is
	 *  called from Simulation.main(). We should be able to test your code by 
	 *  only calling runSimulation.
	 *  
	 *  Parameters:
	 *	@param numCustomers the number of customers wanting to enter Ratsie's
	 *	@param numCooks the number of cooks in the simulation
	 *	@param numTables the number of tables in Ratsie's (i.e. Ratsie's capacity)
	 *	@param machineCapacity the capacity of all machines in Ratsie's
	 *  @param randomOrders a flag say whether or not to give each customer a random order
	 *
	 */
	public static List<SimulationEvent> runSimulation(
			int numCustomers,
			int numCooks,
			int numTables, 
			int machineCapacity,
			boolean randomOrders
			) {

		// This method's signature MUST NOT CHANGE.  


		// We are providing this events list object for you.  
		// It is the ONLY PLACE where a concurrent collection object is 
		// allowed to be used.
		events = Collections.synchronizedList(new ArrayList<SimulationEvent>());




		// Start the simulation
		logEvent(SimulationEvent.startSimulation(numCustomers,
				numCooks,
				numTables,
				machineCapacity));



		// Set things up you might need
		maxTables = numTables; //Exposes max table capacity to Customers
		tables = new LinkedList<Object>(); //Represents tables currently filled in the restaraunt
		orders = new LinkedList<Order>(); //List of submitted orders not being worked on by cooks

		// Start up machines
		// 1 machine for each food type, and all machines have same capacity as defined by simulation
		fryer = new Machine(Machine.MachineType.fryer, FoodType.wings, machineCapacity);
		oven = new Machine(Machine.MachineType.oven, FoodType.pizza, machineCapacity);
		grillPress = new Machine(Machine.MachineType.grillPress, FoodType.sub, machineCapacity);
		fountain = new Machine(Machine.MachineType.fountain, FoodType.soda, machineCapacity);

		//Initialize Machine semaphores with permits equal to the Machines' capacity
		fryerSem = new Semaphore(machineCapacity);
		ovenSem = new Semaphore(machineCapacity);
		grillPressSem = new Semaphore(machineCapacity);
		fountainSem = new Semaphore(machineCapacity);


		// Let cooks in
		Thread[] cooks = new Thread[numCooks];
		for (int i = 0; i < numCooks; i++) {
			cooks[i] = new Thread(
					new Cook("Cook " + (i))
					);
			cooks[i].start(); //Start cooks up right away, they're on infinite loops
		}

		// Build the customers.
		Thread[] customers = new Thread[numCustomers];
		LinkedList<Food> order;
		if (!randomOrders) {
			order = new LinkedList<Food>();
			order.add(FoodType.wings);
			order.add(FoodType.pizza);
			order.add(FoodType.sub);
			order.add(FoodType.soda);
			for(int i = 0; i < customers.length; i++) {
				customers[i] = new Thread(
						new Customer("Customer " + (i), order)
						);
			}
		}
		else {
			for(int i = 0; i < customers.length; i++) {
				Random rnd = new Random();
				int wingsCount = rnd.nextInt(4);
				int pizzaCount = rnd.nextInt(4);
				int subCount = rnd.nextInt(4);
				int sodaCount = rnd.nextInt(4);
				order = new LinkedList<Food>();
				for (int b = 0; b < wingsCount; b++) {
					order.add(FoodType.wings);
				}
				for (int f = 0; f < pizzaCount; f++) {
					order.add(FoodType.pizza);
				}
				for (int f = 0; f < subCount; f++) {
					order.add(FoodType.sub);
				}
				for (int c = 0; c < sodaCount; c++) {
					order.add(FoodType.soda);
				}
				customers[i] = new Thread(
						new Customer("Customer " + (i+1), order)
				);
			}
		}


		// Now "let the customers know the shop is open" by
		//    starting them running in their own thread.
		for(int i = 0; i < customers.length; i++) {
			customers[i].start();
			//NOTE: Starting the customer does NOT mean they get to go
			//      right into the shop.  There has to be a table for
			//      them.  The Customer class' run method has many jobs
			//      to do - one of these is waiting for an available
			//      table...
		}



		try {
			// Wait for customers to finish
			//   -- you need to add some code here...
			for (int i = 0; i < customers.length; i++)
				customers[i].join();

			
			
			

			// Then send cooks home...
			// The easiest way to do this might be the following, where
			// we interrupt their threads.  There are other approaches
			// though, so you can change this if you want to.
			for(int i = 0; i < cooks.length; i++) {
				cooks[i].interrupt();
			}
			for(int i = 0; i < cooks.length; i++) {
				cooks[i].join();
			}
		}
		catch(InterruptedException e) {
			System.out.println("Simulation thread interrupted.");
		}

		// Shut down machines
		logEvent(SimulationEvent.machineEnding(fryer));
		logEvent(SimulationEvent.machineEnding(oven));
		logEvent(SimulationEvent.machineEnding(grillPress));
		logEvent(SimulationEvent.machineEnding(fountain));




		// Done with simulation		
		logEvent(SimulationEvent.endSimulation());

		return events;
	}

	/**
	 * Entry point for the simulation.
	 *
	 * @param args the command-line arguments for the simulation.  There
	 * should be exactly four arguments: the first is the number of customers,
	 * the second is the number of cooks, the third is the number of tables
	 * in Ratsie's, and the fourth is the number of items each machine
	 * can make at the same time.  
	 */
	public static void main(String args[]) throws InterruptedException {
		// Parameters to the simulation
		/*
		if (args.length != 4) {
			System.err.println("usage: java Simulation <#customers> <#cooks> <#tables> <capacity> <randomorders");
			System.exit(1);
		}
		int numCustomers = new Integer(args[0]).intValue();
		int numCooks = new Integer(args[1]).intValue();
		int numTables = new Integer(args[2]).intValue();
		int machineCapacity = new Integer(args[3]).intValue();
		boolean randomOrders = new Boolean(args[4]);
		 */
		int numCustomers = 5;
		int numCooks = 2;
		int numTables = 12;
		int machineCapacity = 4;
		boolean randomOrders = true;


		// Run the simulation and then 
		//   feed the result into the method to validate simulation.
		System.out.println("Did it work? " + 
				Validate.validateSimulation(
						runSimulation(
								numCustomers, numCooks, 
								numTables, machineCapacity,
								randomOrders
								)
						)
				);
	}

}
