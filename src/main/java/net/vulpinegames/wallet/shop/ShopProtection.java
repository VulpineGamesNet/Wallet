package net.vulpinegames.wallet.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.world.Positions;

import java.util.ArrayList;
import java.util.List;

/**
 * Break protection, ported from the script - minus the FTB bypass bookkeeping
 * (gone with the bypass itself), plus one fix the script lacked: breaking the
 * block a shop sign hangs on pops the sign, so that support block is guarded
 * with the same ownership rules.
 */
public final class ShopProtection {
	private ShopProtection() {}

	/** True = cancel the break. */
	public static boolean onBlockBreak(WalletServer ks, ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		String blockKey = Positions.blockKey(level, pos);
		boolean wasSign = state.getBlock() instanceof WallSignBlock;
		BlockPos otherHalf = wasSign ? null : SignShop.otherChestHalf(state, pos);

		// Cheap set lookups first - a dirt break must not touch the DB.
		boolean signShop = wasSign && ks.shopCache().contains(blockKey);
		boolean chestShop = !wasSign && (ks.shopCache().containsChest(blockKey)
			|| (otherHalf != null && ks.shopCache().containsChest(Positions.blockKey(level, otherHalf))));
		List<String> supportedSignKeys = wasSign ? List.of() : supportedShopSigns(ks, level, pos);
		if (!signShop && !chestShop && supportedSignKeys.isEmpty()) return false;

		// Known shop block + DB down: fail closed, deny the break.
		if (!ks.database().isAvailable()) {
			if (player != null) player.sendSystemMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}

		// Which shops does this block carry? A sign is its own shop; a container
		// is whatever signs point at it (both halves); a support block, the sign.
		List<String> brokenSignKeys = new ArrayList<>();
		if (signShop) {
			brokenSignKeys.add(blockKey);
		} else if (chestShop) {
			List<String> here = ks.shops().getShopsByChestPos(blockKey);
			List<String> there = otherHalf != null ? ks.shops().getShopsByChestPos(Positions.blockKey(level, otherHalf)) : List.of();
			if (here == null || there == null) { // query failed on a shop chest - fail closed
				if (player != null) player.sendSystemMessage(Msg.red("Economy is offline - shop locked."));
				return true;
			}
			brokenSignKeys.addAll(here);
			brokenSignKeys.addAll(there);
		}
		brokenSignKeys.addAll(supportedSignKeys);

		List<ShopDao.Shop> shops = new ArrayList<>();
		boolean serverShop = false;
		for (String signKey : brokenSignKeys) {
			ShopDao.Shop shop = ks.shops().getShop(signKey);
			if (shop != null) {
				shops.add(shop);
				if (shop.isServer()) serverShop = true;
			}
		}
		if (shops.isEmpty() && brokenSignKeys.isEmpty()) return false;

		if (player != null && !player.hasPermissions(2)) {
			// A server shop still carries its creator as owner, but it is no
			// longer theirs to tear down.
			if (serverShop) {
				player.sendSystemMessage(Msg.red("That is a server shop."));
				return true;
			}
			String pUuid = player.getStringUUID();
			for (ShopDao.Shop shop : shops) {
				if (!pUuid.equals(shop.owner())) {
					player.sendSystemMessage(Msg.red("That shop is not yours."));
					return true;
				}
			}
		}

		// Allowed - drop the shop rows for whatever just went.
		for (String signKey : brokenSignKeys) {
			ShopDao.Shop shop = ks.shops().getShop(signKey);
			ks.shops().removeShop(signKey);
			// Two signs can share one chest - drop the chest key only once no
			// shop row references it anymore.
			String chestKey = shop != null && ks.shops().getShopsByChestPos(shop.chestPos()).isEmpty()
				? shop.chestPos() : null;
			ks.shopCache().remove(signKey, chestKey);
			Wallet.LOGGER.info("[Wallet] Shop removed ({} broken by {})",
				wasSign ? "sign" : "block", player != null ? player.getName().getString() : "non-player");
			if (player != null) {
				player.sendSystemMessage(Msg.gold(wasSign ? "Shop removed!" : "Shop removed (chest destroyed)!"));
			}
		}
		return false;
	}

