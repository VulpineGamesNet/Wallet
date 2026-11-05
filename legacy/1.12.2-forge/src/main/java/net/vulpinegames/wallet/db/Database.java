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
 * Hikari-pooled database access, same schema and migration runner as the
 * modern builds so a 1.12.2 database can be read by them and vice versa.
 * Queries run synchronously on the server thread, so the timeouts are
 * load-bearing: a dead DB must fail a trade in ~3s, not stall the tick.
 *
 * The JDBC drivers are bundled (shaded and, for MariaDB, relocated) into this
 * jar, so the driver class is named explicitly instead of relying on
 * DriverManager discovery through Forge's LaunchClassLoader.
 */
public final class Database implements AutoCloseable {
	private HikariDataSource pool;
	private volatile boolean available = false;
	private boolean mysql = false;
	private volatile boolean opening = false;

	public boolean isMysql() {
		return mysql;
	}

	public boolean isAvailable() {
		return available;
	}

	/** Fail INSTANTLY while the economy is offline instead of blocking the tick. */
	public Connection getConnection() throws SQLException {
		if (!available && !opening) throw new SQLException("Wallet economy is offline");
		try {
			return pool.getConnection();
		} catch (SQLException e) {
			if (!opening) available = false;
			throw e;
		}
	}

	public void init(WalletConfig cfg, Path sqliteFile) {
		if (pool != null) {
			pool.close();
			pool = null;
		}
		available = false;
		mysql = cfg.isMysql();
		String where = mysql ? cfg.host + ":" + cfg.port + "/" + cfg.database : String.valueOf(sqliteFile);
		if (cfg.loadFailed) {
			Wallet.LOGGER.error("[Wallet] wallet.json is unreadable - economy stays OFFLINE (fix it, then /wallet admin reload)");
			return;
		}
		HikariConfig hc = new HikariConfig();
		if (mysql) {
			hc.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
			hc.setJdbcUrl("jdbc:mariadb://" + cfg.host + ":" + cfg.port + "/" + cfg.database);
			hc.setUsername(cfg.user);
			hc.setPassword(cfg.password);
			hc.setMaximumPoolSize(4);
		} else {
			// WAL + busy_timeout: a locked write waits instead of failing.
			// One connection - SQLite is single-writer.
			hc.setDriverClassName(org.sqlite.JDBC.class.getName());
			hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile + "?journal_mode=WAL&busy_timeout=3000");
			hc.setMaximumPoolSize(1);
		}
		hc.setMinimumIdle(1);
		hc.setConnectionTimeout(3000);
		hc.setValidationTimeout(2000);
		hc.setInitializationFailTimeout(-1); // pool constructs even with DB down
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
	 * Versioned migration runner, identical to the modern builds: wallet_schema
	 * holds applied versions, each pending step runs once inside a transaction.
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

	/** Migration 1: the bootstrap, idempotent (IF NOT EXISTS + guarded ALTERs). */
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
