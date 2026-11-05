package net.vulpinegames.wallet.shop;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.ItemText;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.coin.Coins;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.world.PlayerNames;
import net.vulpinegames.wallet.world.Positions;

import java.util.List;
import java.util.UUID;

/**
 * The sign right-click flow: crouch = shop info / shop creation, plain click =
 * trade. Runs at highest event priority and cancels, so nothing else sees
 * clicks on registered shop signs.
 */
public final class ShopInteractions {
	private ShopInteractions() {}

	/** True when the click was consumed and the event must be cancelled. */
	public static boolean onRightClickWallSign(WalletServer ks, EntityPlayerMP player, World world, BlockPos pos) {
		String signKey = Positions.blockKey(world, pos);
		boolean isKnownShop = ks.shopCache().contains(signKey);
		boolean crouching = player.isSneaking();

		if (!crouching && !isKnownShop) return false;
		if (!ks.database().isAvailable()) {
			if (!isKnownShop) return false;
			player.sendMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}

		ShopDao.Shop existingShop = isKnownShop ? ks.shops().getShop(signKey) : null;

		if (crouching) {
			if (existingShop != null) {
				String actionText = existingShop.isBuy() ? "selling" : "buying";
				player.sendMessage(Msg.empty()
					.appendSibling(Msg.gold("Shop is " + actionText + " "))
					.appendSibling(ItemText.itemListComponent(ShopTemplates.refs(existingShop.itemTemplate)))
					.appendSibling(Msg.gold(" for "))
					.appendSibling(Msg.green(existingShop.price + "$")));
				return true;
			}
			return tryCreateShop(ks, player, world, pos, signKey);
		}

		if (existingShop == null) return false;
		if (SignShop.signAt(world, pos) == null) return false;
		trade(ks, player, world, existingShop);
		return true;
	}

	private static boolean tryCreateShop(WalletServer ks, EntityPlayerMP player, World world, BlockPos pos, String signKey) {
		TileEntitySign sign = SignShop.signAt(world, pos);
		if (sign == null) return false;
		SignShop.Parsed signData = SignShop.parseSignText(sign);
		if (signData == null) return false;

		IBlockState signState = world.getBlockState(pos);
		BlockPos chestPos = SignShop.attachedBlockPos(signState, pos);
		ShopInventory chestInv = chestPos != null ? ShopInventory.at(world, chestPos) : null;
		if (chestInv == null) {
			player.sendMessage(Msg.red("Sign must be placed on a container"));
			return true;
		}

		String itemTemplate = ShopTemplates.serializeInventory(chestInv);
		if ("[]".equals(itemTemplate)) {
			player.sendMessage(Msg.red("Put items in chest first"));
			return true;
		}

		// Refuse a shop on a container that already carries someone else's shop -
		// a parasite sign on the far half of a double chest would sell the
		// victim's stock.
		IBlockState chestState = world.getBlockState(chestPos);
		BlockPos otherHalf = SignShop.otherChestHalf(world, chestState, chestPos);
		for (BlockPos cp : otherHalf != null ? new BlockPos[]{chestPos, otherHalf} : new BlockPos[]{chestPos}) {
			List<String> existing = ks.shops().getShopsByChestPos(Positions.blockKey(world, cp));
			if (existing == null) {
				player.sendMessage(Msg.red("Economy is offline - try again"));
				return true;
			}
			for (String otherKey : existing) {
				ShopDao.Shop other = ks.shops().getShop(otherKey);
				if (other != null && !player.getCachedUniqueIdString().equals(other.owner)) {
					player.sendMessage(Msg.red("That chest already belongs to another player's shop"));
					return true;
				}
			}
		}

		ShopDao.Shop shop = new ShopDao.Shop(
			player.getCachedUniqueIdString(),
			Positions.blockKey(world, chestPos),
			signData.type,
			signData.price,
			itemTemplate,
			false, false);
		if (!ks.shops().saveShop(signKey, shop)) {
			player.sendMessage(Msg.red("Could not create the shop, check the server log"));
			return true;
		}
		ks.shopCache().add(signKey, shop.chestPos);
		SignShop.updateSignForShop(world, pos, sign, signData.type, signData.price);

		String actionText = "BUY".equals(signData.type) ? "selling to users" : "buying from users";
		player.sendMessage(Msg.empty()
			.appendSibling(Msg.gold("Shop created! Shop is " + actionText + " "))
			.appendSibling(ItemText.itemListComponent(ShopTemplates.refs(itemTemplate)))
			.appendSibling(Msg.gold(" for "))
			.appendSibling(Msg.green(signData.price + "$")));
		return true;
	}

