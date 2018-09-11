package cmsc433.p1;

/**
 *  @author $Jack Cowan
 *  Last Updated 2-6-2017
 */


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;



public class AuctionServer
{
	/**
	 * Singleton: the following code makes the server a Singleton. You should
	 * not edit the code in the following noted section.
	 * 
	 * For test purposes, we made the constructor protected. 
	 */

	/* Singleton: Begin code that you SHOULD NOT CHANGE! */
	protected AuctionServer()
	{
	}

	private static AuctionServer instance = new AuctionServer();

	public static AuctionServer getInstance()
	{
		return instance;
	}

	/* Singleton: End code that you SHOULD NOT CHANGE! */





	/* Statistic variables and server constants: Begin code you should likely leave alone. */


	/**
	 * Server statistic variables and access methods:
	 */
	private int soldItemsCount = 0;
	private int revenue = 0;
	private int uncollectedRevenue = 0;

	public int soldItemsCount()
	{
		synchronized (instanceLock) {
			return this.soldItemsCount;
		}
	}

	public int revenue()
	{
		synchronized (instanceLock) {
			return this.revenue;
		}
	}
	
	public int uncollectedRevenue () {
		synchronized (instanceLock) {
			return this.uncollectedRevenue;
		}
	}



	/**
	 * Server restriction constants:
	 */
	public static final int maxBidCount = 10; // The maximum number of bids at any given time for a buyer.
	public static final int maxSellerItems = 20; // The maximum number of items that a seller can submit at any given time.
	public static final int serverCapacity = 80; // The maximum number of active items at a given time.


	/* Statistic variables and server constants: End code you should likely leave alone. */



	/**
	 * Some variables we think will be of potential use as you implement the server...
	 */

	// List of items currently up for bidding (will eventually remove things that have expired).
	private List<Item> itemsUpForBidding = new ArrayList<Item>();


	// The last value used as a listing ID.  We'll assume the first thing added gets a listing ID of 0.
	private int lastListingID = -1; 

	// List of item IDs and actual items.  This is a running list with everything ever added to the auction.
	private HashMap<Integer, Item> itemsAndIDs = new HashMap<Integer, Item>();

	// List of itemIDs and the highest bid for each item.  This is a running list with everything ever bid upon.
	private HashMap<Integer, Integer> highestBids = new HashMap<Integer, Integer>();

	// List of itemIDs and the person who made the highest bid for each item.   This is a running list with everything ever bid upon.
	private HashMap<Integer, String> highestBidders = new HashMap<Integer, String>(); 
	
	// List of Bidders who have been permanently banned because they failed to pay the amount they promised for an item. 
	private HashSet<String> blacklist = new HashSet<String>();
	
	// List of sellers and how many items they have currently up for bidding.
	private HashMap<String, Integer> itemsPerSeller = new HashMap<String, Integer>();

	// List of buyers and how many items on which they are currently bidding.
	private HashMap<String, Integer> itemsPerBuyer = new HashMap<String, Integer>();

	// List of itemIDs that have been paid for. This is a running list including everything ever paid for.
	private HashSet<Integer> itemsSold = new HashSet<Integer> ();

	// Object used for instance synchronization if you need to do it at some point 
	// since as a good practice we don't use synchronized (this) if we are doing internal
	// synchronization.
	//
	private Object instanceLock = new Object(); 









	/*
	 *  The code from this point forward can and should be changed to correctly and safely 
	 *  implement the methods as needed to create a working multi-threaded server for the 
	 *  system.  If you need to add Object instances here to use for locking, place a comment
	 *  with them saying what they represent.  Note that if they just represent one structure
	 *  then you should probably be using that structure's intrinsic lock.
	 */


