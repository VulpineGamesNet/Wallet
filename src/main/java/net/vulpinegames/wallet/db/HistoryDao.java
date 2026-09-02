package net.vulpinegames.wallet.db;

import net.vulpinegames.wallet.Wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Straight port of the script's history persistence, 50-entry cap included. */
public final class HistoryDao {
	public static final int MAX_HISTORY_PER_PLAYER = 50;

	public record Entry(String type, int amount, String other, String desc, long time) {}

	private final Database db;

	public HistoryDao(Database db) {
		this.db = db;
	}

	public void addEntry(String playerUuid, String type, int amount, String otherPlayer, String description) {
		try (Connection conn = db.getConnection()) {
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
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] addHistoryEntry error: {}", e.toString());
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
					java.sql.Timestamp ts = rs.getTimestamp("created_at");
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
