package net.vulpinegames.wallet.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Component shortcuts named after the KubeJS helpers, so ports read 1:1. */
public final class Msg {
	private Msg() {}

	public static MutableComponent empty() {
		return Component.empty();
	}

	public static MutableComponent literal(String text) {
		return Component.literal(text);
	}

	public static MutableComponent gold(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GOLD);
	}

	public static MutableComponent red(String text) {
		return Component.literal(text).withStyle(ChatFormatting.RED);
	}

	public static MutableComponent green(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GREEN);
	}

	public static MutableComponent yellow(String text) {
		return Component.literal(text).withStyle(ChatFormatting.YELLOW);
	}

	public static MutableComponent gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	public static MutableComponent white(String text) {
		return Component.literal(text).withStyle(ChatFormatting.WHITE);
	}

	public static MutableComponent aqua(String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA);
	}

	public static MutableComponent lightPurple(String text) {
		return Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** "ae2:quartz_dust" -> "Ae2:quartz Dust", the script's plain-text item names. */
	public static String prettyId(String id) {
		String simple = id.replaceFirst("^[a-z0-9_.-]+:", "").replace('_', ' ');
		StringBuilder out = new StringBuilder();
		for (String word : simple.split(" ")) {
			if (!out.isEmpty()) out.append(' ');
			if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	/** The script's .clickRunCommand(...).hover(...) chain. */
	public static MutableComponent clickable(MutableComponent text, String command, Component hover) {
		return text.withStyle(style -> style
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
	}

	public static String formatBalance(int amount) {
		return "$" + amount;
	}

	/** "MM/dd HH:mm", same as the script's formatTimestamp. */
	public static String formatTimestamp(long timestamp) {
		return new SimpleDateFormat("MM/dd HH:mm").format(new Date(timestamp));
	}
}
