package net.runelite.client.plugins.potiondecanter;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Potion decanter",
	description = "Type ::decant to decant potions",
	tags = {"decant", "potions", "pots"}
)
public class PotionDecanterPlugin extends Plugin
{
	@Inject
	private Client client;

	private Map<Integer, PotionDefinition> potionDefinitionMap;

	@Override
	protected void startUp() throws Exception
	{
		setupDefinitions();
	}

	@Override
	protected void shutDown() throws Exception
	{
		potionDefinitionMap = null;
	}

	@Subscribe
	public void onCommand(CommandExecuted commandExecuted)
	{
		if (commandExecuted.getCommand().toLowerCase().equals("decant"))
		{
			String[] args = commandExecuted.getArguments();
			if (args.length >= 1)
			{
				int targetDoses;
				try
				{
					targetDoses = Integer.parseInt(args[0]);
				}
				catch (NumberFormatException ex)
				{
					print("Invalid amount of doses");
					return;
				}

				decantPots(targetDoses);
			}

		}
	}

	private void print(String message)
	{
		client.addChatMessage(ChatMessageType.SERVER, "", message, null);
	}

	private void setupDefinitions()
	{
		potionDefinitionMap = new HashMap<>();
		for (PotionType potionType : PotionType.values())
		{
			for (int i = 0; i < potionType.getPotionItemIds().length; i++)
			{
				int itemId = potionType.getPotionItemIds()[i];
				potionDefinitionMap.put(itemId, new PotionDefinition(potionType, i + 1, itemId));
			}
		}
	}

	private int getNotedItemId(int itemId)
	{
		ItemComposition composition = client.getItemDefinition(itemId);
		if (composition.getNote() != -1)
		{
			return itemId;
		}
		return composition.getLinkedNoteId();
	}

	private int getUnnotedItemId(int itemId)
	{
		ItemComposition composition = client.getItemDefinition(itemId);
		if (composition.getNote() == -1)
		{
			return itemId;
		}
		return composition.getLinkedNoteId();
	}

	private int getPrice(int itemId)
	{
		// Real price lookup should go here, but I'm just putting some dummy
		// values here as I believe Jagex has a more convenient way to lookup
		// prices already than Runelite does.

		switch (itemId)
		{
			case ItemID.VIAL:
				return 2;
			case ItemID.EMPTY_CUP:
				return 35;
		}

		return -1;
	}

	private void addToItemMap(Map<Integer, Long> itemMap, int itemId, long quantity)
	{
		long currAmount = itemMap.getOrDefault(itemId, 0L);
		itemMap.put(itemId, currAmount + quantity);
	}

	private String getItemName(int itemId)
	{
		ItemComposition composition = client.getItemDefinition(itemId);
		return composition.getName();
	}

	private InventoryItem[] getInventoryItems()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Item[] items = inventory.getItems();

