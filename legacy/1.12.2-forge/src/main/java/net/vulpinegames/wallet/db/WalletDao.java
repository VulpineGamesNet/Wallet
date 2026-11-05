package net.vulpinegames.wallet.db;

import net.vulpinegames.wallet.Wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Wallet balances - same SQL as the modern builds. */
public final class WalletDao {
	public static final String SERVER_UUID = "00000000-0000-0000-0000-000000000000";
	private static final int STARTING_BALANCE = 0;

	private final Database db;

	public WalletDao(Database db) {
		this.db = db;
	}

	public int getBalance(String uuid) {
		try (Connection conn = db.getConnection()) {
			try (PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM wallet_balances WHERE uuid = ?")) {
				stmt.setString(1, uuid);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) return rs.getInt("balance");
				}
			}
			try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO wallet_balances (uuid, balance) VALUES (?, ?)")) {
				stmt.setString(1, uuid);
				stmt.setInt(2, STARTING_BALANCE);
				stmt.executeUpdate();
			}
			return STARTING_BALANCE;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] getBalance error: {}", e.toString());
			return STARTING_BALANCE;
		}
	}

	public boolean setBalance(String uuid, int amount) {
		return upsertBalance(uuid, amount, false);
	}

	public boolean addBalance(String uuid, int amount) {
		return upsertBalance(uuid, amount, true);
	}

	private boolean upsertBalance(String uuid, int amount, boolean add) {
		if (amount < 0) return false;
		String sql = db.isMysql()
			? "INSERT INTO wallet_balances (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = "
			  + (add ? "balance + ?" : "?")
			: "INSERT INTO wallet_balances (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = "
			  + (add ? "balance + excluded.balance" : "excluded.balance");
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, uuid);
			stmt.setInt(2, amount);
			if (db.isMysql()) stmt.setInt(3, amount);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] {} error: {}", add ? "addBalance" : "setBalance", e.toString());
			return false;
		}
	}

	/** Guarded subtract: fails (returns false) rather than going negative. */
	public boolean removeBalance(String uuid, int amount) {
		if (amount < 0) return false;
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(
			     "UPDATE wallet_balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
			stmt.setInt(1, amount);
			stmt.setString(2, uuid);
			stmt.setInt(3, amount);
			return stmt.executeUpdate() > 0;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] removeBalance error: {}", e.toString());
			return false;
		}
	}

	public boolean removeBalance(Connection conn, String uuid, int amount) throws SQLException {
		if (amount <= 0) throw new IllegalArgumentException("Debit must be positive");
		try (PreparedStatement stmt = conn.prepareStatement(
			"UPDATE wallet_balances SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
			stmt.setInt(1, amount);
			stmt.setString(2, uuid);
			stmt.setInt(3, amount);
			return stmt.executeUpdate() == 1;
		}
	}

	public void addBalance(Connection conn, String uuid, int amount) throws SQLException {
		if (amount <= 0) throw new IllegalArgumentException("Credit must be positive");
		String insert = db.isMysql()
			? "INSERT INTO wallet_balances (uuid, balance) VALUES (?, 0) ON DUPLICATE KEY UPDATE uuid = uuid"
			: "INSERT INTO wallet_balances (uuid, balance) VALUES (?, 0) ON CONFLICT(uuid) DO NOTHING";
		try (PreparedStatement stmt = conn.prepareStatement(insert)) {
			stmt.setString(1, uuid);
			stmt.executeUpdate();
		}
		try (PreparedStatement stmt = conn.prepareStatement(
			"UPDATE wallet_balances SET balance = balance + ? WHERE uuid = ? AND balance <= ?")) {
			stmt.setInt(1, amount);
			stmt.setString(2, uuid);
			stmt.setInt(3, Integer.MAX_VALUE - amount);
			if (stmt.executeUpdate() != 1) throw new SQLException("Wallet balance would overflow");
		}
	}

	public static final class TopEntry {
		public final String uuid;
		public final int balance;

		TopEntry(String uuid, int balance) {
			this.uuid = uuid;
			this.balance = balance;
		}
	}

	public List<TopEntry> top(int limit) {
		List<TopEntry> out = new ArrayList<>();
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(
			     "SELECT uuid, balance FROM wallet_balances WHERE uuid <> ? ORDER BY balance DESC LIMIT ?")) {
			stmt.setString(1, SERVER_UUID);
			stmt.setInt(2, limit);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) out.add(new TopEntry(rs.getString("uuid"), rs.getInt("balance")));
			}
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] top error: {}", e.toString());
		}
		return out;
	}
}
