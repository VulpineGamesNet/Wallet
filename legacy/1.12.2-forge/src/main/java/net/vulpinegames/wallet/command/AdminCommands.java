package net.vulpinegames.wallet.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.db.HistoryDao;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.shop.SignRaytrace;
import net.vulpinegames.wallet.world.PlayerNames;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.vulpinegames.wallet.command.WalletCommand.ks;
import static net.vulpinegames.wallet.command.WalletCommand.playerOrFail;
import static net.vulpinegames.wallet.command.WalletCommand.profileOrFail;
import static net.vulpinegames.wallet.command.WalletCommand.say;

/** /wallet admin - OP level 2 only, same subcommands as the modern builds. */
final class AdminCommands {
	private AdminCommands() {}

	static void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			help(sender);
			return;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "balance":
			case "getbalance":
				need(args, 2, "/wallet admin balance <player>");
				showBalance(server, sender, args[1]);
				return;
			case "setbalance":
				need(args, 3, "/wallet admin setbalance <player> <amount>");
				setBalance(server, sender, args[1], CommandBase.parseInt(args[2], 0));
				return;
			case "addbalance":
				need(args, 3, "/wallet admin addbalance <player> <amount>");
				addBalance(server, sender, args[1], CommandBase.parseInt(args[2], 1));
				return;
			case "subtractbalance":
				need(args, 3, "/wallet admin subtractbalance <player> <amount>");
				subtractBalance(server, sender, args[1], CommandBase.parseInt(args[2], 1));
				return;
			case "history":
				need(args, 2, "/wallet admin history <player>");
				history(server, sender, args[1]);
				return;
			case "shop":
				shop(server, sender, args);
				return;
			case "reload":
				ks().reload(Wallet.configDir());
				say(sender, Msg.gold("Wallet config and caches reloaded (")
					.appendSibling(Msg.yellow(String.valueOf(ks().shopCache().size())))
					.appendSibling(Msg.gold(" shop signs)")));
				return;
			default:
				help(sender);
		}
	}

	private static void need(String[] args, int n, String usage) throws CommandException {
		if (args.length < n) throw new WrongUsageException(usage);
	}

	static List<String> tabComplete(MinecraftServer server, String[] args) {
		if (args.length == 1) {
			return CommandBase.getListOfStringsMatchingLastWord(args, "balance", "setbalance", "addbalance",
				"subtractbalance", "history", "shop", "reload");
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		if ("shop".equals(sub)) {
			if (args.length == 2) {
				return CommandBase.getListOfStringsMatchingLastWord(args, "list", "convert-to-server", "convert-to-infinite", "server-balance");
			}
			if (args.length == 3 && "list".equalsIgnoreCase(args[1])) {
				return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
			}
			return Collections.emptyList();
		}
		if (args.length == 2 && !"reload".equals(sub)) {
			return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
		}
		return Collections.emptyList();
	}

	private static void help(ICommandSender src) {
		say(src, Msg.gold("=== Wallet Admin Commands ==="));
		say(src, Msg.yellow("/wallet admin balance <player>").appendSibling(Msg.gray(" - Check any player's balance, online or not")));
		say(src, Msg.yellow("/wallet admin setbalance <player> <amount>").appendSibling(Msg.gray(" - Set balance")));
		say(src, Msg.yellow("/wallet admin addbalance <player> <amount>").appendSibling(Msg.gray(" - Add to balance")));
		say(src, Msg.yellow("/wallet admin subtractbalance <player> <amount>").appendSibling(Msg.gray(" - Subtract from balance")));
		say(src, Msg.yellow("/wallet admin history <player>").appendSibling(Msg.gray(" - View player's history")));
		say(src, Msg.yellow("/wallet admin shop list [player]").appendSibling(Msg.gray(" - List shops, [S]erver / [I]nfinite")));
		say(src, Msg.yellow("/wallet admin shop convert-to-server").appendSibling(Msg.gray(" - Toggle the shop you look at between server- and player-owned")));
		say(src, Msg.yellow("/wallet admin shop convert-to-infinite").appendSibling(Msg.gray(" - Toggle infinite stock on the shop you look at")));
		say(src, Msg.yellow("/wallet admin shop server-balance [amount]").appendSibling(Msg.gray(" - Show or set the server wallet")));
		say(src, Msg.yellow("/wallet admin reload").appendSibling(Msg.gray(" - Reload config and caches")));
	}

	private static void showBalance(MinecraftServer server, ICommandSender sender, String name) {
		GameProfile prof = profileOrFail(server, sender, name);
		if (prof == null) return;
		int bal = ks().wallets().getBalance(prof.getId().toString());
		say(sender, Msg.empty().appendSibling(Msg.yellow(prof.getName())).appendSibling(Msg.gold("'s balance: "))
			.appendSibling(Msg.green(Msg.formatBalance(bal))));
	}

	private static void setBalance(MinecraftServer server, ICommandSender sender, String name, int amt) {
		GameProfile prof = profileOrFail(server, sender, name);
		if (prof == null) return;
		String uuid = prof.getId().toString();
		int oldBal = ks().wallets().getBalance(uuid);
		if (!ks().wallets().setBalance(uuid, amt)) {
			say(sender, Msg.red("Balance write failed, check the server log"));
			return;
		}
		ks().history().addEntry(uuid, "admin_set", amt, "Admin", "Balance set from " + Msg.formatBalance(oldBal));
		say(sender, Msg.empty().appendSibling(Msg.gold("Set ")).appendSibling(Msg.yellow(prof.getName()))
			.appendSibling(Msg.gold("'s balance to ")).appendSibling(Msg.green(Msg.formatBalance(amt))));
	}

	private static void addBalance(MinecraftServer server, ICommandSender sender, String name, int amt) {
		GameProfile prof = profileOrFail(server, sender, name);
		if (prof == null) return;
		String uuid = prof.getId().toString();
		int oldBal = ks().wallets().getBalance(uuid);
		if (!ks().wallets().addBalance(uuid, amt)) {
			say(sender, Msg.red("Balance write failed, check the server log"));
			return;
		}
		int newBal = ks().wallets().getBalance(uuid);
		ks().history().addEntry(uuid, "admin_add", amt, "Admin", null);
		say(sender, Msg.empty().appendSibling(Msg.gold("Added ")).appendSibling(Msg.green(Msg.formatBalance(amt)))
			.appendSibling(Msg.gold(" to ")).appendSibling(Msg.yellow(prof.getName())).appendSibling(Msg.gold("'s balance ("))
			.appendSibling(Msg.gray(Msg.formatBalance(oldBal))).appendSibling(Msg.gold(" -> "))
			.appendSibling(Msg.green(Msg.formatBalance(newBal))).appendSibling(Msg.gold(")")));
	}

	private static void subtractBalance(MinecraftServer server, ICommandSender sender, String name, int amt) {
		GameProfile prof = profileOrFail(server, sender, name);
		if (prof == null) return;
		String uuid = prof.getId().toString();
		int oldBal = ks().wallets().getBalance(uuid);
		if (oldBal < amt) {
			say(sender, Msg.empty().appendSibling(Msg.red("Cannot subtract ")).appendSibling(Msg.yellow(Msg.formatBalance(amt)))
				.appendSibling(Msg.red(" from ")).appendSibling(Msg.yellow(prof.getName())).appendSibling(Msg.red(" (has "))
				.appendSibling(Msg.yellow(Msg.formatBalance(oldBal))).appendSibling(Msg.red(")")));
			return;
		}
		if (!ks().wallets().removeBalance(uuid, amt)) {
			say(sender, Msg.red("Balance write failed, check the server log"));
			return;
		}
		int newBal = ks().wallets().getBalance(uuid);
		ks().history().addEntry(uuid, "admin_subtract", amt, "Admin", null);
		say(sender, Msg.empty().appendSibling(Msg.gold("Subtracted ")).appendSibling(Msg.red(Msg.formatBalance(amt)))
			.appendSibling(Msg.gold(" from ")).appendSibling(Msg.yellow(prof.getName())).appendSibling(Msg.gold("'s balance ("))
			.appendSibling(Msg.gray(Msg.formatBalance(oldBal))).appendSibling(Msg.gold(" -> "))
			.appendSibling(Msg.green(Msg.formatBalance(newBal))).appendSibling(Msg.gold(")")));
	}

	private static void history(MinecraftServer server, ICommandSender sender, String name) {
		GameProfile prof = profileOrFail(server, sender, name);
		if (prof == null) return;
		List<HistoryDao.Entry> history = ks().history().getHistory(prof.getId().toString());
		say(sender, Msg.gold("=== Transaction History for ").appendSibling(Msg.yellow(prof.getName())).appendSibling(Msg.gold(" ===")));
		if (history.isEmpty()) {
			say(sender, Msg.gray("No transactions yet"));
			return;
		}
		int toShow = Math.min(history.size(), 15);
		for (int i = 0; i < toShow; i++) say(sender, WalletCommand.renderHistoryEntry(history.get(i)));
		if (history.size() > 15) say(sender, Msg.gray("... and " + (history.size() - 15) + " more"));
	}

	private static void shop(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			help(sender);
			return;
		}
		switch (args[1].toLowerCase(Locale.ROOT)) {
			case "list":
				if (args.length >= 3) {
					GameProfile prof = profileOrFail(server, sender, args[2]);
					if (prof != null) listShops(server, sender, prof);
				} else {
					listShops(server, sender, null);
				}
				return;
			case "convert-to-server":
				toggleShopFlag(server, sender, "server");
				return;
			case "convert-to-infinite":
				toggleShopFlag(server, sender, "infinite");
				return;
			case "server-balance":
				if (args.length >= 3) {
					int amount = CommandBase.parseInt(args[2], 0);
					ks().wallets().setBalance(WalletDao.SERVER_UUID, amount);
					say(sender, Msg.gold("Server balance set to ").appendSibling(Msg.green(Msg.formatBalance(amount))));
				} else {
					say(sender, Msg.gold("Server balance: ")
						.appendSibling(Msg.green(Msg.formatBalance(ks().wallets().getBalance(WalletDao.SERVER_UUID)))));
				}
				return;
			default:
				help(sender);
		}
	}

	private static ITextComponent shopFlagComponent(ShopDao.Shop shop) {
		String tag = (shop.isServer ? "S" : "") + (shop.isInfinite ? "I" : "");
		return tag.isEmpty() ? Msg.empty() : Msg.gold("[" + tag + "] ");
	}

	private static ITextComponent templateSummary(String itemTemplateJson) {
		try {
			JsonArray template = new Gson().fromJson(itemTemplateJson, JsonArray.class);
			StringBuilder names = new StringBuilder();
			int listed = Math.min(template.size(), 3);
			for (int i = 0; i < listed; i++) {
				if (i > 0) names.append(", ");
				JsonObject entry = template.get(i).getAsJsonObject();
				names.append(Msg.prettyId(entry.get("id").getAsString()));
			}
			ITextComponent out = Msg.white(names.toString());
			if (template.size() > listed) out.appendSibling(Msg.gray(" +" + (template.size() - listed) + " more"));
			return out;
		} catch (Exception e) {
			return Msg.white("items");
		}
	}

	private static void listShops(MinecraftServer server, ICommandSender sender, GameProfile onlyOwner) {
		Map<String, ShopDao.Shop> shops = ks().shops().getAllShops();
		if (onlyOwner == null) say(sender, Msg.gold("=== All Shops ==="));
		else say(sender, Msg.gold("=== Shops owned by ").appendSibling(Msg.yellow(onlyOwner.getName())).appendSibling(Msg.gold(" ===")));

		if (shops.isEmpty() && onlyOwner == null) {
			say(sender, Msg.gray("No shops registered"));
			return;
		}
		String ownerUuid = onlyOwner != null ? onlyOwner.getId().toString() : null;
		int count = 0;
		for (Map.Entry<String, ShopDao.Shop> e : shops.entrySet()) {
			ShopDao.Shop shop = e.getValue();
			if (ownerUuid != null && !ownerUuid.equals(shop.owner)) continue;
			count++;
			String[] posParts = e.getKey().split("_");
			if (posParts.length < 3) continue;
			String x = posParts[0], y = posParts[1], z = posParts[2];
			String dim = posParts.length > 3 ? posParts[3] : "?";
			ITextComponent tpLink = Msg.clickable(Msg.lightPurple("[TP]"), "/tp @s " + x + " " + y + " " + z,
				Msg.gray("Click to teleport to " + x + ", " + y + ", " + z + " (dimension " + dim + ")"));
			ITextComponent shopMsg = Msg.empty()
				.appendSibling(Msg.yellow("[" + shop.type + "] "))
				.appendSibling(shopFlagComponent(shop))
				.appendSibling(templateSummary(shop.itemTemplate))
				.appendSibling(Msg.gray(" - "))
				.appendSibling(Msg.green(shop.price + "$"));
			if (ownerUuid == null) {
				String ownerName = shop.isServer ? "Server" : PlayerNames.byUuid(server, shop.owner);
				shopMsg.appendSibling(Msg.gray(" by ")).appendSibling(Msg.aqua(ownerName));
			}
			shopMsg.appendSibling(Msg.gray(" ")).appendSibling(tpLink);
			say(sender, shopMsg);
		}
		if (onlyOwner != null && count == 0) say(sender, Msg.gray("No shops found for this player"));
		else say(sender, Msg.gray("Total: " + count + " shops"));
	}

	/** Both convert commands toggle, so the same command undoes itself. */
	private static void toggleShopFlag(MinecraftServer server, ICommandSender sender, String flag) {
		EntityPlayerMP player = playerOrFail(sender);
		if (player == null) return;
		SignRaytrace.Hit looking = SignRaytrace.shopSignLookedAt(player, ks().shopCache());
		ShopDao.Shop shop = looking != null ? ks().shops().getShop(looking.signKey) : null;
		if (shop == null) {
			say(sender, Msg.red("Look at a shop sign first"));
			return;
		}
		boolean isServer = "server".equals(flag) != shop.isServer;
		boolean isInfinite = "infinite".equals(flag) != shop.isInfinite;
		if (!ks().shops().setShopFlags(looking.signKey, isServer, isInfinite)) {
			say(sender, Msg.red("Could not update that shop"));
			return;
		}
		say(sender, Msg.gold("Shop is now ")
			.appendSibling(Msg.yellow(isServer ? "server-owned" : "owned by " + PlayerNames.byUuid(server, shop.owner)))
			.appendSibling(Msg.gold(", "))
			.appendSibling(Msg.yellow(isInfinite ? "infinite stock" : "normal stock")));
	}
}
