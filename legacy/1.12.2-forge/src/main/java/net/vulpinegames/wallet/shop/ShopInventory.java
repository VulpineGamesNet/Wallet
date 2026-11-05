package net.vulpinegames.wallet.shop;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * The slice of a container the shop engine needs. Backed by the Forge
 * IItemHandler capability where the block offers one (a vanilla chest's
 * capability spans both halves of a double chest), else by IInventory.
 */
public interface ShopInventory {
	int slots();

	ItemStack get(int slot);

	/** Remove up to amount from the slot, returning what was removed. */
	ItemStack extract(int slot, int amount);

	/** Insert anywhere, returning what did not fit. */
	ItemStack insert(ItemStack stack);

	/** The container at a block position, or null when the block holds none. */
	static ShopInventory at(World world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		if (te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
			IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
			if (handler != null) return new Handler(handler);
		}
		IInventory inv = TileEntityHopper.getInventoryAtPosition(world, pos.getX(), pos.getY(), pos.getZ());
		return inv != null ? new Container(inv) : null;
	}

	final class Handler implements ShopInventory {
		private final IItemHandler handler;

		public Handler(IItemHandler handler) {
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

	/** Over a vanilla IInventory - player inventories, and containers without the capability. */
	final class Container implements ShopInventory {
		private final IInventory inv;
		private final int size;

		public Container(IInventory inv) {
			this(inv, inv.getSizeInventory());
		}

		/** Only the first `size` slots - e.g. a player's 36 main slots, without armour and offhand. */
		public Container(IInventory inv, int size) {
			this.inv = inv;
			this.size = Math.min(size, inv.getSizeInventory());
		}

		private static boolean canStack(ItemStack a, ItemStack b) {
			return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
		}

		@Override
		public int slots() {
			return size;
		}

		@Override
		public ItemStack get(int slot) {
			return inv.getStackInSlot(slot);
		}

		@Override
		public ItemStack extract(int slot, int amount) {
			ItemStack removed = inv.decrStackSize(slot, amount);
			inv.markDirty();
			return removed;
		}

		@Override
		public ItemStack insert(ItemStack stack) {
			ItemStack remaining = stack.copy();
			// merge into matching stacks first, then empty slots - hopper semantics
			for (int i = 0; i < size && !remaining.isEmpty(); i++) {
				ItemStack existing = inv.getStackInSlot(i);
				if (!existing.isEmpty() && canStack(existing, remaining) && inv.isItemValidForSlot(i, remaining)) {
					int space = Math.min(existing.getMaxStackSize(), inv.getInventoryStackLimit()) - existing.getCount();
					if (space > 0) {
						int moved = Math.min(space, remaining.getCount());
						existing.grow(moved);
						remaining.shrink(moved);
					}
				}
			}
			for (int i = 0; i < size && !remaining.isEmpty(); i++) {
				if (inv.getStackInSlot(i).isEmpty() && inv.isItemValidForSlot(i, remaining)) {
					inv.setInventorySlotContents(i, remaining);
					remaining = ItemStack.EMPTY;
				}
			}
			inv.markDirty();
			return remaining;
		}
	}
}
