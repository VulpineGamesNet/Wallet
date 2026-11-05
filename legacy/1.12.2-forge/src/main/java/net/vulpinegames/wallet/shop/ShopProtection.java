package net.vulpinegames.wallet.shop;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.world.Positions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shop protection. Always on, as in the modern builds:
 *   - break: sign, chest (both halves) and the block a sign hangs on
 *   - explosions skip shop blocks
 * Opt-in (wallet.json, default off - the modern builds leave this to claim
 * mods; servers without claims such as RLCraft turn them on):
 *   - protectShopChests:    a shop chest opens for its owner and OPs only
 *   - protectShopNeighbors: no block entity (hopper, pipe, another chest)
 *                           placed next to someone else's shop chest
 * 1.12.2 Forge has no piston event; vanilla pistons cannot move chests, and a
 * piston that pops a shop sign leaves the stock locked in the chest for the
 * owner to collect.
 */
public final class ShopProtection {
	private ShopProtection() {}

	private static boolean isOp(EntityPlayerMP player) {
		return player.canUseCommand(2, "wallet");
	}

	/** Sign keys of the shops on the chest at pos; null on a DB error (callers fail closed). */
	private static List<String> chestShops(WalletServer ks, World world, BlockPos pos) {
		return ks.shops().getShopsByChestPos(Positions.blockKey(world, pos));
	}

	/** True = cancel the break. */
	public static boolean onBlockBreak(WalletServer ks, EntityPlayerMP player, World world, BlockPos pos, IBlockState state) {
		String blockKey = Positions.blockKey(world, pos);
		boolean wasSign = SignShop.isWallSign(state);
		BlockPos otherHalf = wasSign ? null : SignShop.otherChestHalf(world, state, pos);

		// Cheap set lookups first - a dirt break must not touch the DB.
		boolean signShop = wasSign && ks.shopCache().contains(blockKey);
		boolean chestShop = !wasSign && (ks.shopCache().containsChest(blockKey)
			|| (otherHalf != null && ks.shopCache().containsChest(Positions.blockKey(world, otherHalf))));
		List<String> supportedSignKeys = wasSign ? Collections.<String>emptyList() : supportedShopSigns(ks, world, pos);
		if (!signShop && !chestShop && supportedSignKeys.isEmpty()) return false;

		if (!ks.database().isAvailable()) {
			if (player != null) player.sendMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}

		List<String> brokenSignKeys = new ArrayList<>();
		if (signShop) {
			brokenSignKeys.add(blockKey);
		} else if (chestShop) {
			List<String> here = chestShops(ks, world, pos);
			List<String> there = otherHalf != null ? chestShops(ks, world, otherHalf) : Collections.<String>emptyList();
			if (here == null || there == null) {
				if (player != null) player.sendMessage(Msg.red("Economy is offline - shop locked."));
				return true;
			}
			brokenSignKeys.addAll(here);
			brokenSignKeys.addAll(there);
		}
		for (String k : supportedSignKeys) if (!brokenSignKeys.contains(k)) brokenSignKeys.add(k);

		Map<String, ShopDao.Shop> shops = new HashMap<>();
		boolean serverShop = false;
		for (String signKey : brokenSignKeys) {
			ShopDao.Shop shop = ks.shops().getShop(signKey);
			if (shop != null) {
				shops.put(signKey, shop);
				if (shop.isServer) serverShop = true;
			}
		}
		if (brokenSignKeys.isEmpty()) return false;

		if (player != null && !isOp(player)) {
			if (serverShop) {
				player.sendMessage(Msg.red("That is a server shop."));
				return true;
			}
			String pUuid = player.getCachedUniqueIdString();
			for (ShopDao.Shop shop : shops.values()) {
				if (!pUuid.equals(shop.owner)) {
					player.sendMessage(Msg.red("That shop is not yours."));
					return true;
				}
			}
		}

		// Allowed - drop the shop rows for whatever just went.
		for (String signKey : brokenSignKeys) {
			ShopDao.Shop shop = shops.get(signKey);
			ks.shops().removeShop(signKey);
			String chestKey = null;
			if (shop != null) {
				List<String> stillThere = ks.shops().getShopsByChestPos(shop.chestPos);
				if (stillThere != null && stillThere.isEmpty()) chestKey = shop.chestPos;
			}
			ks.shopCache().remove(signKey, chestKey);
			Wallet.LOGGER.info("[Wallet] Shop {} removed ({} broken by {})", signKey,
				wasSign ? "sign" : "block", player != null ? player.getName() : "non-player");
			if (player != null) player.sendMessage(Msg.gold(wasSign ? "Shop removed!" : "Shop removed (chest destroyed)!"));
		}
		return false;
	}