	/**
	 * Attempt to submit an <code>Item</code> to the auction
	 * @param sellerName Name of the <code>Seller</code>
	 * @param itemName Name of the <code>Item</code>
	 * @param lowestBiddingPrice Opening price
	 * @param biddingDurationMs Bidding duration in milliseconds
	 * @return A positive, unique listing ID if the <code>Item</code> listed successfully, otherwise -1
	 */
	public int submitItem(String sellerName, String itemName, int lowestBiddingPrice, int biddingDurationMs)
	{
		// TODO: IMPLEMENT CODE HERE
		// Some reminders:
		//   Make sure there's room in the auction site.
		//   If the seller is a new one, add them to the list of sellers.
		//   If the seller has too many items up for bidding, don't let them add this one.
		//   Don't forget to increment the number of things the seller has currently listed.

		synchronized(instanceLock) //Lock on the server itself to maintain constance lastListingID
		{
			boolean sellerExists = itemsPerSeller.containsKey(sellerName);

			//Check if listing this item breaks server or seller capacity
			if (itemsUpForBidding.size() >= serverCapacity) //Server is full, can't list any more items
				return -1;
			else if (sellerExists && itemsPerSeller.get(sellerName) >= maxSellerItems) //Seller has too many items up for bid
				return -1;

			//Item can be added, create it and add it to the appropriate data structures
			lastListingID += 1; //Increment this when a new object is added

			Item toAdd = new Item(sellerName, itemName, lastListingID, lowestBiddingPrice, biddingDurationMs);
			itemsUpForBidding.add(toAdd); //Item is available to bid
			itemsAndIDs.put(lastListingID, toAdd); //Item has an ID
			//Check if this is a new seller, and increment their item total appropriately
			if (sellerExists)
			{
				int sellerItems = itemsPerSeller.get(sellerName);
				itemsPerSeller.put(sellerName, sellerItems + 1);
			}
			else
			{
				itemsPerSeller.put(sellerName, 1);
			}

			return lastListingID;
		}
	}

	/**
	 * Get all <code>Items</code> active in the auction
	 * @return A copy of the <code>List</code> of <code>Items</code>
	 */
	public List<Item> getItems()
	{
		// TODO: IMPLEMENT CODE HERE
		// Some reminders:
		//    Don't forget that whatever you return is now outside of your control.

		//Return a copy, not the actual list
		List<Item> temp = new ArrayList<Item>();
		synchronized(instanceLock) //Lock on server so list of items up for bidding is unchanged
		{
			for (int i = 0; i < itemsUpForBidding.size(); i++)
				temp.add(itemsUpForBidding.get(i));
		}

		return temp;
	}


	/**
	 * Attempt to submit a bid for an <code>Item</code>
	 * @param bidderName Name of the <code>Bidder</code>
	 * @param listingID Unique ID of the <code>Item</code>
	 * @param biddingAmount Total amount to bid
	 * @return True if successfully bid, false otherwise
	 */
	public boolean submitBid(String bidderName, int listingID, int biddingAmount)
	{
		// TODO: IMPLEMENT CODE HERE
		// Some reminders:
		//   See if the item exists.
		//   See if it can be bid upon.
		//   See if this bidder has too many items in their bidding list.
		//   Make sure the bidder has not been blacklisted
		//   Get current bidding info.
		//   See if they already hold the highest bid.
		//   See if the new bid isn't better than the existing/opening bid floor.
		//   Decrement the former winning bidder's count
		//   Put your bid in place

		synchronized(instanceLock) //Lock on the server while the bid is being submitted
		{
			boolean bidderExists = itemsPerBuyer.containsKey(bidderName);
			String highestBidder = ""; //If this item has been bid on, get name of highest bidder
			if (highestBidders.containsKey(listingID))
				highestBidder = highestBidders.get(listingID);

			//See if bid is invalid based on item's existence or auction rules
			Item itemInQuestion = itemsAndIDs.get(listingID);
			//System.out.println("ListingID: " + listingID + ": " + itemsUpForBidding.contains(itemInQuestion));
			if (!itemsUpForBidding.contains(itemInQuestion)) //Check if item exists and can be bid upon
			{
				System.out.println(bidderName + ": Item is not available for bidding");
				return false;
			}
			if (bidderExists && itemsPerBuyer.get(bidderName) >= maxBidCount) //Check if bidder has too many outstanding bids
			{
				System.out.println(bidderName + ": Bidder has too many outstanding bids");
				return false;
			}
			if (blacklist.contains(bidderName)) //Check if bidder has been blacklisted
			{
				System.out.println(bidderName + ": Bidder has been blacklisted");
				return false;
			}

			//Get Item from list of items to be bid on
			//We know it's available to be bid on, so grab it from itemsAndIDs
			Item potentialBid = itemsAndIDs.get(listingID);
			int bidPrice = -1;
			if (itemUnbid(listingID))
				bidPrice = potentialBid.lowestBiddingPrice();
			else
				bidPrice = highestBids.get(listingID);

			if (potentialBid != null) //The item exists in the list of items to be bid on
			{
				if (highestBidder.equals(bidderName)) //If this bidder already has the highest bid, bid is invalid
				{
					System.out.println(bidderName + ": Bidder already has highest bid");
					return false;
				}
				else if (biddingAmount <= bidPrice)
				{
					System.out.println(bidderName + ": Bid must be greater than current bid price");
				}
				else //At this point bid should be valid
				{
					//System.out.println(bidderName + ": Bid successfully submitted at this point");
					//Decrement former highest bidder's bid count
					String formerBidder = highestBidders.get(listingID);
					if (formerBidder != null) //Have to check if this actually exists
						itemsPerBuyer.put(formerBidder, itemsPerBuyer.get(formerBidder) - 1);

					//Put bid in place (update itemsPerBuyer, highestBids, and highestBidders)
					if (bidderExists) //Update itemsPerBuyer number
						itemsPerBuyer.put(bidderName, itemsPerBuyer.get(bidderName) + 1);
					else
						itemsPerBuyer.put(bidderName, 1);

					highestBids.put(listingID, biddingAmount);
					highestBidders.put(listingID, bidderName);

					return true;
				}
			}
		}

		//Should never reach this point
		return false;
	}

