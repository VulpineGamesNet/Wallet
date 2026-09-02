package net.vulpinegames.wallet.platform.neoforge;

//? neoforge {

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.vulpinegames.wallet.shop.ShopInventory;

public final class NeoforgeItemHandlerInventory implements ShopInventory {
	private final IItemHandler handler;

	public NeoforgeItemHandlerInventory(IItemHandler handler) {
		this.handler = handler;
	}

	@Override
	public int slots() {
		return handler.getSlots();
	}

	@Override
	public ItemStack get(int slot) {
		return handler.getStackInSlot(slot);
	}

	@Override
	public ItemStack extract(int slot, int amount) {
		return handler.extractItem(slot, amount, false);
	}

	@Override
	public ItemStack insert(ItemStack stack) {
		return ItemHandlerHelper.insertItem(handler, stack, false);
	}
}
//?}
