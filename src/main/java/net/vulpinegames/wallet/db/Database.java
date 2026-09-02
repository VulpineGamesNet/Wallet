package net.vulpinegames.wallet.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.config.WalletConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Hikari-pooled MySQL access. Queries run synchronously on the server thread
 * (matching the script's semantics), so the timeouts here are load-bearing:
 * a dead DB must fail a trade in ~3s, not stall the tick into the watchdog.
 */
public final class Database implements AutoCloseable {
	private HikariDataSource pool;
	private volatile boolean available = false;
	private boolean mysql = false;

	/** DAOs branch on this for the few dialect-specific statements (upserts). */
	public boolean isMysql() {
		return mysql;
	}

	public boolean isAvailable() {
		return available;
	}

	private volatile boolean opening = false;

	/**
	 * Fail INSTANTLY while the economy is offline - without this guard every
	 * query on a dead DB blocks the server thread for the full 3s
	 * connectionTimeout, which reads as multi-second command lag in game.
	 */
	public Connection getConnection() throws SQLException {
		if (!available && !opening) throw new SQLException("Wallet economy is offline");
		try {
			return pool.getConnection();
		} catch (SQLException e) {
			// The pool could not hand one out - go offline so the next call
			// fails instantly instead of blocking the tick for the timeout,
			// and so reload() actually re-inits.
			if (!opening) available = false;
			throw e;
		}
	}

	public void init(WalletConfig cfg, Path sqliteFile) {
		if (pool != null) {
			pool.close();
			pool = null;
		}
		mysql = cfg.isMysql();
		HikariConfig hc = new HikariConfig();
		String where;
		if (mysql) {
			hc.setJdbcUrl("jdbc:mariadb://" + cfg.host + ":" + cfg.port + "/" + cfg.database);
			hc.setUsername(cfg.user);
			hc.setPassword(cfg.password);
			hc.setMaximumPoolSize(4);
			where = cfg.host + ":" + cfg.port + "/" + cfg.database;
		} else {
			// WAL + busy_timeout: concurrent-read friendly, and a locked write
			// waits instead of failing. One connection - SQLite is single-writer.
			hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile + "?journal_mode=WAL&busy_timeout=3000");
			hc.setMaximumPoolSize(1);
			where = sqliteFile.toString();
		}
		hc.setMinimumIdle(1);
		hc.setConnectionTimeout(3000);
		hc.setValidationTimeout(2000);
		hc.setInitializationFailTimeout(-1); // pool constructs even with DB down; economy-offline mode
		hc.setPoolName("Wallet");
		opening = true;
		try {
			pool = new HikariDataSource(hc);
			migrate();
			available = true;
			Wallet.LOGGER.info("[Wallet] Database ready ({}) at {}", mysql ? "mysql" : "sqlite", where);
		} catch (RuntimeException | SQLException e) {
			available = false;
			Wallet.LOGGER.error("[Wallet] ========================================");
			Wallet.LOGGER.error("[Wallet] FAILED TO CONNECT TO DATABASE!");
			Wallet.LOGGER.error("[Wallet] Error: {}", e.toString());
			Wallet.LOGGER.error("[Wallet] Backend: {} at {}", mysql ? "mysql" : "sqlite", where);
			Wallet.LOGGER.error("[Wallet] Economy features will be DISABLED");
			Wallet.LOGGER.error("[Wallet] ========================================");
		} finally {
			opening = false;
		}
	}

	/**
	 * Versioned migration runner. wallet_schema holds applied versions; each
	 * pending step runs once, in order, inside a transaction, and any failure
	 * is LOUD (economy goes offline) instead of half-applied and silent.
	 * Caveats: MySQL auto-commits DDL, so DDL steps must be written re-runnable
	 * (IF NOT EXISTS / guarded ALTER) - a crash between DDL and the version
	 * stamp re-runs them next boot. SQLite DDL is fully transactional.
	 * Legacy script DBs have no version table and read as version 0; migration
	 * 1 is the idempotent bootstrap, so adopting them is the same path as a
	 * fresh database.
	 */
	private static final int SCHEMA_VERSION = 1;

	private interface MigrationStep {
		void run(Connection conn) throws SQLException;
	}

	private void migrate() throws SQLException {
		try (Connection conn = getConnection()) {
			try (Statement stmt = conn.createStatement()) {
				stmt.executeUpdate("CREATE TABLE IF NOT EXISTS wallet_schema (version INT NOT NULL)");
			}
			int v = readVersion(conn);
			if (v > SCHEMA_VERSION) {
				throw new SQLException("Database schema is version " + v + " but this mod only knows "
					+ SCHEMA_VERSION + " - update the Wallet mod instead of downgrading it");
			}
			if (v < 1) applyMigration(conn, 1, this::migration1);
			// future: if (v < 2) applyMigration(conn, 2, this::migration2);
		}
	}

	private int readVersion(Connection conn) throws SQLException {
		try (Statement stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT MAX(version) AS v FROM wallet_schema")) {
			return rs.next() ? rs.getInt("v") : 0;
		}
	}

	private void applyMigration(Connection conn, int version, MigrationStep step) throws SQLException {
		Wallet.LOGGER.info("[Wallet] Applying schema migration {}", version);
		boolean auto = conn.getAutoCommit();
		conn.setAutoCommit(false);
		try {
			step.run(conn);
			try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO wallet_schema (version) VALUES (?)")) {
				stmt.setInt(1, version);
				stmt.executeUpdate();
			}
			conn.commit();
		} catch (SQLException | RuntimeException e) {
			try {
				conn.rollback();
			} catch (SQLException ignored) {
			}
			throw new SQLException("Schema migration " + version + " failed", e);
		} finally {
			conn.setAutoCommit(auto);
		}
	}

	/**
	 * Migration 1: the bootstrap, idempotent (IF NOT EXISTS + guarded ALTERs).
	 * Migrating a KubeJS-script-era database (kubeshop_* tables) is a one-off
	 * operational step, NOT the mod's job: run
	 * scripts/migrate-kubeshop-to-wallet.sql against the DB BEFORE the mod's
	 * first boot - afterwards this bootstrap sees the renamed tables and only
	 * fills in what is missing. Booting first instead creates empty wallet_*
	 * tables and the rename script then refuses to clobber them.
	 */
	private void migration1(Connection conn) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate(mysql
				? "CREATE TABLE IF NOT EXISTS wallet_balances (" +
				  "  uuid VARCHAR(36) PRIMARY KEY," +
				  "  balance INT NOT NULL DEFAULT 0," +
				  "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
				  ")"
				: "CREATE TABLE IF NOT EXISTS wallet_balances (" +
				  "  uuid VARCHAR(36) PRIMARY KEY," +
				  "  balance INT NOT NULL DEFAULT 0," +
				  "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
				  ")");
			stmt.executeUpdate(
				"CREATE TABLE IF NOT EXISTS wallet_shops (" +
				"  sign_key VARCHAR(128) PRIMARY KEY," +
				"  owner_uuid VARCHAR(36) NOT NULL," +
				"  chest_pos VARCHAR(128) NOT NULL," +
				"  shop_type VARCHAR(4) NOT NULL," +
				"  price INT NOT NULL," +
				"  item_template TEXT NOT NULL," +
				"  is_server TINYINT(1) NOT NULL DEFAULT 0," +
				"  is_infinite TINYINT(1) NOT NULL DEFAULT 0," +
				"  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
				")");
			addColumnQuietly(stmt, "ALTER TABLE wallet_shops ADD COLUMN is_server TINYINT(1) NOT NULL DEFAULT 0");
			addColumnQuietly(stmt, "ALTER TABLE wallet_shops ADD COLUMN is_infinite TINYINT(1) NOT NULL DEFAULT 0");
			// Plots (wallet_plots) belong to the WalletPlots addon, which owns
			// their table and migrations - core never touches them.

			if (mysql) {
				stmt.executeUpdate(
					"CREATE TABLE IF NOT EXISTS wallet_history (" +
					"  id INT AUTO_INCREMENT PRIMARY KEY," +
					"  player_uuid VARCHAR(36) NOT NULL," +
					"  type VARCHAR(20) NOT NULL," +
					"  amount INT NOT NULL," +
					"  other_player VARCHAR(64)," +
					"  description TEXT," +
					"  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
					"  INDEX idx_player_uuid (player_uuid)," +
					"  INDEX idx_created_at (created_at)" +
					")");
			} else {
				// datetime('now','localtime'): SQLite's CURRENT_TIMESTAMP is UTC but
				// getTimestamp parses the text in the JVM zone - store local instead.
				stmt.executeUpdate(
					"CREATE TABLE IF NOT EXISTS wallet_history (" +
					"  id INTEGER PRIMARY KEY AUTOINCREMENT," +
					"  player_uuid VARCHAR(36) NOT NULL," +
					"  type VARCHAR(20) NOT NULL," +
					"  amount INT NOT NULL," +
					"  other_player VARCHAR(64)," +
					"  description TEXT," +
					"  created_at TIMESTAMP DEFAULT (datetime('now','localtime'))" +
					")");
				stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_uuid ON wallet_history (player_uuid)");
				stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_created_at ON wallet_history (created_at)");
			}
		}
	}

	private static void addColumnQuietly(Statement stmt, String sql) {
		try {
			stmt.executeUpdate(sql);
		} catch (SQLException ignored) {
			// column already exists
		}
	}

	@Override
	public void close() {
		available = false;
		if (pool != null) pool.close();
	}
}
