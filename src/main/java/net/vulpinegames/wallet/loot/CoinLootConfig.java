package net.vulpinegames.wallet.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.coin.CoinDenomination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The "coinLoot" block of config/wallet.json: coins rolled into loot chests
 * and dropped by mobs a player killed. Off unless the block says
 * {@code "enabled": true} - only servers that opt in mint coins this way.
 *
 * <pre>
 * "coinLoot": {
 *   "enabled": true,
 *   "chests": [ { "name": "rich", "match": ["^deceasedcraft:chests/.+/safe$"], "chance": 0.3,
 *                 "pool": [ { "weight": 77, "coin": 100, "min": 1, "max": 2 } ] } ],
 *   "mobs":   [ { "name": "boss", "entities": ["zombie_extreme:rat_king"], "chance": 1.0,
 *                 "pool": [ { "weight": 1, "coin": 100 } ] } ]
 * }
 * </pre>
 *
 * Chest rules match the loot table id by regex, first matching rule wins.
 * Mob rules match the entity type id exactly. A rule that does not parse is
 * logged and the whole feature stays off - a typo must never mint coins
 * from the wrong table, and must never take the economy itself offline.
 */
public final class CoinLootConfig {
	public record Drop(int weight, CoinDenomination denom, int min, int max) {
		public double expectedValue() {
			return denom.value() * (min + max) / 2.0;
		}
	}

	public record Rule(String name, List<Pattern> match, Set<String> entities, double chance,
			List<Drop> pool, int totalWeight) {
		/** Mean coin value of one roll of this rule, chance included. */
		public double expectedValue() {
			double ev = 0;
			for (Drop d : pool) ev += d.weight() * d.expectedValue();
			return chance * ev / totalWeight;
		}
	}

	/** Cache sentinel: this loot table matched no rule. */
	private static final Rule NO_RULE = new Rule("", List.of(), Set.of(), 0, List.of(), 1);

	public final boolean enabled;
	public final List<Rule> chests;
	public final List<Rule> mobs;
	private final Map<String, Rule> mobIndex;
	// The modifier sees EVERY loot roll (block and entity drops too), so the
	// regex walk runs once per table id and is cached for the config's life.
	private final Map<String, Rule> chestCache = new ConcurrentHashMap<>();

	private CoinLootConfig(boolean enabled, List<Rule> chests, List<Rule> mobs) {
		this.enabled = enabled;
		this.chests = chests;
		this.mobs = mobs;
		Map<String, Rule> idx = new HashMap<>();
		for (Rule r : mobs) for (String id : r.entities()) idx.putIfAbsent(id, r);
		this.mobIndex = idx;
	}

	public static CoinLootConfig disabled() {
		return new CoinLootConfig(false, List.of(), List.of());
	}

	/** The chest rule for a loot table id, or null when none matches. */
	public Rule chestRule(String tableId) {
		if (!enabled || chests.isEmpty()) return null;
		Rule r = chestCache.computeIfAbsent(tableId, id -> {
			for (Rule rule : chests) {
				for (Pattern p : rule.match()) {
					if (p.matcher(id).find()) return rule;
				}
			}
			return NO_RULE;
		});
		return r == NO_RULE ? null : r;
	}

	/** The mob rule for an entity type id, or null when none matches. */
	public Rule mobRule(String entityId) {
		if (!enabled) return null;
		return mobIndex.get(entityId);
	}

	public static CoinLootConfig parse(JsonElement element) {
		if (element == null || !element.isJsonObject()) return disabled();
		JsonObject json = element.getAsJsonObject();
		boolean enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
		if (!enabled) return disabled();
		try {
			List<Rule> chests = parseRules(json.getAsJsonArray("chests"), false);
			List<Rule> mobs = parseRules(json.getAsJsonArray("mobs"), true);
			CoinLootConfig cfg = new CoinLootConfig(true, chests, mobs);
			Wallet.LOGGER.info("[Wallet] Coin loot ON: {} chest rules, {} mob rules", chests.size(), mobs.size());
			for (Rule r : chests) Wallet.LOGGER.info("[Wallet]   chest '{}' {}% -> ${} per roll", r.name(), pct(r.chance()), round2(r.expectedValue()));
			for (Rule r : mobs) Wallet.LOGGER.info("[Wallet]   mob '{}' ({} types) {}% -> ${} per kill", r.name(), r.entities().size(), pct(r.chance()), round2(r.expectedValue()));
			return cfg;
		} catch (RuntimeException e) {
			Wallet.LOGGER.error("[Wallet] coinLoot config is invalid ({}) - coin loot stays OFF until fixed", e.getMessage());
			return disabled();
		}
	}

	private static List<Rule> parseRules(JsonArray array, boolean mob) {
		if (array == null) return List.of();
		List<Rule> rules = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (JsonElement el : array) {
			JsonObject o = el.getAsJsonObject();
			String name = o.has("name") ? o.get("name").getAsString() : (mob ? "mob" : "chest") + (rules.size() + 1);
			if (!names.add(name)) throw new IllegalArgumentException("duplicate rule name '" + name + "'");
			double chance = o.get("chance").getAsDouble();
			if (!(chance >= 0 && chance <= 1)) throw new IllegalArgumentException(name + ": chance must be 0..1");
			List<Pattern> match = new ArrayList<>();
			Set<String> entities = new HashSet<>();
			if (mob) {
				JsonArray ids = o.getAsJsonArray("entities");
				if (ids == null || ids.isEmpty()) throw new IllegalArgumentException(name + ": no entities");
				for (JsonElement id : ids) entities.add(id.getAsString());
			} else {
				JsonArray patterns = o.getAsJsonArray("match");
				if (patterns == null || patterns.isEmpty()) throw new IllegalArgumentException(name + ": no match patterns");
				for (JsonElement p : patterns) {
					try {
						match.add(Pattern.compile(p.getAsString()));
					} catch (PatternSyntaxException e) {
						throw new IllegalArgumentException(name + ": bad regex " + p.getAsString());
					}
				}
			}
			List<Drop> pool = new ArrayList<>();
			int total = 0;
			JsonArray poolJson = o.getAsJsonArray("pool");
			if (poolJson == null || poolJson.isEmpty()) throw new IllegalArgumentException(name + ": empty pool");
			for (JsonElement de : poolJson) {
				JsonObject d = de.getAsJsonObject();
				int weight = d.has("weight") ? d.get("weight").getAsInt() : 1;
				int coin = d.get("coin").getAsInt();
				CoinDenomination denom = CoinDenomination.byValue(coin);
				if (denom == null) throw new IllegalArgumentException(name + ": no $" + coin + " coin exists");
				int min = d.has("min") ? d.get("min").getAsInt() : 1;
				int max = d.has("max") ? d.get("max").getAsInt() : min;
				if (weight <= 0 || min < 1 || max < min || max > 64) {
					throw new IllegalArgumentException(name + ": pool entry needs weight > 0 and 1 <= min <= max <= 64");
				}
				pool.add(new Drop(weight, denom, min, max));
				total += weight;
			}
			rules.add(new Rule(name, List.copyOf(match), Collections.unmodifiableSet(entities), chance, List.copyOf(pool), total));
		}
		return List.copyOf(rules);
	}

	private static String pct(double chance) {
		return String.valueOf(Math.round(chance * 1000) / 10.0);
	}

	private static double round2(double v) {
		return Math.round(v * 100) / 100.0;
	}
}
