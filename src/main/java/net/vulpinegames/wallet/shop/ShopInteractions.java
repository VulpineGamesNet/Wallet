package net.vulpinegames.wallet.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.ItemText;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.world.PlayerNames;
import net.vulpinegames.wallet.world.Positions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The sign right-click flow: crouch = shop info / shop creation, plain click =
 * trade. Ported from the script's BlockEvents.rightClicked handler.
 * Runs at highest event priority and cancels with SUCCESS, so claim mods never
 * see clicks on registered shop signs (see the plan's "Shop clicks in claimed
 * chunks") - no protection bypass needed.
 */
public final class ShopInteractions {
	private static final Gson GSON = new Gson();

	private ShopInteractions() {}

	private static List<ItemText.ItemRef> templateRefs(String itemTemplateJson) {
		List<ItemText.ItemRef> refs = new ArrayList<>();
		try {
			JsonArray arr = GSON.fromJson(itemTemplateJson, JsonArray.class);
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				refs.add(new ItemText.ItemRef(o.get("id").getAsString(), o.get("count").getAsInt()));
			}
		} catch (Exception ignored) {
		}
		return refs;
	}

	private static List<ItemText.ItemRef> missingRefs(List<ShopTemplates.Missing> missing) {
		List<ItemText.ItemRef> refs = new ArrayList<>();
		for (ShopTemplates.Missing m : missing) refs.add(new ItemText.ItemRef(m.id(), m.count()));
		return refs;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) player.drop(stack, false);
	}

	/** True when the click was consumed and the event must cancel with SUCCESS. */
	public static boolean onRightClickWallSign(WalletServer ks, ServerPlayer player, ServerLevel level, BlockPos pos) {
		String signKey = Positions.blockKey(level, pos);
		boolean isKnownShop = ks.shopCache().contains(signKey);
		boolean crouching = player.isCrouching();

		// Fast path: an ordinary click on a sign that is no shop is none of our
		// business - claim mods keep their say.
		if (!crouching && !isKnownShop) return false;
		if (!ks.database().isAvailable()) return false;

		ShopDao.Shop existingShop = isKnownShop ? ks.shops().getShop(signKey) : null;

		if (crouching) {
			if (existingShop != null) {
				String actionText = existingShop.isBuy() ? "selling" : "buying";
				player.sendSystemMessage(Msg.empty()
					.append(Msg.gold("Shop is " + actionText + " "))
					.append(ItemText.itemListComponent(templateRefs(existingShop.itemTemplate())))
					.append(Msg.gold(" for "))
					.append(Msg.green(existingShop.price() + "$")));
				return true;
			}
			return tryCreateShop(ks, player, level, pos, signKey);
		}

		// SHOP USAGE
		if (existingShop == null) return false;
		SignShop.Sign sign = SignShop.Sign.at(level, pos);
		if (sign == null || !sign.isWaxed()) return false;

		trade(ks, player, level, existingShop);
		return true;
	}

	private static boolean tryCreateShop(WalletServer ks, ServerPlayer player, ServerLevel level, BlockPos pos, String signKey) {
		SignShop.Sign sign = SignShop.Sign.at(level, pos);
		if (sign == null) return false;
		SignShop.Parsed signData = SignShop.parseSignText(sign);
		if (signData == null) return false;

		BlockState signState = level.getBlockState(pos);
		BlockPos chestPos = SignShop.attachedBlockPos(signState, pos);
		ShopInventory chestInv = chestPos != null ? Wallet.xplat().inventoryAt(level, chestPos) : null;
		if (chestInv == null) {
			player.sendSystemMessage(Msg.red("Sign must be placed on a container"));
			return true;
		}

		String itemTemplate = ShopTemplates.serializeInventory(level, chestInv);
		if ("[]".equals(itemTemplate)) {
			player.sendSystemMessage(Msg.red("Put items in chest first"));
			return true;
		}

		// Refuse building a shop on a container that already carries someone
		// else's shop - otherwise a parasite sign on the far half of a double
		// chest would serialize (and sell) the victim's stock.
		BlockState chestState = level.getBlockState(chestPos);
		BlockPos otherHalf = SignShop.otherChestHalf(chestState, chestPos);
		for (BlockPos cp : otherHalf != null ? new BlockPos[]{chestPos, otherHalf} : new BlockPos[]{chestPos}) {
			List<String> existing = ks.shops().getShopsByChestPos(Positions.blockKey(level, cp));
			if (existing == null) {
				player.sendSystemMessage(Msg.red("Economy is offline - try again"));
				return true;
			}
			for (String otherKey : existing) {
				ShopDao.Shop other = ks.shops().getShop(otherKey);
				if (other != null && !player.getStringUUID().equals(other.owner())) {
					player.sendSystemMessage(Msg.red("That chest already belongs to another player's shop"));
					return true;
				}
			}
		}

		ShopDao.Shop shop = new ShopDao.Shop(
			player.getStringUUID(),
			Positions.blockKey(level, chestPos),
			signData.type(),
			signData.price(),
			itemTemplate,
			false, false);
		if (!ks.shops().saveShop(signKey, shop)) {
			player.sendSystemMessage(Msg.red("Could not create the shop, check the server log"));
			return true;
		}
		ks.shopCache().add(signKey, shop.chestPos());
		SignShop.updateSignForShop(level, pos, sign, signData.type(), signData.price());

		String actionText = "BUY".equals(signData.type()) ? "selling to users" : "buying from users";
		player.sendSystemMessage(Msg.empty()
			.append(Msg.gold("Shop created! Shop is " + actionText + " "))
			.append(ItemText.itemListComponent(templateRefs(itemTemplate)))
			.append(Msg.gold(" for "))
			.append(Msg.green(signData.price() + "$")));
		return true;
	}

	private static void trade(WalletServer ks, ServerPlayer player, ServerLevel level, ShopDao.Shop shop) {
		MinecraftServer server = level.getServer();
		boolean strict = ks.config().strictItemMatch;

		String[] posParts = shop.chestPos().split("_");
		BlockPos chestPos;
		try {
			chestPos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
		} catch (Exception e) {
			player.sendSystemMessage(Msg.red("Shop chest not found"));
			return;
		}

		ShopInventory chestInv = Wallet.xplat().inventoryAt(level, chestPos);
		if (chestInv == null) {
			player.sendSystemMessage(Msg.red("Shop chest not found"));
			return;
		}

		String buyerUuid = player.getStringUUID();
		// A server shop pays into and out of the server wallet; the row keeps its
		// creator so the shop list and break protection still know who built it.
		String ownerUuid = shop.isServer() ? WalletDao.SERVER_UUID : shop.owner();
		int price = shop.price();
		String itemTemplate = shop.itemTemplate();
		ServerPlayer ownerPlayer = onlinePlayer(server, ownerUuid);
		String buyerName = player.getName().getString();

		if (shop.isBuy()) {
			if (ks.wallets().getBalance(buyerUuid) < price) {
				player.sendSystemMessage(Msg.empty()
					.append(Msg.red("You need "))
					.append(Msg.yellow("$" + price))
					.append(Msg.red(" but only have "))
					.append(Msg.yellow("$" + ks.wallets().getBalance(buyerUuid))));
				return;
			}

			List<ShopTemplates.Missing> missing = ShopTemplates.getMissingItems(level, chestInv, itemTemplate, strict);
			if (!missing.isEmpty()) {
				player.sendSystemMessage(Msg.red("Shop is out of stock"));
				if (ownerPlayer != null) {
					ownerPlayer.sendSystemMessage(Msg.empty()
						.append(Msg.yellow(buyerName))
						.append(Msg.red(" tried to buy from your shop but you're out of: "))
						.append(ItemText.itemListComponent(missingRefs(missing))));
				}
				return;
			}

			// Money settles completely before any item moves - a failed ledger
			// write must never leave goods and gold both moved (or both minted).
			if (!ks.wallets().removeBalance(buyerUuid, price)) {
				player.sendSystemMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}
			if (!ks.wallets().addBalance(ownerUuid, price)) {
				ks.wallets().addBalance(buyerUuid, price);
				Wallet.LOGGER.error("[Wallet] BUY credit to {} failed, buyer refunded", ownerUuid);
				player.sendSystemMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			// Infinite: hand out copies, the chest keeps its stock.
			List<ItemStack> bought = ShopTemplates.removeItems(level, chestInv, itemTemplate, strict, shop.isInfinite());

			// A container can pass the read-only stock check yet refuse extraction
			// (machine input slots). Short delivery: put it back, unwind the money.
			List<ItemText.ItemRef> template = templateRefs(itemTemplate);
			int expected = template.stream().mapToInt(ItemText.ItemRef::count).sum();
			int delivered = bought.stream().mapToInt(ItemStack::getCount).sum();
			if (delivered < expected) {
				// Put back whatever WAS extracted, then unwind the money. Items
				// that will not go back into the chest go to the buyer instead -
				// they came out of that chest, so this neither dupes nor loses.
				for (ItemStack stack : ShopTemplates.addItems(chestInv, bought)) give(player, stack);
				ks.wallets().removeBalance(ownerUuid, price);
				ks.wallets().addBalance(buyerUuid, price);
				Wallet.LOGGER.warn("[Wallet] Shop container at {} refused to release its stock ({}/{})",
					shop.chestPos(), delivered, expected);
				player.sendSystemMessage(Msg.red("The shop container refused to release its stock - nothing was traded"));
				return;
			}
			for (ItemStack stack : bought) give(player, stack);

			String itemsStr = ItemText.itemListText(template);
			String ownerName = PlayerNames.byUuid(server, ownerUuid);

			ks.history().addEntry(buyerUuid, "shop_buy", price, ownerName, itemsStr);
			ks.history().addEntry(ownerUuid, "shop_sell", price, buyerName, itemsStr);

			player.sendSystemMessage(Msg.empty()
				.append(Msg.gold("Purchased "))
				.append(ItemText.itemListComponent(template))
				.append(Msg.gold(" for "))
				.append(Msg.green(price + "$")));

			if (ownerPlayer != null) {
				ownerPlayer.sendSystemMessage(Msg.empty()
					.append(Msg.yellow(buyerName))
					.append(Msg.gold(" bought "))
					.append(ItemText.itemListComponent(template))
					.append(Msg.gold(" from your shop for "))
					.append(Msg.green(price + "$")));
			}
		} else {
			// SELL shop
			ShopInventory playerInv = new ContainerInventory(player.getInventory());
			List<ShopTemplates.Missing> missingPlayer = ShopTemplates.getMissingItems(level, playerInv, itemTemplate, strict);
			if (!missingPlayer.isEmpty()) {
				player.sendSystemMessage(Msg.empty()
					.append(Msg.red("You're missing: "))
					.append(ItemText.itemListComponent(missingRefs(missingPlayer))));
				return;
			}

			if (ks.wallets().getBalance(ownerUuid) < price) {
				player.sendSystemMessage(Msg.red("Shop owner cannot afford this purchase"));
				if (ownerPlayer != null) {
					ownerPlayer.sendSystemMessage(Msg.empty()
						.append(Msg.yellow(buyerName))
						.append(Msg.red(" tried to sell "))
						.append(ItemText.itemListComponent(templateRefs(itemTemplate)))
						.append(Msg.red(" but you can't afford "))
						.append(Msg.yellow(price + "$")));
				}
				return;
			}

			if (!shop.isInfinite() && !ShopTemplates.chestHasSpace(chestInv, itemTemplate)) {
				player.sendSystemMessage(Msg.red("Shop chest is full"));
				if (ownerPlayer != null) {
					ownerPlayer.sendSystemMessage(Msg.empty()
						.append(Msg.yellow(buyerName))
						.append(Msg.red(" tried to sell "))
						.append(ItemText.itemListComponent(templateRefs(itemTemplate)))
						.append(Msg.red(" but your shop chest is full")));
				}
				return;
			}

			// Debit the owner first; if items then fail to move, the block above
			// refunds via removeBalance(buyer)/addBalance(owner).
			if (!ks.wallets().removeBalance(ownerUuid, price)) {
				player.sendSystemMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			List<ItemStack> sold = ShopTemplates.removeItems(level, playerInv, itemTemplate, strict, false);
			// Infinite: the chest never fills up because it never receives anything.
			if (!shop.isInfinite()) {
				List<ItemStack> leftover = ShopTemplates.addItems(chestInv, sold);
				// A container can pass the empty-slot check yet refuse insertion
				// (machine inputs, filtered barrels). Refund and abort rather
				// than pay the player and hand the goods back.
				if (!leftover.isEmpty()) {
					for (ItemStack stack : leftover) give(player, stack);
					ks.wallets().addBalance(ownerUuid, price); // undo the owner debit
					Wallet.LOGGER.warn("[Wallet] Shop chest at {} refused items on SELL, trade unwound", shop.chestPos());
					player.sendSystemMessage(Msg.red("The shop chest refused your items - nothing was traded"));
					return;
				}
			}
			if (!ks.wallets().addBalance(buyerUuid, price)) {
				ks.wallets().addBalance(ownerUuid, price); // refund owner
				Wallet.LOGGER.error("[Wallet] SELL credit to {} failed, owner refunded", buyerUuid);
				player.sendSystemMessage(Msg.red("Payment failed - nothing was traded"));
				return;
			}

			List<ItemText.ItemRef> template = templateRefs(itemTemplate);
			String itemsStr = ItemText.itemListText(template);
			String ownerName = PlayerNames.byUuid(server, ownerUuid);

			ks.history().addEntry(buyerUuid, "shop_sell", price, ownerName, itemsStr);
			ks.history().addEntry(ownerUuid, "shop_buy", price, buyerName, itemsStr);

			player.sendSystemMessage(Msg.empty()
				.append(Msg.gold("Sold "))
				.append(ItemText.itemListComponent(template))
				.append(Msg.gold(" for "))
				.append(Msg.green(price + "$")));

			if (ownerPlayer != null) {
				ownerPlayer.sendSystemMessage(Msg.empty()
					.append(Msg.yellow(buyerName))
					.append(Msg.gold(" sold "))
					.append(ItemText.itemListComponent(template))
					.append(Msg.gold(" to your shop for "))
					.append(Msg.green(price + "$")));
			}
		}
	}

	private static ServerPlayer onlinePlayer(MinecraftServer server, String uuid) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(uuid));
		} catch (Exception e) {
			return null;
		}
	}
}
