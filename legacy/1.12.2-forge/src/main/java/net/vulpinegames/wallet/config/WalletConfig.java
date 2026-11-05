package net.vulpinegames.wallet.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.vulpinegames.wallet.Wallet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loaded from config/wallet.json - same keys as the modern builds. The
 * kubejs/config/kubeshop.json fallback is kept for parity, although no 1.12.2
 * pack runs KubeJS.
 */
public final class WalletConfig {
	/** "sqlite" (default) or "mysql". */
	public String backend = "sqlite";
	/** Optional SQLite file override; empty = the world folder's wallet.db. */
	public String sqliteFile = "";
	/** True when the config file existed but could not be parsed - fail closed. */
	public boolean loadFailed = false;

	public String host = "localhost";
	public int port = 3306;
	public String database = "minecraft";
	public String user = "root";
	public String password = "";

	public boolean isMysql() {
		return "mysql".equalsIgnoreCase(backend);
	}

	/** Item + metadata + NBT exact stack matching (default) vs item + subtype only. */
	public boolean strictItemMatch = true;
	/** Enables /wallet top. Off by default - balances stay private. */
	public boolean walletTop = false;
	/**
	 * 1.12.2-only, OFF by default so behaviour matches the modern builds, which
	 * leave chest access to claim mods. For servers without claims (RLCraft):
	 * a shop chest opens only for its owner and OPs.
	 */
	public boolean protectShopChests = false;
	/**
	 * 1.12.2-only, OFF by default. Refuses non-owners placing a block entity
	 * (hopper, pipe, second chest) next to a shop chest, which would drain or
	 * merge into it.
	 */
	public boolean protectShopNeighbors = false;

	public static WalletConfig load(Path configDir, Path gameDir) {
		Path primary = configDir.resolve("wallet.json");
		Path legacy = gameDir.resolve("kubejs/config/kubeshop.json");
		Path source = Files.exists(primary) ? primary : legacy;
		WalletConfig cfg = new WalletConfig();
		if (!Files.exists(source)) {
			Wallet.LOGGER.info("[Wallet] No config at {} - using SQLite defaults", primary);
			return cfg;
		}
		try {
			String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
			JsonObject json = new Gson().fromJson(text, JsonObject.class);
			if (json == null) throw new IOException("empty file");
			if (json.has("backend")) {
				cfg.backend = json.get("backend").getAsString();
			} else if (json.has("host") || json.has("database") || json.has("user")) {
				// MySQL fields but no backend key: defaulting to sqlite would
				// silently start an empty economy.
				cfg.backend = "mysql";
			}
			if (json.has("sqliteFile")) cfg.sqliteFile = json.get("sqliteFile").getAsString();
			if (json.has("host")) cfg.host = json.get("host").getAsString();
			if (json.has("port")) cfg.port = json.get("port").getAsInt();
			if (json.has("database")) cfg.database = json.get("database").getAsString();
			if (json.has("user")) cfg.user = json.get("user").getAsString();
			if (json.has("password")) cfg.password = json.get("password").getAsString();
			if (json.has("strictItemMatch")) cfg.strictItemMatch = json.get("strictItemMatch").getAsBoolean();
			if (json.has("walletTop")) cfg.walletTop = json.get("walletTop").getAsBoolean();
			if (json.has("protectShopChests")) cfg.protectShopChests = json.get("protectShopChests").getAsBoolean();
			if (json.has("protectShopNeighbors")) cfg.protectShopNeighbors = json.get("protectShopNeighbors").getAsBoolean();
			Wallet.LOGGER.info("[Wallet] Config loaded from {}", source);
		} catch (IOException | RuntimeException e) {
			// A file that exists but does not parse is NOT a request for defaults.
			Wallet.LOGGER.error("[Wallet] Failed to read {}: {} - economy will stay OFFLINE until fixed", source, e.toString());
			cfg.loadFailed = true;
		}
		return cfg;
	}
}
