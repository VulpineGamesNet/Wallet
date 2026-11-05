package net.vulpinegames.wallet;

import net.minecraft.server.MinecraftServer;
import net.vulpinegames.wallet.config.WalletConfig;
import net.vulpinegames.wallet.db.Database;
import net.vulpinegames.wallet.db.HistoryDao;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.shop.ShopCache;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Everything alive between server start and stop: config, pool, DAOs, caches.
 * One instance per running server, held by Wallet.
 */
public final class WalletServer {
	private static final AtomicLong NEXT_GENERATION = new AtomicLong();
	private final MinecraftServer server;
	private long databaseGeneration;
	private WalletConfig config;
	private final Database database = new Database();
	private final WalletDao wallets = new WalletDao(database);
	private final ShopDao shops = new ShopDao(database);
	private final HistoryDao history = new HistoryDao(database);
	private final ShopCache shopCache = new ShopCache();
	private Path sqlitePath;

	private WalletServer(MinecraftServer server) {
		this.server = server;
	}

	public static WalletServer start(MinecraftServer server, Path configDir) {
		WalletServer ks = new WalletServer(server);
		ks.config = WalletConfig.load(configDir, configDir.getParent());
		ks.sqlitePath = ks.config.sqliteFile.isEmpty()
			? server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("wallet.db")
			: configDir.getParent().resolve(ks.config.sqliteFile);
		ks.database.init(ks.config, ks.sqlitePath);
		ks.databaseGeneration = NEXT_GENERATION.incrementAndGet();
		if (ks.database.isAvailable()) {
			ks.shopCache.reload(ks.shops);
			Wallet.LOGGER.info("[Wallet] {} shop signs cached", ks.shopCache.size());
		}
		return ks;
	}

	/** /wallet admin reload: config, caches, and a dead DB connection - no restart. */
	public void reload(Path configDir) {
		config = WalletConfig.load(configDir, configDir.getParent());
		if (!config.sqliteFile.isEmpty()) {
			sqlitePath = configDir.getParent().resolve(config.sqliteFile);
		}
		// Unconditional: init() closes any prior pool, revives a dead DB, and
		// picks up a backend change in wallet.json. isAvailable() alone stays
		// true after boot, so a conditional re-init would never fire.
		database.init(config, sqlitePath);
		databaseGeneration = NEXT_GENERATION.incrementAndGet();
		if (database.isAvailable()) shopCache.reload(shops);
	}

	public void stop() {
		database.close();
	}

	public MinecraftServer server() {
		return server;
	}

	public WalletConfig config() {
		return config;
	}

	public Database database() {
		return database;
	}

	public long databaseGeneration() {
		return databaseGeneration;
	}

	public WalletDao wallets() {
		return wallets;
	}

	public ShopDao shops() {
		return shops;
	}

	public HistoryDao history() {
		return history;
	}

	public ShopCache shopCache() {
		return shopCache;
	}

}
