package net.vulpinegames.wallet.loot;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.coin.Coins;

import java.util.List;

/**
 * Rolls coin loot. Loader glue calls in with a loot table id (chests) or an
 * entity type id (a mob a real player killed) and spawns what comes back.
 * The coins are built by {@link Coins#create}, so they are the exact item a
 * /wallet withdraw hands out.
 */
public final class CoinLoot {
	private CoinLoot() {}

	/** Coins for a chest generated from this loot table; empty when none. */
	public static List<ItemStack> rollChest(String tableId, RandomSource random) {
		WalletServer ws = Wallet.active();
		if (ws == null) return List.of();
		CoinLootConfig.Rule rule = ws.config().coinLoot.chestRule(tableId);
		return rule == null ? List.of() : roll(ws, "chest:" + rule.name(), rule, random);
	}

	/** Coins for a mob of this entity type killed by a real player; empty when none. */
	public static List<ItemStack> rollMob(String entityId, RandomSource random) {
		WalletServer ws = Wallet.active();
		if (ws == null) return List.of();
		CoinLootConfig.Rule rule = ws.config().coinLoot.mobRule(entityId);
		return rule == null ? List.of() : roll(ws, "mob:" + rule.name(), rule, random);
	}

	private static List<ItemStack> roll(WalletServer ws, String source, CoinLootConfig.Rule rule, RandomSource random) {
		if (random.nextDouble() >= rule.chance()) return List.of();
		int pick = random.nextInt(rule.totalWeight());
		for (CoinLootConfig.Drop drop : rule.pool()) {
			pick -= drop.weight();
			if (pick < 0) {
				int count = drop.min() + (drop.max() > drop.min() ? random.nextInt(drop.max() - drop.min() + 1) : 0);
				CoinLootStats stats = ws.coinLootStats();
				if (stats != null) stats.record(source, count * drop.denom().value());
				return List.of(Coins.create(drop.denom(), count));
			}
		}
		return List.of();
	}
}