	/**
	 * Check the status of a <code>Bidder</code>'s bid on an <code>Item</code>
	 * @param bidderName Name of <code>Bidder</code>
	 * @param listingID Unique ID of the <code>Item</code>
	 * @return 1 (success) if bid is over and this <code>Bidder</code> has won<br>
	 * 2 (open) if this <code>Item</code> is still up for auction<br>
	 * 3 (failed) If this <code>Bidder</code> did not win or the <code>Item</code> does not exist
	 */
	public int checkBidStatus(String bidderName, int listingID)
	{
		final int SUCCESS = 1, OPEN = 2, FAILURE = 3;
		// TODO: IMPLEMENT CODE HERE
		// Some reminders:
		//   If the bidding is closed, clean up for that item.
		//     Remove item from the list of things up for bidding.
		//     Decrease the count of items being bid on by the winning bidder if there was any...
		//     Update the number of open bids for this seller
		//     If the item was sold to someone, update the uncollectedRevenue field appropriately

		synchronized(instanceLock) //Lock to make sure bid status doesn't change when checking it
		{
			if (!itemsAndIDs.containsKey(listingID)) //The given ID doesn't match an actual item in the auction server
				return FAILURE;

			Item checkItem = itemsAndIDs.get(listingID);
			String highestBidder; //Keep reference to who the highest bidder is currently; if no bids, null
			if (itemUnbid(listingID))
				highestBidder = null;
			else
				highestBidder = highestBidders.get(listingID);

			synchronized(checkItem) //Lock on item to make sure no other threads can modify it
			{
				if (checkItem.biddingOpen()) //If the item is still up for bid, return OPEN and do nothing else
					return OPEN;
			}

			//At this point bidding is closed, so clean up for the item
			//Remove it from all collections of active items
			itemsUpForBidding.remove(checkItem);
			//Find whoever sold this item and decrease their active auctions by 1
			if (highestBidder != null) //This item has been bid on
			{
				itemsPerBuyer.put(highestBidder, itemsPerBuyer.get(highestBidder) - 1);
				uncollectedRevenue += highestBids.get(listingID);
				System.out.println(bidderName + ": ID:" + listingID + ": uncollectedRevenue increased to: " + uncollectedRevenue);

				if (bidderName == highestBidder) //This bidder made the highest bid
					return SUCCESS;
				else
					return FAILURE;
			}
			else //This item has gone unbid
			{
				//Do Nothing?
			}
		}

		//Should never reach this point
		return FAILURE;
	}

