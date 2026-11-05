package net.vulpinegames.wallet.db;

import net.vulpinegames.wallet.Wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Transaction history, 50-entry cap per player, same SQL as the modern builds. */
public final class HistoryDao {
	public static final int MAX_HISTORY_PER_PLAYER = 50;

	public static final class Entry {
		public final String type;
		public final int amount;
		public final String other;
		public final String desc;
		public final long time;

		Entry(String type, int amount, String other, String desc, long time) {
			this.type = type;
			this.amount = amount;
			this.other = other;
			this.desc = desc;
			this.time = time;
		}
	}

	private final Database db;

	public HistoryDao(Database db) {
		this.db = db;
	}

	public void addEntry(String playerUuid, String type, int amount, String otherPlayer, String description) {
		try (Connection conn = db.getConnection()) {
			addEntry(conn, playerUuid, type, amount, otherPlayer, description);
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] addHistoryEntry error: {}", e.toString());
		}
	}

	public void addEntry(Connection conn, String playerUuid, String type, int amount,
	                     String otherPlayer, String description) throws SQLException {
		try (PreparedStatement stmt = conn.prepareStatement(
			"INSERT INTO wallet_history (player_uuid, type, amount, other_player, description) VALUES (?, ?, ?, ?, ?)")) {
			stmt.setString(1, playerUuid);
			stmt.setString(2, type);
			stmt.setInt(3, amount);
			if (otherPlayer != null) stmt.setString(4, otherPlayer);
			else stmt.setNull(4, Types.VARCHAR);
			if (description != null) stmt.setString(5, description);
			else stmt.setNull(5, Types.VARCHAR);
			stmt.executeUpdate();
		}
		try (PreparedStatement stmt = conn.prepareStatement(
			"DELETE FROM wallet_history WHERE player_uuid = ? AND id NOT IN (" +
			"  SELECT id FROM (SELECT id FROM wallet_history WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?) AS t" +
			")")) {
			stmt.setString(1, playerUuid);
			stmt.setString(2, playerUuid);
			stmt.setInt(3, MAX_HISTORY_PER_PLAYER);
			stmt.executeUpdate();
		}
	}

	public List<Entry> getHistory(String playerUuid) {
		List<Entry> history = new ArrayList<>();
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(
			     "SELECT * FROM wallet_history WHERE player_uuid = ? ORDER BY created_at DESC LIMIT ?")) {
			stmt.setString(1, playerUuid);
			stmt.setInt(2, MAX_HISTORY_PER_PLAYER);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					Timestamp ts = rs.getTimestamp("created_at");
					history.add(new Entry(
						rs.getString("type"),
						rs.getInt("amount"),
						rs.getString("other_player"),
						rs.getString("description"),
						ts != null ? ts.getTime() : 0L));
				}
			}
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] getHistory error: {}", e.toString());
		}
		return history;
	}
}
