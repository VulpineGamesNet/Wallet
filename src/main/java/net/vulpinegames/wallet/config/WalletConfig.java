package net.vulpinegames.wallet.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.vulpinegames.wallet.Wallet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loaded from config/wallet.json, falling back to the legacy
 * kubejs/config/kubeshop.json so deploy day needs no file move.
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

	/** Component/NBT-exact stack matching (ATM10) vs id-only (DC). */
	public boolean strictItemMatch = true;
	/** Enables /wallet top. Off by default - balances stay private. */
	public boolean walletTop = false;
	/**
	 * Item names in chat as client-mod link tokens instead of plain text with a
	 * hover. Only for servers whose pack ships that client mod (ATM10);
	 * everyone else would read the raw token.
	 */
	public boolean itemChatLinks = false;

	public static WalletConfig load(Path configDir, Path gameDir) {
		Path primary = configDir.resolve("wallet.json");
		Path legacy = gameDir.resolve("kubejs/config/kubeshop.json");
		Path source = Files.exists(primary) ? primary : legacy;
		WalletConfig cfg = new WalletConfig();
		if (!Files.exists(source)) {
			// The happy zero-config path: SQLite in the world folder.
			Wallet.LOGGER.info("[Wallet] No config at {} - using SQLite defaults", primary);
			return cfg;
		}
		try {
			JsonObject json = new Gson().fromJson(Files.readString(source), JsonObject.class);
			if (json.has("backend")) {
				cfg.backend = json.get("backend").getAsString();
			} else if (json.has("host") || json.has("database") || json.has("user")) {
				// Legacy config from the scripts: MySQL fields, no backend key.
				// Defaulting those to sqlite would silently start an empty economy.
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
			if (json.has("itemChatLinks")) cfg.itemChatLinks = json.get("itemChatLinks").getAsBoolean();
			Wallet.LOGGER.info("[Wallet] Config loaded from {}", source);
		} catch (IOException | RuntimeException e) {
			// A file that exists but does not parse is NOT a request for
			// defaults - defaulting to sqlite would silently start an empty
			// economy while prod MySQL sits untouched. Come up offline instead.
			Wallet.LOGGER.error("[Wallet] Failed to read {}: {} - economy will stay OFFLINE until fixed", source, e.toString());
			cfg.loadFailed = true;
		}
		return cfg;
	}
}
