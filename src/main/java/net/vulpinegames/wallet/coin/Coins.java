package net.vulpinegames.wallet.coin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//? if >1.20.1 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
//?}
//? if <=1.20.1 {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
*///?}

/** Physical coins: vanilla gold nuggets identified purely by CustomModelData. */
public final class Coins {
	private Coins() {}

	public static ItemStack create(CoinDenomination denom, int count) {
		ItemStack stack = new ItemStack(Items.GOLD_NUGGET, count);
		Component name = Component.literal(denom.name())
			.withStyle(style -> style.withColor(ChatFormatting.getByName(denom.color())).withItalic(false));
		Component loreLine = Component.literal(denom.lore())
			.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
		//? if >1.20.1 {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(denom.customModelData()));
		stack.set(DataComponents.CUSTOM_NAME, name);
		stack.set(DataComponents.LORE, new ItemLore(List.of(loreLine)));
		//?}
		//? if <=1.20.1 {
		/*stack.getOrCreateTag().putInt("CustomModelData", denom.customModelData());
		CompoundTag display = stack.getOrCreateTagElement("display");
		display.putString("Name", Component.Serializer.toJson(name));
		ListTag lore = new ListTag();
		lore.add(StringTag.valueOf(Component.Serializer.toJson(loreLine)));
		display.put("Lore", lore);
		*///?}
		return stack;
	}

	public static int customModelDataOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		//? if >1.20.1 {
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return cmd != null ? cmd.value() : 0;
		//?}
		//? if <=1.20.1 {
		/*CompoundTag tag = stack.getTag();
		return tag != null && tag.contains("CustomModelData") ? tag.getInt("CustomModelData") : 0;
		*///?}
	}

	/** Denomination of a stack, null when it is not a coin. */
	public static CoinDenomination denomOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (!stack.is(Items.GOLD_NUGGET)) return null;
		int cmd = customModelDataOf(stack);
		return cmd == 0 ? null : CoinDenomination.byCustomModelData(cmd);
	}

	public static int valueOf(ItemStack stack) {
		CoinDenomination denom = denomOf(stack);
		return denom != null ? denom.value() : 0;
	}

	public record CoinCount(int total, Map<Integer, Integer> breakdown) {}

	public static CoinCount count(Player player) {
		Inventory inv = player.getInventory();
		int total = 0;
		Map<Integer, Integer> breakdown = new HashMap<>();
		for (CoinDenomination d : CoinDenomination.ALL) breakdown.put(d.customModelData(), 0);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			CoinDenomination denom = denomOf(stack);
			if (denom != null) {
				total += denom.value() * stack.getCount();
				breakdown.merge(denom.customModelData(), stack.getCount(), Integer::sum);
			}
		}
		return new CoinCount(total, breakdown);
	}

	/** Give coins using the greedy algorithm (fewest coins possible). */
	public static void giveEfficient(Player player, int amount) {
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining >= denom.value()) {
				int count = remaining / denom.value();
				remaining = remaining % denom.value();
				while (count > 0) {
					int stackSize = Math.min(count, 64);
					give(player, create(denom, stackSize));
					count -= stackSize;
				}
			}
		}
	}

	/** Give coins of one specific denomination; false when amount does not divide. */
	public static boolean giveSpecific(Player player, int amount, int denomination) {
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

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** Whether greedy largest-first coin removal can pay this amount exactly. */
	public static boolean canPayExact(Player player, int amount) {
		CoinCount coins = count(player);
		if (coins.total() < amount) return false;
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining <= 0) break;
			int available = coins.breakdown().getOrDefault(denom.customModelData(), 0);
			int use = Math.min(available, remaining / denom.value());
			remaining -= use * denom.value();
		}
		return remaining == 0;
	}

	/** Remove coins worth exactly this amount (greedy, largest first). */
	public static boolean remove(Player player, int amount) {
		if (!canPayExact(player, amount)) return false;
		Inventory inv = player.getInventory();
		int remaining = amount;
		for (CoinDenomination denom : CoinDenomination.ALL) {
			if (remaining <= 0) break;
			for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
				ItemStack stack = inv.getItem(i);
				CoinDenomination stackDenom = denomOf(stack);
				if (stackDenom != null && stackDenom.customModelData() == denom.customModelData()) {
					int maxCanUse = remaining / denom.value();
					int toRemove = Math.min(stack.getCount(), maxCanUse);
					if (toRemove > 0) {
						stack.shrink(toRemove);
						inv.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
						remaining -= toRemove * denom.value();
					}
				}
			}
		}
		return remaining == 0;
	}
}
