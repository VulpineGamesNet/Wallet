package net.vulpinegames.wallet.chat;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Text component shortcuts with the same names as the modern builds, so the ports read 1:1. */
public final class Msg {
	private Msg() {}

	public static ITextComponent empty() {
		return new TextComponentString("");
	}

	public static ITextComponent literal(String text) {
		return new TextComponentString(text);
	}

	public static ITextComponent colored(String text, TextFormatting color) {
		ITextComponent c = new TextComponentString(text);
		c.setStyle(new Style().setColor(color));
		return c;
	}

	public static ITextComponent gold(String text) {
		return colored(text, TextFormatting.GOLD);
	}

	public static ITextComponent red(String text) {
		return colored(text, TextFormatting.RED);
	}

	public static ITextComponent green(String text) {
		return colored(text, TextFormatting.GREEN);
	}

	public static ITextComponent yellow(String text) {
		return colored(text, TextFormatting.YELLOW);
	}

	public static ITextComponent gray(String text) {
		return colored(text, TextFormatting.GRAY);
	}

	public static ITextComponent white(String text) {
		return colored(text, TextFormatting.WHITE);
	}

	public static ITextComponent aqua(String text) {
		return colored(text, TextFormatting.AQUA);
	}

	public static ITextComponent lightPurple(String text) {
		return colored(text, TextFormatting.LIGHT_PURPLE);
	}

	/** "minecraft:quartz_block" -> "Quartz Block", the plain-text item names in history. */
	public static String prettyId(String id) {
		String simple = id.replaceFirst("^[a-z0-9_.-]+:", "").replace('_', ' ');
		StringBuilder out = new StringBuilder();
		for (String word : simple.split(" ")) {
			if (out.length() > 0) out.append(' ');
			if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	public static ITextComponent clickable(ITextComponent text, String command, ITextComponent hover) {
		text.getStyle()
			.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
			.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
		return text;
	}

	public static String formatBalance(int amount) {
		return "$" + amount;
	}

	/** "MM/dd HH:mm", same as the modern builds. */
	public static String formatTimestamp(long timestamp) {
		return new SimpleDateFormat("MM/dd HH:mm").format(new Date(timestamp));
	}
}