	/** Owner-gate for opening a shop chest. True = cancel the click. */
	public static boolean onRightClickShopChest(WalletServer ks, EntityPlayerMP player, World world, BlockPos pos) {
		if (!ks.config().protectShopChests || ks.shopCache().size() == 0) return false;
		IBlockState state = world.getBlockState(pos);
		BlockPos other = SignShop.otherChestHalf(world, state, pos);
		boolean isShopChest = ks.shopCache().containsChest(Positions.blockKey(world, pos))
			|| (other != null && ks.shopCache().containsChest(Positions.blockKey(world, other)));
		if (!isShopChest) return false;
		if (isOp(player)) return false;

		if (!ks.database().isAvailable()) {
			player.sendMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}

		String pUuid = player.getCachedUniqueIdString();
		for (BlockPos p : other != null ? new BlockPos[]{pos, other} : new BlockPos[]{pos}) {
			List<String> signKeys = chestShops(ks, world, p);
			if (signKeys == null) {
				player.sendMessage(Msg.red("Economy is offline - shop locked."));
				return true;
			}
			for (String signKey : signKeys) {
				ShopDao.Shop shop = ks.shops().getShop(signKey);
				if (shop == null) continue;
				if (shop.isServer) {
					player.sendMessage(Msg.red("That is a server shop."));
					return true;
				}
				if (!pUuid.equals(shop.owner)) {
					player.sendMessage(Msg.red("That shop chest is not yours."));
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A block entity placed next to a shop chest by someone who does not own
	 * it (a hopper below, a pipe, a second chest merging into a double) would
	 * reach the stock. True = cancel the placement.
	 */
	public static boolean onPlaceNearShop(WalletServer ks, EntityPlayerMP player, World world, BlockPos pos, IBlockState placed) {
		if (!ks.config().protectShopNeighbors || ks.shopCache().size() == 0) return false;
		if (SignShop.isWallSign(placed) || !placed.getBlock().hasTileEntity(placed)) return false;
		// The shop row names one chest half; a hopper under the other half of a
		// double chest reaches the same inventory, so resolve both halves.
		List<BlockPos> shopChests = new ArrayList<>();
		for (EnumFacing dir : EnumFacing.values()) {
			BlockPos n = pos.offset(dir);
			if (ks.shopCache().containsChest(Positions.blockKey(world, n))) {
				shopChests.add(n);
				continue;
			}
			BlockPos other = SignShop.otherChestHalf(world, world.getBlockState(n), n);
			if (other != null && ks.shopCache().containsChest(Positions.blockKey(world, other))) shopChests.add(other);
		}
		if (shopChests.isEmpty()) return false;
		if (isOp(player)) return false;
		if (!ks.database().isAvailable()) {
			player.sendMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}
		String pUuid = player.getCachedUniqueIdString();
		for (BlockPos chest : shopChests) {
			List<String> signKeys = chestShops(ks, world, chest);
			if (signKeys == null) return true;
			for (String signKey : signKeys) {
				ShopDao.Shop shop = ks.shops().getShop(signKey);
				if (shop != null && (shop.isServer || !pUuid.equals(shop.owner))) {
					player.sendMessage(Msg.red("You cannot place that next to someone else's shop chest."));
					return true;
				}
			}
		}
		return false;
	}

	/** A shop sign, a shop chest, or a block a shop sign hangs on. Cache only - no DB. */
	public static boolean isProtectedBlock(WalletServer ks, World world, BlockPos pos) {
		String key = Positions.blockKey(world, pos);
		if (ks.shopCache().contains(key) || ks.shopCache().containsChest(key)) return true;
		BlockPos other = SignShop.otherChestHalf(world, world.getBlockState(pos), pos);
		if (other != null && ks.shopCache().containsChest(Positions.blockKey(world, other))) return true;
		return !supportedShopSigns(ks, world, pos).isEmpty();
	}

	/** Strip shop blocks out of an explosion's affected list. */
	public static void filterExplosion(WalletServer ks, World world, List<BlockPos> affected) {
		if (ks.shopCache().size() == 0) return;
		affected.removeIf(pos -> isProtectedBlock(ks, world, pos));
	}

	/** Shop signs hanging on this block (4 map lookups, DB-free). */
	private static List<String> supportedShopSigns(WalletServer ks, World world, BlockPos pos) {
		List<String> keys = new ArrayList<>();
		if (ks.shopCache().size() == 0) return keys;
		for (EnumFacing dir : EnumFacing.Plane.HORIZONTAL) {
			BlockPos signPos = pos.offset(dir);
			if (!pos.equals(SignShop.attachedBlockPos(world.getBlockState(signPos), signPos))) continue;
			String signKey = Positions.blockKey(world, signPos);
			if (ks.shopCache().contains(signKey)) keys.add(signKey);
		}
		return keys;
	}
}
