package net.vulpinegames.wallet.coin;

import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Coin denominations, largest to smallest for the greedy algorithms. The ids
 * are the CustomModelData values the modern builds use; on 1.12.2 they are
 * stored as a plain int NBT tag of the same name (see Coins).
 */
public final class CoinDenomination {
	public static final List<CoinDenomination> ALL = Collections.unmodifiableList(Arrays.asList(
		new CoinDenomination(10000, 710000, "Coin", "Worth $10,000", TextFormatting.GOLD),
		new CoinDenomination(1000, 719999, "Coin", "Worth $1,000", TextFormatting.LIGHT_PURPLE),
		new CoinDenomination(100, 719100, "Coin", "Worth $100", TextFormatting.BLUE),
		new CoinDenomination(10, 719010, "Coin", "Worth $10", TextFormatting.GREEN),
		new CoinDenomination(1, 719001, "Coin", "Worth $1", TextFormatting.WHITE)
	));

	public final int value;
	public final int customModelData;
	public final String name;
	public final String lore;
	public final TextFormatting color;

	private CoinDenomination(int value, int customModelData, String name, String lore, TextFormatting color) {
		this.value = value;
		this.customModelData = customModelData;
		this.name = name;
		this.lore = lore;
		this.color = color;
	}

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
