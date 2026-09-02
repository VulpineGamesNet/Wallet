package net.vulpinegames.wallet.shop;

import net.vulpinegames.wallet.db.ShopDao;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory keys of shop signs and their chests, loaded once at server start
 * and mutated on create/delete/convert - the mod is the sole authority, so no
 * periodic DB refresh. It keeps the break handler and the click handler free
 * of DB queries for blocks that carry no shop. /wallet admin reload re-reads
 * it, which is the documented answer to a hand-edited wallet_shops row.
 */
public final class ShopCache {
	private final Set<String> signKeys = ConcurrentHashMap.newKeySet();
	private final Set<String> chestKeys = ConcurrentHashMap.newKeySet();

	public void reload(ShopDao shops) {
		signKeys.clear();
		chestKeys.clear();
		for (Map.Entry<String, ShopDao.Shop> e : shops.getAllShops().entrySet()) {
			signKeys.add(e.getKey());
			chestKeys.add(e.getValue().chestPos());
		}
	}

	public boolean contains(String signKey) {
		return signKeys.contains(signKey);
	}

	public boolean containsChest(String chestKey) {
		return chestKeys.contains(chestKey);
	}

	public void add(String signKey, String chestKey) {
		signKeys.add(signKey);
		chestKeys.add(chestKey);
	}

	/** chestKey null = another shop still references that chest, keep its key. */
	public void remove(String signKey, String chestKey) {
		signKeys.remove(signKey);
		if (chestKey != null) chestKeys.remove(chestKey);
	}

	public int size() {
		return signKeys.size();
	}
}
