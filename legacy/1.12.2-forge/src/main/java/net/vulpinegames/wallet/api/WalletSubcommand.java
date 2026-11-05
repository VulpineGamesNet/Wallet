package net.vulpinegames.wallet.api;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.List;

/**
 * A {@code /wallet <name> ...} branch owned by an addon. 1.12.2 has no
 * Brigadier, so an addon cannot merge its own literal into the /wallet tree
 * the way the modern builds do; it registers one of these through
 * {@link WalletApi#registerSubcommand} and Wallet's command dispatches to it.
 */
public interface WalletSubcommand {
	/** args excludes the subcommand name itself: {@code /wallet ah sell 5} -> {"sell", "5"}. */
	void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;

	/** Same argument convention as {@link #execute}; the last element is the word being typed. */
	default List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args) {
		return Collections.emptyList();
	}

	/** Whether the sender may see and run this branch at all. */
	default boolean canUse(MinecraftServer server, ICommandSender sender) {
		return true;
	}
}
