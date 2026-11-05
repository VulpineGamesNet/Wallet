package net.vulpinegames.wallet.command;

import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.api.WalletApi;
import net.vulpinegames.wallet.api.WalletSubcommand;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.coin.CoinDenomination;
import net.vulpinegames.wallet.coin.Coins;
import net.vulpinegames.wallet.db.HistoryDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.world.PlayerNames;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The /wallet tree on 1.12.2's CommandBase. Same subcommands, arguments and
 * messages as the Brigadier version; addon branches (/wallet ah) come from
 * {@link WalletApi#registerSubcommand}.
 */
public final class WalletCommand extends CommandBase {
	private static final List<String> BUILTIN = Arrays.asList(
		"balance", "help", "shop", "pay", "history", "top", "withdraw", "deposit", "admin");

	static WalletServer ks() {
		return Wallet.active();
	}

	static boolean isAdmin(ICommandSender sender) {
		return sender.canUseCommand(2, "wallet");
	}

	static void say(ICommandSender sender, ITextComponent msg) {
		sender.sendMessage(msg);
	}

	static EntityPlayerMP playerOrFail(ICommandSender sender) {
		if (sender instanceof EntityPlayerMP) return (EntityPlayerMP) sender;
		say(sender, Msg.red("This command can only be used by players"));
		return null;
	}

	static GameProfile profileOrFail(MinecraftServer server, ICommandSender sender, String name) {
		GameProfile prof = PlayerNames.byName(server, name);
		if (prof == null) say(sender, Msg.red("No player found"));
		return prof;
	}

	@Override
	public String getName() {
		return "wallet";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/wallet [balance|pay|withdraw|deposit|history|shop|help]";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		return true;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		WalletServer ks = ks();
		// Admins keep the tree while the DB is down, so /wallet admin reload can revive it.
		if (ks == null || (!ks.database().isAvailable() && !isAdmin(sender))) {
			say(sender, Msg.red("The economy is offline right now."));
			return;
		}
		if (args.length == 0) {
			rootHelp(sender);
			return;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		switch (sub) {
			case "balance":
				balance(sender);
				return;
			case "help":
				help(sender);
				return;
			case "shop":
				shop(sender, rest);
				return;
			case "pay":
				if (rest.length < 2) throw new net.minecraft.command.WrongUsageException("/wallet pay <player> <amount>");
				pay(server, sender, rest[0], parseInt(rest[1], 1));
				return;
			case "history":
				history(sender);
				return;
			case "top":
				top(server, sender);
				return;
			case "withdraw":
				if (rest.length < 1) throw new net.minecraft.command.WrongUsageException("/wallet withdraw <amount> [denomination]");
				withdraw(sender, parseInt(rest[0], 1), rest.length > 1 ? parseInt(rest[1], 1) : 0);
				return;
			case "deposit":
				deposit(sender, rest.length > 0 ? parseInt(rest[0], 1) : -1);
				return;
			case "admin":
				if (!isAdmin(sender)) {
					say(sender, Msg.red("You do not have permission to use this command."));
					return;
				}
				AdminCommands.execute(server, sender, rest);
				return;
			default:
				WalletSubcommand addon = WalletApi.subcommands().get(sub);
				if (addon != null && addon.canUse(server, sender)) {
					addon.execute(server, sender, rest);
					return;
				}
				say(sender, Msg.red("Unknown subcommand. Try /wallet help"));
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
		if (args.length == 1) {
			List<String> options = new ArrayList<>();
			for (String b : BUILTIN) {
				if ("admin".equals(b) && !isAdmin(sender)) continue;
				if ("top".equals(b) && (ks() == null || !ks().config().walletTop)) continue;
				options.add(b);
			}
			for (Map.Entry<String, WalletSubcommand> e : WalletApi.subcommands().entrySet()) {
				if (e.getValue().canUse(server, sender)) options.add(e.getKey());
			}
			return getListOfStringsMatchingLastWord(args, options);
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		switch (sub) {
			case "pay":
				return args.length == 2 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : Collections.<String>emptyList();
			case "withdraw":
				return args.length == 3 ? getListOfStringsMatchingLastWord(args, "1", "10", "100", "1000", "10000") : Collections.<String>emptyList();
			case "shop":
				return args.length == 2 ? getListOfStringsMatchingLastWord(args, "help") : Collections.<String>emptyList();
			case "admin":
				return isAdmin(sender) ? AdminCommands.tabComplete(server, rest) : Collections.<String>emptyList();
			default:
				WalletSubcommand addon = WalletApi.subcommands().get(sub);
				if (addon != null && addon.canUse(server, sender)) return addon.tabComplete(server, sender, rest);
				return Collections.emptyList();
		}
	}

	private static void rootHelp(ICommandSender src) {
		say(src, Msg.gold("=== Wallet Commands ==="));
		say(src, Msg.yellow("/wallet balance").appendSibling(Msg.gray(" - Check your balance")));
		say(src, Msg.yellow("/wallet pay <player> <amount>").appendSibling(Msg.gray(" - Send money")));
		say(src, Msg.yellow("/wallet withdraw <amount>").appendSibling(Msg.gray(" - Withdraw as coins (auto-split)")));
		say(src, Msg.yellow("/wallet withdraw <amount> <denom>").appendSibling(Msg.gray(" - Withdraw as specific coin (1/10/100/1000)")));
		say(src, Msg.yellow("/wallet deposit").appendSibling(Msg.gray(" - Deposit all coins")));
		say(src, Msg.yellow("/wallet deposit <amount>").appendSibling(Msg.gray(" - Deposit specific value")));
		say(src, Msg.yellow("/wallet history").appendSibling(Msg.gray(" - Transaction history")));
		say(src, Msg.yellow("/wallet shop help").appendSibling(Msg.gray(" - Shop creation guide")));
		if (Wallet.isModLoaded("walletauction")) {
			say(src, Msg.yellow("/wallet ah").appendSibling(Msg.gray(" - Player auction house")));
		}
		if (isAdmin(src)) {
			say(src, Msg.red("/wallet admin").appendSibling(Msg.gray(" - Admin commands")));
		}
	}

	private static void help(ICommandSender src) {
		say(src, Msg.gold("=== Wallet Commands ==="));
		say(src, Msg.yellow("/wallet balance").appendSibling(Msg.gray(" - Check your balance")));
		say(src, Msg.yellow("/wallet pay <player> <amount>").appendSibling(Msg.gray(" - Send money")));
		say(src, Msg.gold("--- Coin Commands ---"));
		say(src, Msg.yellow("/wallet withdraw <amount>").appendSibling(Msg.gray(" - Withdraw as coins (auto-split)")));
		say(src, Msg.yellow("/wallet withdraw <amount> <denom>").appendSibling(Msg.gray(" - Withdraw as specific coin")));
		say(src, Msg.gray("  Denominations: 1, 10, 100, 1000, 10000"));
		say(src, Msg.gray("  Example: /wallet withdraw 100 10 = 10x $10 coins"));
		say(src, Msg.yellow("/wallet deposit").appendSibling(Msg.gray(" - Deposit all coins in inventory")));
		say(src, Msg.yellow("/wallet deposit <amount>").appendSibling(Msg.gray(" - Deposit specific value from coins")));
		say(src, Msg.gray("  Or right-click a coin (crouch for the whole stack)"));
		say(src, Msg.gold("--- Other ---"));
		say(src, Msg.yellow("/wallet history").appendSibling(Msg.gray(" - Transaction history")));
		say(src, Msg.yellow("/wallet shop help").appendSibling(Msg.gray(" - Shop creation guide")));
		if (Wallet.isModLoaded("walletauction")) {
			say(src, Msg.yellow("/wallet ah").appendSibling(Msg.gray(" - Player auction house")));
		}
		if (isAdmin(src)) {
			say(src, Msg.red("/wallet admin").appendSibling(Msg.gray(" - Admin commands")));
		}
	}

	private static void shop(ICommandSender src, String[] rest) {
		if (rest.length == 0 || !"help".equalsIgnoreCase(rest[0])) {
			say(src, Msg.gold("=== Shop Commands ==="));
			say(src, Msg.yellow("/wallet shop help").appendSibling(Msg.gray(" - Shop creation guide")));
			return;
		}
		say(src, Msg.gold("=== Shop Creation Guide ==="));
		say(src, Msg.white("1. Place a chest with items to sell/buy"));
		say(src, Msg.white("2. Place a wall sign on the chest"));
		say(src, Msg.white("3. Write on the sign:"));
		say(src, Msg.gray("   Line 1: ").appendSibling(Msg.yellow("[BUY]")).appendSibling(Msg.gray(" or ")).appendSibling(Msg.yellow("[SELL]")));
		say(src, Msg.gray("   Line 2-3: Description (optional)"));
		say(src, Msg.gray("   Line 4: Price (e.g. ").appendSibling(Msg.green("10")).appendSibling(Msg.gray(")")));
		say(src, Msg.white("4. Shift+right-click the sign to create"));
		say(src, Msg.gold("---"));
		say(src, Msg.yellow("[BUY]").appendSibling(Msg.gray(" = Players buy FROM the shop")));
		say(src, Msg.yellow("[SELL]").appendSibling(Msg.gray(" = Players sell TO the shop")));
		say(src, Msg.gray("Right-click shop sign to buy/sell"));
		say(src, Msg.gray("Shift+click existing shop to see info"));
		say(src, Msg.gray("Break sign or chest to remove shop"));
		if (ks() != null && ks().config().protectShopChests) {
			say(src, Msg.gray("Only you (and staff) can open your shop chest"));
		}
	}

	private static void balance(ICommandSender sender) {
		EntityPlayerMP player = playerOrFail(sender);
		if (player == null) return;
		int bal = ks().wallets().getBalance(player.getCachedUniqueIdString());
		say(player, Msg.empty().appendSibling(Msg.gold("Your balance: ")).appendSibling(Msg.green(Msg.formatBalance(bal))));
	}

	private static void pay(MinecraftServer server, ICommandSender sender, String recipientName, int payAmount) {
		EntityPlayerMP payer = playerOrFail(sender);
		if (payer == null) return;
		GameProfile recipient = profileOrFail(server, sender, recipientName);
		if (recipient == null) return;

		String senderUuid = payer.getCachedUniqueIdString();
		String recipientUuid = recipient.getId().toString();
		String name = recipient.getName();

		if (senderUuid.equals(recipientUuid)) {
			say(sender, Msg.red("You cannot pay yourself"));
			return;
		}
		int senderBalance = ks().wallets().getBalance(senderUuid);
		if (senderBalance < payAmount) {
			say(sender, Msg.empty().appendSibling(Msg.red("Insufficient funds. You have "))
				.appendSibling(Msg.yellow(Msg.formatBalance(senderBalance))));
			return;
		}
		if (!ks().wallets().removeBalance(senderUuid, payAmount)) {
			say(sender, Msg.red("Payment failed - nothing was sent"));
			return;
		}
		if (!ks().wallets().addBalance(recipientUuid, payAmount)) {
			ks().wallets().addBalance(senderUuid, payAmount);
			say(sender, Msg.red("Payment failed - nothing was sent"));
			return;
		}
		ks().history().addEntry(senderUuid, "pay_sent", payAmount, name, null);
		ks().history().addEntry(recipientUuid, "pay_received", payAmount, payer.getName(), null);

		say(payer, Msg.empty().appendSibling(Msg.gold("Sent ")).appendSibling(Msg.green(Msg.formatBalance(payAmount)))
			.appendSibling(Msg.gold(" to ")).appendSibling(Msg.yellow(name)));
		EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(recipient.getId());
		if (online != null) {
			say(online, Msg.empty().appendSibling(Msg.gold("You received ")).appendSibling(Msg.green(Msg.formatBalance(payAmount)))
				.appendSibling(Msg.gold(" from ")).appendSibling(Msg.yellow(payer.getName())));
		}
	}

	private static void top(MinecraftServer server, ICommandSender sender) {
		if (!ks().config().walletTop) {
			say(sender, Msg.red("The leaderboard is disabled on this server"));
			return;
		}
		List<WalletDao.TopEntry> top = ks().wallets().top(10);
		say(sender, Msg.gold("=== Richest Players ==="));
		if (top.isEmpty()) {
			say(sender, Msg.gray("No wallets yet"));
			return;
		}
		int rank = 1;
		for (WalletDao.TopEntry entry : top) {
			say(sender, Msg.empty().appendSibling(Msg.gold("#" + rank + " "))
				.appendSibling(Msg.yellow(PlayerNames.byUuid(server, entry.uuid)))
				.appendSibling(Msg.gray(" - ")).appendSibling(Msg.green(Msg.formatBalance(entry.balance))));
			rank++;
		}
	}

	private static void history(ICommandSender sender) {
		EntityPlayerMP player = playerOrFail(sender);
		if (player == null) return;
		List<HistoryDao.Entry> history = ks().history().getHistory(player.getCachedUniqueIdString());
		say(sender, Msg.gold("=== Transaction History ==="));
		if (history.isEmpty()) {
			say(sender, Msg.gray("No transactions yet"));
			return;
		}
		int toShow = Math.min(history.size(), 10);
		for (int i = 0; i < toShow; i++) say(sender, renderHistoryEntry(history.get(i)));
		if (history.size() > 10) say(sender, Msg.gray("... and " + (history.size() - 10) + " more"));
	}

	/** Shared with the admin history command. */
	static ITextComponent renderHistoryEntry(HistoryDao.Entry entry) {
		ITextComponent msg = Msg.empty().appendSibling(Msg.gray("[" + Msg.formatTimestamp(entry.time) + "] "));
		String other = entry.other;
		String desc = entry.desc;
		switch (entry.type) {
			case "pay_sent":
				msg.appendSibling(Msg.red("-" + Msg.formatBalance(entry.amount)))
					.appendSibling(Msg.gray(" sent to ")).appendSibling(Msg.yellow(String.valueOf(other)));
				break;
			case "pay_received":
				msg.appendSibling(Msg.green("+" + Msg.formatBalance(entry.amount)))
					.appendSibling(Msg.gray(" from ")).appendSibling(Msg.yellow(String.valueOf(other)));
				break;
			case "shop_buy":
				msg.appendSibling(Msg.red("-" + Msg.formatBalance(entry.amount)))
					.appendSibling(Msg.gray(" bought ")).appendSibling(Msg.white(desc != null ? desc : "items"))
					.appendSibling(Msg.gray(" from ")).appendSibling(Msg.yellow(other != null ? other : "Unknown"));
				break;
			case "shop_sell":
				msg.appendSibling(Msg.green("+" + Msg.formatBalance(entry.amount)))
					.appendSibling(Msg.gray(" sold ")).appendSibling(Msg.white(desc != null ? desc : "items"))
					.appendSibling(Msg.gray(" to ")).appendSibling(Msg.yellow(other != null ? other : "Unknown"));
				break;
			case "admin_set":
				msg.appendSibling(Msg.aqua(Msg.formatBalance(entry.amount))).appendSibling(Msg.gray(" balance set by admin"));
				break;
			case "admin_add":
				msg.appendSibling(Msg.green("+" + Msg.formatBalance(entry.amount))).appendSibling(Msg.gray(" added by admin"));
				break;
			case "admin_subtract":
				msg.appendSibling(Msg.red("-" + Msg.formatBalance(entry.amount))).appendSibling(Msg.gray(" removed by admin"));
				break;
			case "withdraw":
				msg.appendSibling(Msg.red("-" + Msg.formatBalance(entry.amount))).appendSibling(Msg.gray(" withdrawn as coins"));
				if (desc != null) msg.appendSibling(Msg.gray(" (" + desc + ")"));
				break;
			case "deposit":
				msg.appendSibling(Msg.green("+" + Msg.formatBalance(entry.amount))).appendSibling(Msg.gray(" deposited from coins"));
				break;
			case "AH_BUY":
				msg.appendSibling(Msg.red("-" + Msg.formatBalance(Math.abs(entry.amount))))
					.appendSibling(Msg.gray(" auction purchase")).appendSibling(Msg.gray(desc != null ? " (" + desc + ")" : ""));
				break;
			case "AH_SELL":
				msg.appendSibling(Msg.green("+" + Msg.formatBalance(entry.amount)))
					.appendSibling(Msg.gray(" auction sale")).appendSibling(Msg.gray(desc != null ? " (" + desc + ")" : ""));
				break;
			default:
				msg.appendSibling(Msg.gray(entry.type + " " + Msg.formatBalance(entry.amount)));
		}
		return msg;
	}

	private static void withdraw(ICommandSender sender, int withdrawAmount, int denomination) {
		EntityPlayerMP player = playerOrFail(sender);
		if (player == null) return;
		if (denomination != 0) {
			if (CoinDenomination.byValue(denomination) == null) {
				say(sender, Msg.red("Invalid denomination. Use 1, 10, 100, 1000, or 10000"));
				return;
			}
			if (withdrawAmount % denomination != 0) {
				say(sender, Msg.empty().appendSibling(Msg.red("Amount ")).appendSibling(Msg.yellow(Msg.formatBalance(withdrawAmount)))
					.appendSibling(Msg.red(" is not divisible by ")).appendSibling(Msg.yellow("$" + denomination)));
				return;
			}
		}
		String uuid = player.getCachedUniqueIdString();
		int currentBalance = ks().wallets().getBalance(uuid);
		if (currentBalance < withdrawAmount) {
			say(sender, Msg.empty().appendSibling(Msg.red("Insufficient balance. You have "))
				.appendSibling(Msg.yellow(Msg.formatBalance(currentBalance))));
			return;
		}
		if (!ks().wallets().removeBalance(uuid, withdrawAmount)) {
			say(sender, Msg.red("Withdrawal failed - try again"));
			return;
		}
		if (denomination == 0) {
			Coins.giveEfficient(player, withdrawAmount);
			ks().history().addEntry(uuid, "withdraw", withdrawAmount, null, "Coins withdrawn");
			int remaining = withdrawAmount;
			List<String> parts = new ArrayList<>();
			for (CoinDenomination denom : CoinDenomination.ALL) {
				if (remaining >= denom.value) {
					int count = remaining / denom.value;
					remaining = remaining % denom.value;
					parts.add(count + "x $" + denom.value);
				}
			}
			say(player, Msg.empty().appendSibling(Msg.gold("Withdrew ")).appendSibling(Msg.green(Msg.formatBalance(withdrawAmount)))
				.appendSibling(Msg.gold(" as coins: ")).appendSibling(Msg.yellow(String.join(", ", parts))));
		} else {
			Coins.giveSpecific(player, withdrawAmount, denomination);
			int coinCount = withdrawAmount / denomination;
			ks().history().addEntry(uuid, "withdraw", withdrawAmount, null, coinCount + "x $" + denomination + " coins");
			say(player, Msg.empty().appendSibling(Msg.gold("Withdrew ")).appendSibling(Msg.green(Msg.formatBalance(withdrawAmount)))
				.appendSibling(Msg.gold(" as ")).appendSibling(Msg.yellow(coinCount + "x $" + denomination + " coins")));
		}
	}

	private static void deposit(ICommandSender sender, int requestedAmount) {
		EntityPlayerMP player = playerOrFail(sender);
		if (player == null) return;
		String uuid = player.getCachedUniqueIdString();
		Coins.CoinCount coins = Coins.count(player);

		if (requestedAmount == -1) {
			if (coins.total <= 0) {
				say(sender, Msg.red("You don't have any coins to deposit"));
				return;
			}
			Coins.remove(player, coins.total);
			if (!ks().wallets().addBalance(uuid, coins.total)) {
				Coins.giveEfficient(player, coins.total);
				say(sender, Msg.red("Deposit failed - your coins were returned"));
				return;
			}
			ks().history().addEntry(uuid, "deposit", coins.total, null, "All coins deposited");
			say(player, Msg.empty().appendSibling(Msg.gold("Deposited ")).appendSibling(Msg.green(Msg.formatBalance(coins.total)))
				.appendSibling(Msg.gold(" from coins")));
			return;
		}
		if (coins.total < requestedAmount) {
			say(sender, Msg.empty().appendSibling(Msg.red("You don't have enough coins. You have "))
				.appendSibling(Msg.yellow(Msg.formatBalance(coins.total))).appendSibling(Msg.red(" in coins")));
			return;
		}
		if (!Coins.canPayExact(player, requestedAmount)) {
			say(sender, Msg.empty().appendSibling(Msg.red("Cannot deposit exact amount "))
				.appendSibling(Msg.yellow(Msg.formatBalance(requestedAmount))).appendSibling(Msg.red(". You need exact change.")));
			return;
		}
		Coins.remove(player, requestedAmount);
		if (!ks().wallets().addBalance(uuid, requestedAmount)) {
			Coins.giveEfficient(player, requestedAmount);
			say(sender, Msg.red("Deposit failed - your coins were returned"));
			return;
		}
		ks().history().addEntry(uuid, "deposit", requestedAmount, null, "Coins deposited");
		say(player, Msg.empty().appendSibling(Msg.gold("Deposited ")).appendSibling(Msg.green(Msg.formatBalance(requestedAmount)))
			.appendSibling(Msg.gold(" from coins")));
	}
}
