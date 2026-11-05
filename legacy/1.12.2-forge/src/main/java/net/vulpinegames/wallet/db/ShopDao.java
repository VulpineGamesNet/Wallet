package net.vulpinegames.wallet.db;

import net.vulpinegames.wallet.Wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sign shop persistence - same table and SQL as the modern builds. */
public final class ShopDao {
	public static final class Shop {
		public final String owner;
		public final String chestPos;
		public final String type;
		public final int price;
		public final String itemTemplate;
		public final boolean isServer;
		public final boolean isInfinite;

		public Shop(String owner, String chestPos, String type, int price,
		            String itemTemplate, boolean isServer, boolean isInfinite) {
			this.owner = owner;
			this.chestPos = chestPos;
			this.type = type;
			this.price = price;
			this.itemTemplate = itemTemplate;
			this.isServer = isServer;
			this.isInfinite = isInfinite;
		}

		public boolean isBuy() {
			return "BUY".equals(type);
		}
	}

	private final Database db;

	public ShopDao(Database db) {
		this.db = db;
	}

	/** Leaves is_server/is_infinite alone on update, so admin flags survive a re-save. */
	public boolean saveShop(String signKey, Shop shop) {
		String sql = db.isMysql()
			? "INSERT INTO wallet_shops (sign_key, owner_uuid, chest_pos, shop_type, price, item_template) " +
			  "VALUES (?, ?, ?, ?, ?, ?) " +
			  "ON DUPLICATE KEY UPDATE owner_uuid = ?, chest_pos = ?, shop_type = ?, price = ?, item_template = ?"
			: "INSERT INTO wallet_shops (sign_key, owner_uuid, chest_pos, shop_type, price, item_template) " +
			  "VALUES (?, ?, ?, ?, ?, ?) " +
			  "ON CONFLICT(sign_key) DO UPDATE SET owner_uuid = excluded.owner_uuid, chest_pos = excluded.chest_pos, " +
			  "shop_type = excluded.shop_type, price = excluded.price, item_template = excluded.item_template";
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, signKey);
			stmt.setString(2, shop.owner);
			stmt.setString(3, shop.chestPos);
			stmt.setString(4, shop.type);
			stmt.setInt(5, shop.price);
			stmt.setString(6, shop.itemTemplate);
			if (db.isMysql()) {
				stmt.setString(7, shop.owner);
				stmt.setString(8, shop.chestPos);
				stmt.setString(9, shop.type);
				stmt.setInt(10, shop.price);
				stmt.setString(11, shop.itemTemplate);
			}
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] saveShop error: {}", e.toString());
			return false;
		}
	}

	public void removeShop(String signKey) {
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement("DELETE FROM wallet_shops WHERE sign_key = ?")) {
			stmt.setString(1, signKey);
			stmt.executeUpdate();
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] removeShop error: {}", e.toString());
		}
	}

	public boolean setShopFlags(String signKey, boolean isServer, boolean isInfinite) {
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement(
			     "UPDATE wallet_shops SET is_server = ?, is_infinite = ? WHERE sign_key = ?")) {
			stmt.setInt(1, isServer ? 1 : 0);
			stmt.setInt(2, isInfinite ? 1 : 0);
			stmt.setString(3, signKey);
			return stmt.executeUpdate() > 0;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] setShopFlags error: {}", e.toString());
			return false;
		}
	}

	public Shop getShop(String signKey) {
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM wallet_shops WHERE sign_key = ?")) {
			stmt.setString(1, signKey);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) return readShop(rs);
			}
			return null;
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] getShop error: {}", e.toString());
			return null;
		}
	}

	public Map<String, Shop> getAllShops() {
		Map<String, Shop> shops = new LinkedHashMap<>();
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM wallet_shops");
		     ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) shops.put(rs.getString("sign_key"), readShop(rs));
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] getAllShops error: {}", e.toString());
		}
		return shops;
	}

	/** Null when the query failed - callers on a known shop chest fail closed. */
	public List<String> getShopsByChestPos(String chestPos) {
		List<String> results = new ArrayList<>();
		try (Connection conn = db.getConnection();
		     PreparedStatement stmt = conn.prepareStatement("SELECT sign_key FROM wallet_shops WHERE chest_pos = ?")) {
			stmt.setString(1, chestPos);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) results.add(rs.getString("sign_key"));
			}
		} catch (SQLException e) {
			Wallet.LOGGER.error("[Wallet] getShopsByChestPos error: {}", e.toString());
			return null;
		}
		return results;
	}

	private static Shop readShop(ResultSet rs) throws SQLException {
		return new Shop(
			rs.getString("owner_uuid"),
			rs.getString("chest_pos"),
			rs.getString("shop_type"),
			rs.getInt("price"),
			rs.getString("item_template"),
			rs.getInt("is_server") == 1,
			rs.getInt("is_infinite") == 1);
	}
}
