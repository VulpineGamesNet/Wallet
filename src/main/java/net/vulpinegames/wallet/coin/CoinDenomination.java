package net.vulpinegames.wallet.coin;

import java.util.List;

/**
 * Coin denominations, ordered largest to smallest for the greedy algorithms.
 * The CustomModelData values are a contract with the deployed resource pack
 * and with every coin already sitting in a player chest - never change them.
 */
public record CoinDenomination(int value, int customModelData, String name, String lore, String color) {
	public static final List<CoinDenomination> ALL = List.of(
		new CoinDenomination(10000, 710000, "Coin", "Worth $10,000", "gold"),
		new CoinDenomination(1000, 719999, "Coin", "Worth $1,000", "light_purple"),
		new CoinDenomination(100, 719100, "Coin", "Worth $100", "blue"),
		new CoinDenomination(10, 719010, "Coin", "Worth $10", "green"),
		new CoinDenomination(1, 719001, "Coin", "Worth $1", "white")
	);

	public static CoinDenomination byCustomModelData(int cmd) {
		for (CoinDenomination d : ALL) {
			if (d.customModelData == cmd) return d;
		}
		return null;
	}

	public static CoinDenomination byValue(int value) {
		for (CoinDenomination d : ALL) {
			if (d.value == value) return d;
		}
		return null;
	}
}
