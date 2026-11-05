package net.vulpinegames.wallet.coin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextFormatting;

import java.util.HashMap;
import java.util.Map;

/**
 * Physical coins: vanilla gold nuggets identified by an int NBT tag.
 *
 * 1.12.2 has no CustomModelData component and no item-model predicate for
 * NBT, so a coin cannot get its own texture without a client mod. The
 * equivalent chosen here keeps the same contract with a different carrier:
 * {@code minecraft:gold_nugget} with {@code tag.CustomModelData = <id>} (the
 * same ids as every other server), plus a display name and lore so players can
 * tell coins apart. Only that int tag identifies a coin; a nugget renamed to
 * "Coin" in an anvil is still a nugget. Vanilla stacking compares the whole
 * tag, so coins of one denomination stack and never merge with plain nuggets.
 */
public final class Coins {
	public static final String TAG = "CustomModelData";

	private Coins() {}

	public static ItemStack create(CoinDenomination denom, int count) {
		ItemStack stack = new ItemStack(Items.GOLD_NUGGET, count);
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInteger(TAG, denom.customModelData);
		NBTTagCompound display = new NBTTagCompound();
		// RESET first: 1.12 renders custom names and lore italic by prepending §o.
		display.setString("Name", TextFormatting.RESET.toString() + denom.color + denom.name);
		NBTTagList lore = new NBTTagList();
		lore.appendTag(new NBTTagString(TextFormatting.RESET.toString() + TextFormatting.GRAY + denom.lore));
		display.setTag("Lore", lore);
		tag.setTag("display", display);
		stack.setTagCompound(tag);
		return stack;
	}

	public static int customModelDataOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		NBTTagCompound tag = stack.getTagCompound();
		return tag != null && tag.hasKey(TAG, 99) ? tag.getInteger(TAG) : 0;
	}

	/** Denomination of a stack, null when it is not a coin. */
	public static CoinDenomination denomOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (stack.getItem() != Items.GOLD_NUGGET) return null;
		int cmd = customModelDataOf(stack);
		return cmd == 0 ? null : CoinDenomination.byCustomModelData(cmd);
	}

	public static int valueOf(ItemStack stack) {
		CoinDenomination denom = denomOf(stack);
		return denom != null ? denom.value : 0;
	}

	public static final class CoinCount {
		public final int total;
		public final Map<Integer, Integer> breakdown;

		CoinCount(int total, Map<Integer, Integer> breakdown) {
			this.total = total;
			this.breakdown = breakdown;
		}
	}

	public static CoinCount count(EntityPlayer player) {
		InventoryPlayer inv = player.inventory;
		long total = 0;
		Map<Integer, Integer> breakdown = new HashMap<>();
		for (CoinDenomination d : CoinDenomination.ALL) breakdown.put(d.customModelData, 0);
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			CoinDenomination denom = denomOf(stack);
			if (denom != null) {
				total += (long) denom.value * stack.getCount();
				breakdown.merge(denom.customModelData, stack.getCount(), Integer::sum);
			}
		}
		return new CoinCount((int) Math.min(total, Integer.MAX_VALUE), breakdown);
	}

	/** Give coins using the greedy algorithm (fewest coins possible). */
	public static void giveEfficient(EntityPlayer player, int amount) {
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining >= denom.value) {
				int count = remaining / denom.value;
				remaining = remaining % denom.value;
				while (count > 0) {
					int stackSize = Math.min(count, 64);
					give(player, create(denom, stackSize));
					count -= stackSize;
				}
			}
		}
	}

	/** Give coins of one specific denomination; false when amount does not divide. */
	public static boolean giveSpecific(EntityPlayer player, int amount, int denomination) {
		CoinDenomination denom = CoinDenomination.byValue(denomination);
		if (denom == null || amount % denomination != 0) return false;
		int count = amount / denomination;
		while (count > 0) {
			int stackSize = Math.min(count, 64);
			give(player, create(denom, stackSize));
			count -= stackSize;
		}
		return true;
	}

	/** Into the inventory; whatever does not fit drops at the player's feet. */
	public static void give(EntityPlayer player, ItemStack stack) {
		player.inventory.addItemStackToInventory(stack);
		if (!stack.isEmpty()) player.dropItem(stack, false);
	}

	/** Whether greedy largest-first coin removal can pay this amount exactly. */
	public static boolean canPayExact(EntityPlayer player, int amount) {
		CoinCount coins = count(player);
		if (coins.total < amount) return false;
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining <= 0) break;
			int available = coins.breakdown.getOrDefault(denom.customModelData, 0);
			int use = Math.min(available, remaining / denom.value);
			remaining -= use * denom.value;
		}
		return remaining == 0;
	}

	/** Remove coins worth exactly this amount (greedy, largest first). */
	public static boolean remove(EntityPlayer player, int amount) {
		if (!canPayExact(player, amount)) return false;
		InventoryPlayer inv = player.inventory;
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining <= 0) break;
			for (int i = 0; i < inv.getSizeInventory() && remaining > 0; i++) {
				ItemStack stack = inv.getStackInSlot(i);
				CoinDenomination stackDenom = denomOf(stack);
				if (stackDenom != null && stackDenom.customModelData == denom.customModelData) {
					int maxCanUse = remaining / denom.value;
					int toRemove = Math.min(stack.getCount(), maxCanUse);
					if (toRemove > 0) {
						stack.shrink(toRemove);
						inv.setInventorySlotContents(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
						remaining -= toRemove * denom.value;
					}
				}
			}
		}
		inv.markDirty();
		return remaining == 0;
	}
}