		// Copy items to another data structure to make them modifiable
		InventoryItem[] invItems = new InventoryItem[items.length];
		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];
			if (item != null && item.getId() != -1)
			{
				invItems[i] = new InventoryItem(item.getId(), item.getQuantity());
			}
		}

		return invItems;
	}

	private InventoryActionResult attemptToPerformInventoryActions(
		Map<Integer, Long> itemsToTake, Map<Integer, Long> itemsToGive)
	{
		InventoryItem[] items = getInventoryItems();

		// Take items
		for (Map.Entry<Integer, Long> entry : itemsToTake.entrySet())
		{
			int notedItemId = getNotedItemId(entry.getKey());
			int unnotedItemId = getUnnotedItemId(entry.getKey());
			long amount = entry.getValue();

			// Try unnoted items first (to try to make inventory space)
			for (int i = 0; i < items.length && amount > 0; i++)
			{
				InventoryItem item = items[i];
				if (item != null && item.getItemId() == unnotedItemId)
				{
					int take = (int)Math.min(amount, item.getQuantity());
					amount -= take;
					int newQuantity = item.getQuantity() - take;
					if (newQuantity == 0)
					{
						items[i] = null;
					}
					else
					{
						item.setQuantity(newQuantity);
					}
				}
			}

			// Then try noted items
			for (int i = 0; i < items.length && amount > 0; i++)
			{
				InventoryItem item = items[i];
				if (item != null && item.getItemId() == notedItemId)
				{
					int take = (int)Math.min(amount, item.getQuantity());
					amount -= take;
					int newQuantity = item.getQuantity() - take;
					if (newQuantity == 0)
					{
						items[i] = null;
					}
					else
					{
						item.setQuantity(newQuantity);
					}
				}
			}

			if (amount > 0)
			{
				// Items to take were not found in the inventory
				return InventoryActionResult.MISSING_ITEMS;
			}
		}

		// Give items
		for (Map.Entry<Integer, Long> entry : itemsToGive.entrySet())
		{
			// Note that I'm only handling noted items here,
			// but that's all that's needed for our use case.
			// More code would be needed to also handle giving unnoted items.

			// Check if the players inventory already contains the noted items
			boolean foundItem = false;
			for (InventoryItem item : items)
			{
				if (item != null && item.getItemId() == entry.getKey())
				{
					long newQuantiy = item.getQuantity() + entry.getValue();
					if (newQuantiy > Integer.MAX_VALUE)
					{
						// Overflow would occur, so it's not possible to give
						// the items to the player.
						return InventoryActionResult.OVERFLOW;
					}

					foundItem = true;
					item.setQuantity((int)newQuantiy);
					break;
				}
			}

			// If the noted items weren't in the inventory already,
			// use up a space for them.
			if (!foundItem)
			{
				boolean foundSpace = false;
				for (int i = 0; i < items.length; i++)
				{
					InventoryItem item = items[i];
					if (item == null)
					{
						long quantity = entry.getValue();
						if (quantity > Integer.MAX_VALUE)
						{
							// More than a maximum stack is trying to be added,
							// which is too much.
							return InventoryActionResult.OVERFLOW;
						}

						items[i] = new InventoryItem(entry.getKey(), (int)quantity);
						foundSpace = true;
						break;
					}
				}

				if (!foundSpace)
				{
					// The player doesn't have enough inventory space.
					return InventoryActionResult.FULL_INVENTORY;
				}
			}
		}

		// If we got here, the inventory modifications would be successful

		// Print the changes since we can't actually modify the items in Runelite
		for (Map.Entry<Integer, Long> entry : itemsToTake.entrySet())
		{
			int itemId = entry.getKey();
			int quantity = Math.toIntExact(entry.getValue());
			print("Take " + quantity + " x " + getItemName(itemId));
		}
		for (Map.Entry<Integer, Long> entry : itemsToGive.entrySet())
		{
			int itemId = entry.getKey();
			int quantity = Math.toIntExact(entry.getValue());
			print("Give " + quantity + " x " + getItemName(itemId));
		}

		return InventoryActionResult.SUCCESS;
	}

	private void decantPots(int targetDoses)
	{
		if (targetDoses <= 0)
		{
			// Invalid target amount
			return;
		}

		print("----- Decanting potions into " + targetDoses + "-doses -----");

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Item[] items = inventory.getItems();

		Map<PotionType, Long> potionTypeDoses = new HashMap<>();
		Map<Integer, Long> baseContainers = new HashMap<>();
		Map<Integer, Long> extraContaienrs = new HashMap<>();
		Map<Integer, Long> itemsToTake = new HashMap<>();
		Map<Integer, Long> itemsToGive = new HashMap<>();

		for (Item item : items)
		{
			int itemId = getUnnotedItemId(item.getId());
			PotionDefinition def = potionDefinitionMap.get(itemId);
			if (def != null)
			{
				long currDoses = potionTypeDoses.getOrDefault(def.getType(), 0L);
				potionTypeDoses.put(def.getType(), currDoses + item.getQuantity() * def.getDoses());

				long currContainerAmount = baseContainers.getOrDefault(def.getType().getContainerItemId(), 0L);
				baseContainers.put(def.getType().getContainerItemId(), currContainerAmount + item.getQuantity());

				addToItemMap(itemsToTake, item.getId(), item.getQuantity());
			}
		}

		// Player may have extra potion containers in his inventory,
		// so we need to take those into account
		for (Item item : items)
		{
			int itemId = getUnnotedItemId(item.getId());

			// Check if the potion container is relevant at all
			Long amount = baseContainers.get(itemId);
			if (amount != null)
			{
				long currAmount = extraContaienrs.getOrDefault(itemId, 0L);
				extraContaienrs.put(itemId, currAmount + item.getQuantity());
			}
		}

		// The big calculation
		for (Map.Entry<PotionType, Long> entry : potionTypeDoses.entrySet())
		{
			// Give decanted potions to player
			int dosesPerPot = Math.min(targetDoses, entry.getKey().getPotionItemIds().length);
			long doses = entry.getValue();
			long pots = doses / dosesPerPot;
			addToItemMap(itemsToGive, getNotedItemId(entry.getKey().getPotionItemIds()[dosesPerPot - 1]), pots);

			// Check if an extra pot needs to be given to make use of the last doses
			int leftoverDoses = (int)(doses % dosesPerPot);
			if (leftoverDoses > 0)
			{
				addToItemMap(itemsToGive, getNotedItemId(entry.getKey().getPotionItemIds()[leftoverDoses - 1]), 1);
			}

			// Make use of containers
			int containerItemId = entry.getKey().getContainerItemId();
			long base = baseContainers.getOrDefault(containerItemId, 0L);
			long extra = extraContaienrs.getOrDefault(containerItemId, 0L);
			long required = pots + (leftoverDoses > 0 ? 1 : 0);

			// Take from base first
			if (base > 0 && required > 0)
			{
				long take = Math.min(base, required);
				required -= take;

				baseContainers.put(containerItemId, base - take);
			}

			// If more are needed, use the players extra containers in inventory
			if (extra > 0 && required > 0)
			{
				long take = Math.min(extra, required);
				required -= take;
				addToItemMap(itemsToTake, containerItemId, take);

				extraContaienrs.put(containerItemId, extra - take);
			}

			// If more are needed, buy them
			if (required > 0)
			{
				int price = getPrice(containerItemId);
				if (price == -1)
				{
					price = 5; // Jagex default value for containers
				}
				else
				{
					price += 2; // Because herb guy needs to make money somehow
				}

				addToItemMap(itemsToTake, ItemID.COINS_995, required * price);
			}
		}

		// Give extra containers that were collected from the potions
		for (Map.Entry<Integer, Long> entry : baseContainers.entrySet())
		{
			if (entry.getValue() > 0)
			{
				addToItemMap(itemsToGive, getNotedItemId(entry.getKey()), entry.getValue());
			}
		}

		// Attempt to do everything. I'm assuming Jagex already has a method
		// which tries to take and give items to a players inventory, so
		// I'm putting that in another method.
		InventoryActionResult result = attemptToPerformInventoryActions(itemsToTake, itemsToGive);
		switch (result)
		{
			case SUCCESS:
				print("Potion decant completed successfully");
				break;
			case FULL_INVENTORY:
				print("Your inventory is too full to hold all items");
				break;
			case MISSING_ITEMS:
				print("Your inventory is missing containers or coins");
				break;
			case OVERFLOW:
				print("Cannot decant potions because of an integer overflow");
				break;
		}
	}
}