	private static int totalCount(List<ItemText.ItemRef> refs) {
		int sum = 0;
		for (ItemText.ItemRef r : refs) sum += r.count;
		return sum;
	}

	private static int totalStacks(List<ItemStack> stacks) {
		int sum = 0;
		for (ItemStack s : stacks) sum += s.getCount();
		return sum;
	}

	private static void trade(WalletServer ks, EntityPlayerMP player, World world, ShopDao.Shop shop) {
		MinecraftServer server = player.getServer();
		boolean strict = ks.config().strictItemMatch;

		String[] posParts = shop.chestPos.split("_");
		BlockPos chestPos;
		try {
			chestPos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
		} catch (Exception e) {
			player.sendMessage(Msg.red("Shop chest not found"));
			return;
		}

		ShopInventory chestInv = ShopInventory.at(world, chestPos);
		if (chestInv == null) {
			player.sendMessage(Msg.red("Shop chest not found"));
			return;
		}

		String buyerUuid = player.getCachedUniqueIdString();
		// A server shop pays into and out of the server wallet; the row keeps its
		// creator so the shop list and break protection still know who built it.
		String ownerUuid = shop.isServer ? WalletDao.SERVER_UUID : shop.owner;
		int price = shop.price;
		String itemTemplate = shop.itemTemplate;
		EntityPlayerMP ownerPlayer = onlinePlayer(server, ownerUuid);
		String buyerName = player.getName();
		List<ItemText.ItemRef> template = ShopTemplates.refs(itemTemplate);

		if (shop.isBuy()) {
			if (ks.wallets().getBalance(buyerUuid) < price) {
				player.sendMessage(Msg.empty()
					.appendSibling(Msg.red("You need "))
					.appendSibling(Msg.yellow("$" + price))
					.appendSibling(Msg.red(" but only have "))
					.appendSibling(Msg.yellow("$" + ks.wallets().getBalance(buyerUuid))));
				return;
			}

			List<ShopTemplates.Missing> missing = ShopTemplates.getMissingItems(chestInv, itemTemplate, strict);
			if (!missing.isEmpty()) {
				player.sendMessage(Msg.red("Shop is out of stock"));
				if (ownerPlayer != null) {
					ownerPlayer.sendMessage(Msg.empty()
						.appendSibling(Msg.yellow(buyerName))
						.appendSibling(Msg.red(" tried to buy from your shop but you're out of: "))
						.appendSibling(ItemText.itemListComponent(ShopTemplates.refs(missing))));
				}
				return;
			}

			// Money settles completely before any item moves.
			if (!ks.wallets().removeBalance(buyerUuid, price)) {
				player.sendMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}
			if (!ks.wallets().addBalance(ownerUuid, price)) {
				ks.wallets().addBalance(buyerUuid, price);
				Wallet.LOGGER.error("[Wallet] BUY credit to {} failed, buyer refunded", ownerUuid);
				player.sendMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			List<ItemStack> bought = ShopTemplates.removeItems(chestInv, itemTemplate, strict, shop.isInfinite);

			// A container can pass the read-only stock check yet refuse
			// extraction. Short delivery: put it back, unwind the money.
			int expected = totalCount(template);
			int delivered = totalStacks(bought);
			if (delivered < expected) {
				for (ItemStack stack : ShopTemplates.addItems(chestInv, bought)) Coins.give(player, stack);
				ks.wallets().removeBalance(ownerUuid, price);
				ks.wallets().addBalance(buyerUuid, price);
				Wallet.LOGGER.warn("[Wallet] Shop container at {} refused to release its stock ({}/{})",
					shop.chestPos, delivered, expected);
				player.sendMessage(Msg.red("The shop container refused to release its stock - nothing was traded"));
				return;
			}
			for (ItemStack stack : bought) Coins.give(player, stack);

			String itemsStr = ItemText.itemListText(template);
			String ownerName = PlayerNames.byUuid(server, ownerUuid);
			ks.history().addEntry(buyerUuid, "shop_buy", price, ownerName, itemsStr);
			ks.history().addEntry(ownerUuid, "shop_sell", price, buyerName, itemsStr);

			player.sendMessage(Msg.empty()
				.appendSibling(Msg.gold("Purchased "))
				.appendSibling(ItemText.itemListComponent(template))
				.appendSibling(Msg.gold(" for "))
				.appendSibling(Msg.green(price + "$")));
			if (ownerPlayer != null) {
				ownerPlayer.sendMessage(Msg.empty()
					.appendSibling(Msg.yellow(buyerName))
					.appendSibling(Msg.gold(" bought "))
					.appendSibling(ItemText.itemListComponent(template))
					.appendSibling(Msg.gold(" from your shop for "))
					.appendSibling(Msg.green(price + "$")));
			}
		} else {
			// Main inventory only (36 slots): worn armour and the offhand item are
			// never sold out from under the player.
			ShopInventory playerInv = new ShopInventory.Container(player.inventory, player.inventory.mainInventory.size());
			List<ShopTemplates.Missing> missingPlayer = ShopTemplates.getMissingItems(playerInv, itemTemplate, strict);
			if (!missingPlayer.isEmpty()) {
				player.sendMessage(Msg.empty()
					.appendSibling(Msg.red("You're missing: "))
					.appendSibling(ItemText.itemListComponent(ShopTemplates.refs(missingPlayer))));
				return;
			}

			if (ks.wallets().getBalance(ownerUuid) < price) {
				player.sendMessage(Msg.red("Shop owner cannot afford this purchase"));
				if (ownerPlayer != null) {
					ownerPlayer.sendMessage(Msg.empty()
						.appendSibling(Msg.yellow(buyerName))
						.appendSibling(Msg.red(" tried to sell "))
						.appendSibling(ItemText.itemListComponent(template))
						.appendSibling(Msg.red(" but you can't afford "))
						.appendSibling(Msg.yellow(price + "$")));
				}
				return;
			}

			if (!shop.isInfinite && !ShopTemplates.chestHasSpace(chestInv, itemTemplate)) {
				player.sendMessage(Msg.red("Shop chest is full"));
				if (ownerPlayer != null) {
					ownerPlayer.sendMessage(Msg.empty()
						.appendSibling(Msg.yellow(buyerName))
						.appendSibling(Msg.red(" tried to sell "))
						.appendSibling(ItemText.itemListComponent(template))
						.appendSibling(Msg.red(" but your shop chest is full")));
				}
				return;
			}

			if (!ks.wallets().removeBalance(ownerUuid, price)) {
				player.sendMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			List<ItemStack> sold = ShopTemplates.removeItems(playerInv, itemTemplate, strict, false);
			if (!shop.isInfinite) {
				List<ItemStack> handBack = ShopTemplates.addAllOrNothing(chestInv, sold);
				if (!handBack.isEmpty()) {
					for (ItemStack stack : handBack) Coins.give(player, stack);
					ks.wallets().addBalance(ownerUuid, price);
					Wallet.LOGGER.warn("[Wallet] Shop chest at {} refused items on SELL, trade unwound", shop.chestPos);
					player.sendMessage(Msg.red("The shop chest refused your items - nothing was traded"));
					return;
				}
			}
			if (!ks.wallets().addBalance(buyerUuid, price)) {
				ks.wallets().addBalance(ownerUuid, price);
				Wallet.LOGGER.error("[Wallet] SELL credit to {} failed, owner refunded", buyerUuid);
				player.sendMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			String itemsStr = ItemText.itemListText(template);
			String ownerName = PlayerNames.byUuid(server, ownerUuid);
			ks.history().addEntry(buyerUuid, "shop_sell", price, ownerName, itemsStr);
			ks.history().addEntry(ownerUuid, "shop_buy", price, buyerName, itemsStr);

			player.sendMessage(Msg.empty()
				.appendSibling(Msg.gold("Sold "))
				.appendSibling(ItemText.itemListComponent(template))
				.appendSibling(Msg.gold(" for "))
				.appendSibling(Msg.green(price + "$")));
			if (ownerPlayer != null) {
				ownerPlayer.sendMessage(Msg.empty()
					.appendSibling(Msg.yellow(buyerName))
					.appendSibling(Msg.gold(" sold "))
					.appendSibling(ItemText.itemListComponent(template))
					.appendSibling(Msg.gold(" to your shop for "))
					.appendSibling(Msg.green(price + "$")));
			}
		}
	}

	private static EntityPlayerMP onlinePlayer(MinecraftServer server, String uuid) {
		try {
			return server.getPlayerList().getPlayerByUUID(UUID.fromString(uuid));
		} catch (Exception e) {
			return null;
		}
	}
}
