package net.vulpinegames.wallet.shop;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * ShopInventory over a vanilla Container - player inventories, and chests on
 * Fabric where there is no item-handler capability.
 */
public final class ContainerInventory implements ShopInventory {
	private final Container container;

	public ContainerInventory(Container container) {
		this.container = container;
	}

	private static boolean canStack(ItemStack a, ItemStack b) {
		//? if >1.20.1 {
		return ItemStack.isSameItemSameComponents(a, b);
		//?}
		//? if <=1.20.1 {
		/*return ItemStack.isSameItemSameTags(a, b);
		*///?}
	}

	@Override
	public int slots() {
		return container.getContainerSize();
	}

	@Override
	public ItemStack get(int slot) {
		return container.getItem(slot);
	}

	@Override
	public ItemStack extract(int slot, int amount) {
		ItemStack removed = container.removeItem(slot, amount);
		container.setChanged();
		return removed;
	}

	@Override
	public ItemStack insert(ItemStack stack) {
		ItemStack remaining = stack.copy();
		// merge into matching stacks first, then empty slots - hopper semantics
		for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
			ItemStack existing = container.getItem(i);
			if (!existing.isEmpty() && canStack(existing, remaining)) {
				int space = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
				if (space > 0) {
					int moved = Math.min(space, remaining.getCount());
					existing.grow(moved);
					remaining.shrink(moved);
				}
			}
		}
		for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, remaining);
				remaining = ItemStack.EMPTY;
			}
		}
		container.setChanged();
		return remaining;
	}
}
