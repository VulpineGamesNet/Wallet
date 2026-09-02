package net.vulpinegames.wallet.shop;

import net.minecraft.world.item.ItemStack;

/**
 * The slice of a container the shop engine needs. Forge/NeoForge back it with
 * the IItemHandler capability - the same thing KubeJS wrapped, so double-chest
 * and modded-container behavior match the script and the templates already in
 * the DB. Fabric backs it with the vanilla Container.
 */
public interface ShopInventory {
	int slots();

	ItemStack get(int slot);

	/** Remove up to amount from the slot, returning what was removed. */
	ItemStack extract(int slot, int amount);

	/** Insert anywhere, returning what did not fit. */
	ItemStack insert(ItemStack stack);
}
