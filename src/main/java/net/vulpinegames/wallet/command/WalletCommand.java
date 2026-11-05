package net.vulpinegames.wallet.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.coin.CoinDenomination;
import net.vulpinegames.wallet.coin.Coins;
import net.vulpinegames.wallet.db.HistoryDao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The /wallet tree, ported from the script command for command. */
public final class WalletCommand {
	private WalletCommand() {}

	static WalletServer ks() {
		return Wallet.active();
	}

	/** Player behind the command, or null (with the script's failure message sent). */
	static ServerPlayer playerOrFail(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Msg.red("This command can only be used by players"));
		}
		return player;
	}

	static GameProfile firstProfileOrFail(CommandContext<CommandSourceStack> ctx, String arg) {
		try {
			Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, arg);
			if (!profiles.isEmpty()) return profiles.iterator().next();
		} catch (Exception ignored) {
		}
		ctx.getSource().sendFailure(Msg.red("No player found"));
		return null;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wallet")
			// The script also sendFailure()d inside requires(); a Java predicate runs
			// during tab-completion sync too, so stay silent here - the executes
			// handlers answer "economy offline" when it matters. Admins keep the
			// tree while the DB is down, so /wallet admin reload can revive it.
			.requires(src -> ks() != null && (ks().database().isAvailable() || src.hasPermission(2)))
			.executes(ctx -> {
				CommandSourceStack src = ctx.getSource();
				src.sendSystemMessage(Msg.gold("=== Wallet Commands ==="));
				src.sendSystemMessage(Msg.yellow("/wallet balance").append(Msg.gray(" - Check your balance")));
				src.sendSystemMessage(Msg.yellow("/wallet pay <player> <amount>").append(Msg.gray(" - Send money")));
				src.sendSystemMessage(Msg.yellow("/wallet withdraw <amount>").append(Msg.gray(" - Withdraw as coins (auto-split)")));
				src.sendSystemMessage(Msg.yellow("/wallet withdraw <amount> <denom>").append(Msg.gray(" - Withdraw as specific coin (1/10/100/1000)")));
				src.sendSystemMessage(Msg.yellow("/wallet deposit").append(Msg.gray(" - Deposit all coins")));
				src.sendSystemMessage(Msg.yellow("/wallet deposit <amount>").append(Msg.gray(" - Deposit specific value")));
				src.sendSystemMessage(Msg.yellow("/wallet history").append(Msg.gray(" - Transaction history")));
				src.sendSystemMessage(Msg.yellow("/wallet shop help").append(Msg.gray(" - Shop creation guide")));
				if (Wallet.xplat().isModLoaded("walletauction")) {
					src.sendSystemMessage(Msg.yellow("/wallet ah").append(Msg.gray(" - Player auction house")));
				}
				if (src.hasPermission(2)) {
					src.sendSystemMessage(Msg.red("/wallet admin").append(Msg.gray(" - Admin commands")));
				}
				return 1;
			})

			.then(Commands.literal("balance")
				.executes(ctx -> {
					ServerPlayer player = playerOrFail(ctx);
					if (player == null) return 0;
					int bal = ks().wallets().getBalance(player.getStringUUID());
					player.sendSystemMessage(Msg.empty()
						.append(Msg.gold("Your balance: "))
						.append(Msg.green(Msg.formatBalance(bal))));
					return 1;
				}))

			.then(Commands.literal("help")
				.executes(ctx -> {
					CommandSourceStack src = ctx.getSource();
					src.sendSystemMessage(Msg.gold("=== Wallet Commands ==="));
					src.sendSystemMessage(Msg.yellow("/wallet balance").append(Msg.gray(" - Check your balance")));
					src.sendSystemMessage(Msg.yellow("/wallet pay <player> <amount>").append(Msg.gray(" - Send money")));
					src.sendSystemMessage(Msg.gold("--- Coin Commands ---"));
					src.sendSystemMessage(Msg.yellow("/wallet withdraw <amount>").append(Msg.gray(" - Withdraw as coins (auto-split)")));
					src.sendSystemMessage(Msg.yellow("/wallet withdraw <amount> <denom>").append(Msg.gray(" - Withdraw as specific coin")));
					src.sendSystemMessage(Msg.gray("  Denominations: 1, 10, 100, 1000, 10000"));
					src.sendSystemMessage(Msg.gray("  Example: /wallet withdraw 100 10 = 10x $10 coins"));
					src.sendSystemMessage(Msg.yellow("/wallet deposit").append(Msg.gray(" - Deposit all coins in inventory")));
					src.sendSystemMessage(Msg.yellow("/wallet deposit <amount>").append(Msg.gray(" - Deposit specific value from coins")));
					src.sendSystemMessage(Msg.gold("--- Other ---"));
					src.sendSystemMessage(Msg.yellow("/wallet history").append(Msg.gray(" - Transaction history")));
					src.sendSystemMessage(Msg.yellow("/wallet shop help").append(Msg.gray(" - Shop creation guide")));
				if (Wallet.xplat().isModLoaded("walletauction")) {
					src.sendSystemMessage(Msg.yellow("/wallet ah").append(Msg.gray(" - Player auction house")));
				}
					if (src.hasPermission(2)) {
						src.sendSystemMessage(Msg.red("/wallet admin").append(Msg.gray(" - Admin commands")));
					}
					return 1;
				}))

			.then(Commands.literal("shop")
				.executes(ctx -> {
					CommandSourceStack src = ctx.getSource();
					src.sendSystemMessage(Msg.gold("=== Shop Commands ==="));
					src.sendSystemMessage(Msg.yellow("/wallet shop help").append(Msg.gray(" - Shop creation guide")));
					return 1;
				})
				.then(Commands.literal("help")
					.executes(ctx -> {
						CommandSourceStack src = ctx.getSource();
						src.sendSystemMessage(Msg.gold("=== Shop Creation Guide ==="));
						src.sendSystemMessage(Msg.white("1. Place a chest with items to sell/buy"));
						src.sendSystemMessage(Msg.white("2. Place a wall sign on the chest"));
						src.sendSystemMessage(Msg.white("3. Write on the sign:"));
						src.sendSystemMessage(Msg.gray("   Line 1: ").append(Msg.yellow("[BUY]")).append(Msg.gray(" or ")).append(Msg.yellow("[SELL]")));
						src.sendSystemMessage(Msg.gray("   Line 2-3: Description (optional)"));
						src.sendSystemMessage(Msg.gray("   Line 4: Price (e.g. ").append(Msg.green("10")).append(Msg.gray(")")));
						src.sendSystemMessage(Msg.white("4. Shift+right-click the sign to create"));
						src.sendSystemMessage(Msg.gold("---"));
						src.sendSystemMessage(Msg.yellow("[BUY]").append(Msg.gray(" = Players buy FROM the shop")));
						src.sendSystemMessage(Msg.yellow("[SELL]").append(Msg.gray(" = Players sell TO the shop")));
						src.sendSystemMessage(Msg.gray("Right-click shop sign to buy/sell"));
						src.sendSystemMessage(Msg.gray("Shift+click existing shop to see info"));
						src.sendSystemMessage(Msg.gray("Break sign or chest to remove shop"));
						return 1;
					})))

			.then(Commands.literal("pay")
				.then(Commands.argument("recipient", GameProfileArgument.gameProfile())
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(WalletCommand::pay))))

			.then(Commands.literal("history")
				.executes(WalletCommand::history))

			// Optional: hidden and refused unless walletTop is set in the config.
			// requires() re-evaluates on /reload, so flipping the flag needs one.
			.then(Commands.literal("top")
				.requires(src -> ks() != null && ks().config().walletTop)
				.executes(WalletCommand::top))

			.then(Commands.literal("withdraw")
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
					.executes(ctx -> withdraw(ctx, 0))
					.then(Commands.argument("denomination", IntegerArgumentType.integer(1))
						.executes(ctx -> withdraw(ctx, IntegerArgumentType.getInteger(ctx, "denomination"))))))

			.then(Commands.literal("deposit")
				.executes(ctx -> deposit(ctx, -1))
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
					.executes(ctx -> deposit(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))

			.then(AdminCommands.build())
		);
	}

	private static int pay(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer sender = playerOrFail(ctx);
		if (sender == null) return 0;

		int payAmount = IntegerArgumentType.getInteger(ctx, "amount");
		if (payAmount <= 0) {
			ctx.getSource().sendFailure(Msg.red("Amount must be positive"));
			return 0;
		}

		GameProfile recipient = firstProfileOrFail(ctx, "recipient");
		if (recipient == null) return 0;

		String senderUuid = sender.getStringUUID();
		String recipientUuid = recipient.getId().toString();
		String recipientName = recipient.getName();

		if (senderUuid.equals(recipientUuid)) {
			ctx.getSource().sendFailure(Msg.red("You cannot pay yourself"));
			return 0;
		}

		int senderBalance = ks().wallets().getBalance(senderUuid);
		if (senderBalance < payAmount) {
			ctx.getSource().sendFailure(Msg.empty()
				.append(Msg.red("Insufficient funds. You have "))
				.append(Msg.yellow(Msg.formatBalance(senderBalance))));
			return 0;
		}

		if (!ks().wallets().removeBalance(senderUuid, payAmount)) {
			ctx.getSource().sendFailure(Msg.red("Payment failed - nothing was sent"));
			return 0;
		}
		if (!ks().wallets().addBalance(recipientUuid, payAmount)) {
			ks().wallets().addBalance(senderUuid, payAmount);
			ctx.getSource().sendFailure(Msg.red("Payment failed - nothing was sent"));
			return 0;
		}

		ks().history().addEntry(senderUuid, "pay_sent", payAmount, recipientName, null);
		ks().history().addEntry(recipientUuid, "pay_received", payAmount, sender.getName().getString(), null);

		sender.sendSystemMessage(Msg.empty()
			.append(Msg.gold("Sent "))
			.append(Msg.green(Msg.formatBalance(payAmount)))
			.append(Msg.gold(" to "))
			.append(Msg.yellow(recipientName)));

		ServerPlayer onlineRecipient = ctx.getSource().getServer().getPlayerList().getPlayer(recipient.getId());
		if (onlineRecipient != null) {
			onlineRecipient.sendSystemMessage(Msg.empty()
				.append(Msg.gold("You received "))
				.append(Msg.green(Msg.formatBalance(payAmount)))
				.append(Msg.gold(" from "))
				.append(Msg.yellow(sender.getName().getString())));
		}
		return 1;
	}

	private static int top(CommandContext<CommandSourceStack> ctx) {
		if (!ks().config().walletTop) {
			ctx.getSource().sendFailure(Msg.red("The leaderboard is disabled on this server"));
			return 0;
		}
		List<net.vulpinegames.wallet.db.WalletDao.TopEntry> top = ks().wallets().top(10);
		ctx.getSource().sendSystemMessage(Msg.gold("=== Richest Players ==="));
		if (top.isEmpty()) {
			ctx.getSource().sendSystemMessage(Msg.gray("No wallets yet"));
			return 1;
		}
		int rank = 1;
		for (net.vulpinegames.wallet.db.WalletDao.TopEntry entry : top) {
			ctx.getSource().sendSystemMessage(Msg.empty()
				.append(Msg.gold("#" + rank + " "))
				.append(Msg.yellow(net.vulpinegames.wallet.world.PlayerNames.byUuid(
					ctx.getSource().getServer(), entry.uuid())))
				.append(Msg.gray(" - "))
				.append(Msg.green(Msg.formatBalance(entry.balance()))));
			rank++;
		}
		return 1;
	}

	private static int history(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = playerOrFail(ctx);
		if (player == null) return 0;

		List<HistoryDao.Entry> history = ks().history().getHistory(player.getStringUUID());
		ctx.getSource().sendSystemMessage(Msg.gold("=== Transaction History ==="));
		if (history.isEmpty()) {
			ctx.getSource().sendSystemMessage(Msg.gray("No transactions yet"));
			return 1;
		}

		int toShow = Math.min(history.size(), 10);
		for (int i = 0; i < toShow; i++) {
			ctx.getSource().sendSystemMessage(renderHistoryEntry(history.get(i)));
		}
		if (history.size() > 10) {
			ctx.getSource().sendSystemMessage(Msg.gray("... and " + (history.size() - 10) + " more"));
		}
		return 1;
	}

	/** Shared with the admin history command, which renders the same rows. */
	static MutableComponent renderHistoryEntry(HistoryDao.Entry entry) {
		MutableComponent msg = Msg.empty().append(Msg.gray("[" + Msg.formatTimestamp(entry.time()) + "] "));
		String other = entry.other();
		String desc = entry.desc();
		switch (entry.type()) {
			case "pay_sent" -> msg.append(Msg.red("-" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" sent to ")).append(Msg.yellow(other));
			case "pay_received" -> msg.append(Msg.green("+" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" from ")).append(Msg.yellow(other));
			case "shop_buy" -> msg.append(Msg.red("-" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" bought ")).append(Msg.white(desc != null ? desc : "items"))
				.append(Msg.gray(" from ")).append(Msg.yellow(other != null ? other : "Unknown"));
			case "shop_sell" -> msg.append(Msg.green("+" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" sold ")).append(Msg.white(desc != null ? desc : "items"))
				.append(Msg.gray(" to ")).append(Msg.yellow(other != null ? other : "Unknown"));
			case "admin_set" -> msg.append(Msg.aqua(Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" balance set by admin"));
			case "admin_add" -> msg.append(Msg.green("+" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" added by admin"));
			case "admin_subtract" -> msg.append(Msg.red("-" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" removed by admin"));
			case "withdraw" -> {
				msg.append(Msg.red("-" + Msg.formatBalance(entry.amount())))
					.append(Msg.gray(" withdrawn as coins"));
				if (desc != null) msg.append(Msg.gray(" (" + desc + ")"));
			}
			case "deposit" -> msg.append(Msg.green("+" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" deposited from coins"));
			case "plot_buy" -> {
				msg.append(Msg.red("-" + Msg.formatBalance(entry.amount())))
					.append(Msg.gray(" bought ")).append(Msg.white(desc != null ? desc : "a plot"));
				if (other != null) msg.append(Msg.gray(" from ")).append(Msg.yellow(other));
			}
			case "plot_sell" -> msg.append(Msg.green("+" + Msg.formatBalance(entry.amount())))
				.append(Msg.gray(" sold ")).append(Msg.white(desc != null ? desc : "a plot"))
				.append(Msg.gray(" to ")).append(Msg.yellow(other != null ? other : "Unknown"));
			default -> msg.append(Msg.gray(entry.type() + " " + Msg.formatBalance(entry.amount())));
		}
		return msg;
	}

	private static int withdraw(CommandContext<CommandSourceStack> ctx, int denomination) {
		ServerPlayer player = playerOrFail(ctx);
		if (player == null) return 0;

		int withdrawAmount = IntegerArgumentType.getInteger(ctx, "amount");
		if (withdrawAmount <= 0) {
			ctx.getSource().sendFailure(Msg.red("Amount must be positive"));
			return 0;
		}

		if (denomination != 0) {
			if (CoinDenomination.byValue(denomination) == null) {
				ctx.getSource().sendFailure(Msg.red("Invalid denomination. Use 1, 10, 100, 1000, or 10000"));
				return 0;
			}
			if (withdrawAmount % denomination != 0) {
				ctx.getSource().sendFailure(Msg.empty()
					.append(Msg.red("Amount "))
					.append(Msg.yellow(Msg.formatBalance(withdrawAmount)))
					.append(Msg.red(" is not divisible by "))
					.append(Msg.yellow("$" + denomination)));
				return 0;
			}
		}

		String uuid = player.getStringUUID();
		int currentBalance = ks().wallets().getBalance(uuid);
		if (currentBalance < withdrawAmount) {
			ctx.getSource().sendFailure(Msg.empty()
				.append(Msg.red("Insufficient balance. You have "))
				.append(Msg.yellow(Msg.formatBalance(currentBalance))));
			return 0;
		}

		if (!ks().wallets().removeBalance(uuid, withdrawAmount)) {
			ctx.getSource().sendFailure(Msg.red("Withdrawal failed - try again"));
			return 0;
		}

		if (denomination == 0) {
			Coins.giveEfficient(player, withdrawAmount);
			ks().history().addEntry(uuid, "withdraw", withdrawAmount, null, "Coins withdrawn");

			int remaining = withdrawAmount;
			List<String> parts = new ArrayList<>();
			for (CoinDenomination denom : CoinDenomination.ALL) {
				if (remaining >= denom.value()) {
					int count = remaining / denom.value();
					remaining = remaining % denom.value();
					parts.add(count + "x $" + denom.value());
				}
			}
			player.sendSystemMessage(Msg.empty()
				.append(Msg.gold("Withdrew "))
				.append(Msg.green(Msg.formatBalance(withdrawAmount)))
				.append(Msg.gold(" as coins: "))
				.append(Msg.yellow(String.join(", ", parts))));
		} else {
			Coins.giveSpecific(player, withdrawAmount, denomination);
			int coinCount = withdrawAmount / denomination;
			ks().history().addEntry(uuid, "withdraw", withdrawAmount, null, coinCount + "x $" + denomination + " coins");
			player.sendSystemMessage(Msg.empty()
				.append(Msg.gold("Withdrew "))
				.append(Msg.green(Msg.formatBalance(withdrawAmount)))
				.append(Msg.gold(" as "))
				.append(Msg.yellow(coinCount + "x $" + denomination + " coins")));
		}
		return 1;
	}

	private static int deposit(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
		ServerPlayer player = playerOrFail(ctx);
		if (player == null) return 0;

		String uuid = player.getStringUUID();
		Coins.CoinCount coins = Coins.count(player);

		if (requestedAmount == -1) {
			if (coins.total() <= 0) {
				ctx.getSource().sendFailure(Msg.red("You don't have any coins to deposit"));
				return 0;
			}
			Coins.remove(player, coins.total());
			if (!ks().wallets().addBalance(uuid, coins.total())) {
				Coins.giveEfficient(player, coins.total());
				ctx.getSource().sendFailure(Msg.red("Deposit failed - your coins were returned"));
				return 0;
			}
			ks().history().addEntry(uuid, "deposit", coins.total(), null, "All coins deposited");
			player.sendSystemMessage(Msg.empty()
				.append(Msg.gold("Deposited "))
				.append(Msg.green(Msg.formatBalance(coins.total())))
				.append(Msg.gold(" from coins")));
			return 1;
		}

		if (requestedAmount <= 0) {
			ctx.getSource().sendFailure(Msg.red("Amount must be positive"));
			return 0;
		}
		if (coins.total() < requestedAmount) {
			ctx.getSource().sendFailure(Msg.empty()
				.append(Msg.red("You don't have enough coins. You have "))
				.append(Msg.yellow(Msg.formatBalance(coins.total())))
				.append(Msg.red(" in coins")));
			return 0;
		}
		if (!Coins.canPayExact(player, requestedAmount)) {
			ctx.getSource().sendFailure(Msg.empty()
				.append(Msg.red("Cannot deposit exact amount "))
				.append(Msg.yellow(Msg.formatBalance(requestedAmount)))
				.append(Msg.red(". You need exact change.")));
			return 0;
		}

		Coins.remove(player, requestedAmount);
		if (!ks().wallets().addBalance(uuid, requestedAmount)) {
			Coins.giveEfficient(player, requestedAmount);
			ctx.getSource().sendFailure(Msg.red("Deposit failed - your coins were returned"));
			return 0;
		}
		ks().history().addEntry(uuid, "deposit", requestedAmount, null, "Coins deposited");
		player.sendSystemMessage(Msg.empty()
			.append(Msg.gold("Deposited "))
			.append(Msg.green(Msg.formatBalance(requestedAmount)))
			.append(Msg.gold(" from coins")));
		return 1;
	}
}
