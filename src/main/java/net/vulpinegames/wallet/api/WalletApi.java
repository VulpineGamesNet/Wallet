package net.vulpinegames.wallet.api;

import net.minecraft.server.MinecraftServer;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.world.PlayerNames;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The stable surface for addon mods (WalletPlots, a future archive addon).
 * Addons call ONLY this class - never DAOs or internals, so core can evolve
 * freely behind it. Everything is a no-op/failure while the economy is
 * offline; check isAvailable() or just handle the false/exception.
 */
public final class WalletApi {
	private WalletApi() {}

	private static WalletServer ks() {
		return Wallet.active();
	}

	/** False while no server is up or the database is offline. */
	public static boolean isAvailable() {
		WalletServer ks = ks();
		return ks != null && ks.database().isAvailable();
	}

	/** Which SQL dialect the shared database speaks. */
	public static boolean isMysql() {
		WalletServer ks = ks();
		return ks != null && ks.database().isMysql();
	}

	/**
	 * A pooled connection to the shared database, for addon-owned tables.
	 * Close it (try-with-resources) - it returns to the pool.
	 *
	 * WARNING: on the SQLite backend the pool holds EXACTLY ONE connection.
	 * Never call another WalletApi method (getBalance, removeBalance, ...)
	 * while holding this connection - the second call would wait for the pool's
	 * only connection and block the server thread until the timeout. Do your
	 * balance/history calls before you open it or after you close it, or use
	 * {@link #transaction} to get one connection for the whole unit of work.
	 */
	public static Connection connection() throws SQLException {
		WalletServer ks = ks();
		if (ks == null) throw new SQLException("Wallet economy is offline");
		return ks.database().getConnection();
	}

	/** A unit of work that runs against one borrowed connection. */
	public interface SqlWork<T> {
		T run(Connection conn) throws SQLException;
	}

	/**
	 * Run addon SQL against a single pooled connection - the safe shape when a
	 * unit of work needs several statements, avoiding the size-1 re-entrancy
	 * trap described on {@link #connection}. The connection is NOT auto-commit
	 * managed here; do your own transaction control inside if you need it.
	 */
	public static <T> T transaction(SqlWork<T> work) throws SQLException {
		try (Connection conn = connection()) {
			return work.run(conn);
		}
	}

	public static int getBalance(String uuid) {
		WalletServer ks = ks();
		return ks != null ? ks.wallets().getBalance(uuid) : 0;
	}

	public static boolean addBalance(String uuid, int amount) {
		WalletServer ks = ks();
		return ks != null && ks.wallets().addBalance(uuid, amount);
	}

	/** Guarded debit: false when insufficient funds or the write failed. */
	public static boolean removeBalance(String uuid, int amount) {
		WalletServer ks = ks();
		return ks != null && ks.wallets().removeBalance(uuid, amount);
	}

	public static void addHistory(String uuid, String type, int amount, String otherPlayer, String description) {
		WalletServer ks = ks();
		if (ks != null) ks.history().addEntry(uuid, type, amount, otherPlayer, description);
	}

	/** Online name, then profile cache, then a shortened uuid. */
	public static String playerName(MinecraftServer server, String uuid) {
		return PlayerNames.byUuid(server, uuid);
	}
}