	/**
	 * Check the current bid for an <code>Item</code>
	 * @param listingID Unique ID of the <code>Item</code>
	 * @return The highest bid so far or the opening price if there is no bid on the <code>Item</code>,
	 * or -1 if no <code>Item</code> with the given listingID exists
	 */
	public int itemPrice(int listingID)
	{
		// TODO: IMPLEMENT CODE HERE
		// Remember: once an item has been purchased, this method should continue to return the
		// highest bid, even if the buyer paid more than necessary for the item or if the buyer
		// is subsequently blacklisted

		synchronized(instanceLock) //Lock on server so that highest bid value is unchanged
		{
			if (!itemsAndIDs.containsKey(listingID)) //Check if item exists
				return -1;
			else if (!highestBids.containsKey(listingID)) //Check if item has a bid placed on it yet
				return itemsAndIDs.get(listingID).lowestBiddingPrice();
			else
				return highestBids.get(listingID);
		}
	}

	/**
	 * Check whether an <code>Item</code> has a bid on it
	 * @param listingID Unique ID of the <code>Item</code>
	 * @return True if there is no bid or the <code>Item</code> does not exist, false otherwise
	 */
	public boolean itemUnbid(int listingID)
	{
		// TODO: IMPLEMENT CODE HERE
		//Maybe doesn't need to be synchronized?
		synchronized(instanceLock) //Lock on server to get stable list of items
		{
			//If an item is in this list it has been bid on, and so is not unbid
			return !highestBids.containsKey(listingID);
		}
	}

	/**
	 * Pay for an <code>Item</code> that has already been won.
	 * @param bidderName Name of <code>Bidder</code>
	 * @param listingID Unique ID of the <code>Item</code>
	 * @param amount The amount the <code>Bidder</code> is paying for the item 
	 * @return The name of the <code>Item</code> won, or null if the <code>Item</code> was not won by the <code>Bidder</code> or if the <code>Item</code> did not exist
	 * @throws InsufficientFundsException If the <code>Bidder</code> did not pay at least the final selling price for the <code>Item</code>
	 */
	public String payForItem (String bidderName, int listingID, int amount) throws InsufficientFundsException {
		// TODO: IMPLEMENT CODE HERE
		// Remember:
		// - Check to make sure the buyer is the correct individual and can afford the item
		// - If the purchase is valid, update soldItemsCount, revenue, and uncollectedRevenue
		// - If the amount tendered is insufficient, cancel all active bids held by the buyer, 
		//   add the buyer to the blacklist, and throw an InsufficientFundsException

		synchronized(instanceLock) //Lock to pay for item without other threads interfering
		{
			if (!itemsAndIDs.containsKey(listingID)) //Item has to actually exist to be paid for
				return null;

			if (checkBidStatus(bidderName, listingID) == 1) //Bidder is the winner of the item
			{
				if (amount >= highestBids.get(listingID)) //Sufficient funds to pay for the item
				{
					uncollectedRevenue -= highestBids.get(listingID); //uncollectedRevenue only accounts for highest bid
					System.out.println(bidderName + ": ID:" + listingID + ": uncollectedRevenue decreased to: " + uncollectedRevenue);
					revenue += amount; //revenue collects the total amount submitted by the buyer
					soldItemsCount += 1; //1 more item is sold
					itemsSold.add(listingID); //Item goes in the itemsSold list

					return itemsAndIDs.get(listingID).name();
				}
				else //Insufficient funds to pay for item, cancel outstanding bids and blacklist seller
				{
					//Server gets no profit and the item is not considered sold
					//Cancel all outstanding bids, blacklist buyer
					itemsPerBuyer.put(bidderName, 0);
					blacklist.add(bidderName);

					//Remove buyer from list of highestBidders, it is as if the item was never bid upon in the first place (revert to opening price)
					System.out.println("Values:");
					System.out.println(highestBidders.values());
					while (highestBidders.values().remove(bidderName)); //Should keep looping while values contains bidderName
					throw new InsufficientFundsException();
				}
			}
			else //bidder did not win auction, or bidding is still open
			{
				return null;
			}
		}
	}

}