	/**
	 * Owner-gate for opening a shop chest. The chest's stock IS the shop, so
	 * only the owner of every shop on it (or an OP) may open it - server shops
	 * are staff-only, mirroring the break rules. True = cancel the click.
	 */
	public static boolean onRightClickShopChest(WalletServer ks, ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockPos other = SignShop.otherChestHalf(state, pos);
		boolean isShopChest = ks.shopCache().containsChest(Positions.blockKey(level, pos))
			|| (other != null && ks.shopCache().containsChest(Positions.blockKey(level, other)));
		if (!isShopChest) return false;
		if (player.hasPermissions(2)) return false;

		// Cache already proved this is a shop chest - a DB failure now must DENY,
		// never fall open.
		if (!ks.database().isAvailable()) {
			player.sendSystemMessage(Msg.red("Economy is offline - shop locked."));
			return true;
		}

		String pUuid = player.getStringUUID();
		for (BlockPos p : other != null ? new BlockPos[]{pos, other} : new BlockPos[]{pos}) {
			List<String> signKeys = ks.shops().getShopsByChestPos(Positions.blockKey(level, p));
			if (signKeys == null) { // query failed on a known shop chest - fail closed
				player.sendSystemMessage(Msg.red("Economy is offline - shop locked."));
				return true;
			}
			for (String signKey : signKeys) {
				ShopDao.Shop shop = ks.shops().getShop(signKey);
				if (shop == null) continue;
				if (shop.isServer()) {
					player.sendSystemMessage(Msg.red("That is a server shop."));
					return true;
				}
				if (!pUuid.equals(shop.owner())) {
					player.sendSystemMessage(Msg.red("That shop chest is not yours."));
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A block no explosion or piston may touch: a shop sign, a shop chest, or
	 * a block a shop sign hangs on. Cache lookups only - no DB.
	 */
	public static boolean isProtectedBlock(WalletServer ks, ServerLevel level, BlockPos pos) {
		String key = Positions.blockKey(level, pos);
		if (ks.shopCache().contains(key) || ks.shopCache().containsChest(key)) return true;
		BlockPos other = SignShop.otherChestHalf(level.getBlockState(pos), pos);
		if (other != null && ks.shopCache().containsChest(Positions.blockKey(level, other))) return true;
		return !supportedShopSigns(ks, level, pos).isEmpty();
	}

	/** Strip shop blocks out of an explosion's affected list. */
	public static void filterExplosion(WalletServer ks, ServerLevel level, List<BlockPos> affected) {
		affected.removeIf(pos -> isProtectedBlock(ks, level, pos));
	}

	/**
	 * True = refuse the whole piston action: it would move or destroy a shop
	 * sign, a shop chest (immovable anyway, but toDestroy can hold signs), or
	 * yank the block a sign hangs on.
	 */
	public static boolean shouldCancelPiston(WalletServer ks, ServerLevel level, Iterable<BlockPos> moved) {
		for (BlockPos pos : moved) {
			if (isProtectedBlock(ks, level, pos)) return true;
		}
		return false;
	}

	/** Shop signs hanging on this block (4 map lookups, DB-free). */
	private static List<String> supportedShopSigns(WalletServer ks, ServerLevel level, BlockPos pos) {
		List<String> keys = new ArrayList<>();
		if (ks.shopCache().size() == 0) return keys;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos signPos = pos.relative(dir);
			BlockState signState = level.getBlockState(signPos);
			if (!(signState.getBlock() instanceof WallSignBlock)) continue;
			if (signState.getValue(WallSignBlock.FACING) != dir) continue;
			String signKey = Positions.blockKey(level, signPos);
			if (ks.shopCache().contains(signKey)) keys.add(signKey);
		}
		return keys;
	}
}
