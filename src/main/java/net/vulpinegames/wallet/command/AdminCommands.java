package net.vulpinegames.wallet.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.vulpinegames.wallet.chat.Msg;
import net.vulpinegames.wallet.db.HistoryDao;
import net.vulpinegames.wallet.db.ShopDao;
import net.vulpinegames.wallet.db.WalletDao;
import net.vulpinegames.wallet.shop.SignRaytrace;
import net.vulpinegames.wallet.world.PlayerNames;

import java.util.List;
import java.util.Map;

import static net.vulpinegames.wallet.command.WalletCommand.firstProfileOrFail;
import static net.vulpinegames.wallet.command.WalletCommand.ks;
import static net.vulpinegames.wallet.command.WalletCommand.playerOrFail;

/** /wallet admin - ported from the script, OP level 2 only. */
final class AdminCommands {
	private AdminCommands() {}

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("admin")
			.requires(src -> src.hasPermission(2))
			.executes(AdminCommands::help)

			.then(Commands.literal("balance")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.executes(AdminCommands::showBalance)))
			.then(Commands.literal("getbalance")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.executes(AdminCommands::showBalance)))
			.then(Commands.literal("setbalance")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.then(Commands.argument("amount", IntegerArgumentType.integer(0))
						.executes(AdminCommands::setBalance))))
			.then(Commands.literal("addbalance")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(AdminCommands::addBalance))))
			.then(Commands.literal("subtractbalance")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(AdminCommands::subtractBalance))))
			.then(Commands.literal("history")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
					.executes(AdminCommands::history)))

			.then(Commands.literal("shop")
				.then(Commands.literal("list")
					.executes(ctx -> listShops(ctx, null))
					.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.executes(ctx -> {
							GameProfile prof = firstProfileOrFail(ctx, "player");
							return prof == null ? 0 : listShops(ctx, prof);
						})))
				.then(Commands.literal("convert-to-server")
					.executes(ctx -> toggleShopFlag(ctx, "server")))
				.then(Commands.literal("convert-to-infinite")
					.executes(ctx -> toggleShopFlag(ctx, "infinite")))
				// A server SELL shop pays players out of the server wallet, so that
				// wallet needs a way to be filled - no player profile resolves to it.
				.then(Commands.literal("server-balance")
					.executes(ctx -> {
						ctx.getSource().sendSystemMessage(Msg.gold("Server balance: ")
							.append(Msg.green(Msg.formatBalance(ks().wallets().getBalance(WalletDao.SERVER_UUID)))));
						return 1;
					})
					.then(Commands.argument("amount", IntegerArgumentType.integer(0))
						.executes(ctx -> {
							int amount = IntegerArgumentType.getInteger(ctx, "amount");
							if (amount < 0) {
								ctx.getSource().sendFailure(Msg.red("Amount cannot be negative"));
								return 0;
							}
							ks().wallets().setBalance(WalletDao.SERVER_UUID, amount);
							ctx.getSource().sendSystemMessage(Msg.gold("Server balance set to ")
								.append(Msg.green(Msg.formatBalance(amount))));
							return 1;
						}))))

			.then(Commands.literal("reload")
				.executes(ctx -> {
					ks().reload(net.vulpinegames.wallet.Wallet.xplat().configDir());
					ctx.getSource().sendSystemMessage(Msg.gold("Wallet config and caches reloaded (")
						.append(Msg.yellow(String.valueOf(ks().shopCache().size())))
						.append(Msg.gold(" shop signs)")));
					return 1;
				}));
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		src.sendSystemMessage(Msg.gold("=== Wallet Admin Commands ==="));
		src.sendSystemMessage(Msg.yellow("/wallet admin balance <player>").append(Msg.gray(" - Check any player's balance, online or not")));
		src.sendSystemMessage(Msg.yellow("/wallet admin setbalance <player> <amount>").append(Msg.gray(" - Set balance")));
		src.sendSystemMessage(Msg.yellow("/wallet admin addbalance <player> <amount>").append(Msg.gray(" - Add to balance")));
		src.sendSystemMessage(Msg.yellow("/wallet admin subtractbalance <player> <amount>").append(Msg.gray(" - Subtract from balance")));
		src.sendSystemMessage(Msg.yellow("/wallet admin history <player>").append(Msg.gray(" - View player's history")));
		src.sendSystemMessage(Msg.yellow("/wallet admin shop list [player]").append(Msg.gray(" - List shops, [S]erver / [I]nfinite")));
		src.sendSystemMessage(Msg.yellow("/wallet admin shop convert-to-server").append(Msg.gray(" - Toggle the shop you look at between server- and player-owned")));
		src.sendSystemMessage(Msg.yellow("/wallet admin shop convert-to-infinite").append(Msg.gray(" - Toggle infinite stock on the shop you look at")));
		src.sendSystemMessage(Msg.yellow("/wallet admin shop server-balance [amount]").append(Msg.gray(" - Show or set the server wallet")));
		src.sendSystemMessage(Msg.yellow("/wallet admin reload").append(Msg.gray(" - Reload config and caches")));
		return 1;
	}

	private static int showBalance(CommandContext<CommandSourceStack> ctx) {
		GameProfile prof = firstProfileOrFail(ctx, "player");
		if (prof == null) return 0;
		int bal = ks().wallets().getBalance(prof.getId().toString());
		ctx.getSource().sendSystemMessage(Msg.empty()
			.append(Msg.yellow(prof.getName()))
			.append(Msg.gold("'s balance: "))
			.append(Msg.green(Msg.formatBalance(bal))));
		return 1;
	}

	private static int setBalance(CommandContext<CommandSourceStack> ctx) {
		int amt = IntegerArgumentType.getInteger(ctx, "amount");
		if (amt < 0) {
			ctx.getSource().sendFailure(Msg.red("Amount cannot be negative"));
			return 0;
		}
		GameProfile prof = firstProfileOrFail(ctx, "player");
		if (prof == null) return 0;
		String uuid = prof.getId().toString();

		int oldBal = ks().wallets().getBalance(uuid);
		if (!ks().wallets().setBalance(uuid, amt)) {
			ctx.getSource().sendFailure(Msg.red("Balance write failed, check the server log"));
			return 0;
		}
		ks().history().addEntry(uuid, "admin_set", amt, "Admin", "Balance set from " + Msg.formatBalance(oldBal));

		ctx.getSource().sendSystemMessage(Msg.empty()
			.append(Msg.gold("Set "))
			.append(Msg.yellow(prof.getName()))
			.append(Msg.gold("'s balance to "))
			.append(Msg.green(Msg.formatBalance(amt))));
		return 1;
	}

	private static int addBalance(CommandContext<CommandSourceStack> ctx) {
		int amt = IntegerArgumentType.getInteger(ctx, "amount");
		if (amt <= 0) {
			ctx.getSource().sendFailure(Msg.red("Amount must be positive"));
			return 0;
		}
		GameProfile prof = firstProfileOrFail(ctx, "player");
		if (prof == null) return 0;
		String uuid = prof.getId().toString();

		int oldBal = ks().wallets().getBalance(uuid);
		if (!ks().wallets().addBalance(uuid, amt)) {
			ctx.getSource().sendFailure(Msg.red("Balance write failed, check the server log"));
			return 0;
		}
		int newBal = ks().wallets().getBalance(uuid);
		ks().history().addEntry(uuid, "admin_add", amt, "Admin", null);

		ctx.getSource().sendSystemMessage(Msg.empty()
			.append(Msg.gold("Added "))
			.append(Msg.green(Msg.formatBalance(amt)))
			.append(Msg.gold(" to "))
			.append(Msg.yellow(prof.getName()))
			.append(Msg.gold("'s balance ("))
			.append(Msg.gray(Msg.formatBalance(oldBal)))
			.append(Msg.gold(" -> "))
			.append(Msg.green(Msg.formatBalance(newBal)))
			.append(Msg.gold(")")));
		return 1;
	}

	private static int subtractBalance(CommandContext<CommandSourceStack> ctx) {
		int amt = IntegerArgumentType.getInteger(ctx, "amount");
		if (amt <= 0) {
			ctx.getSource().sendFailure(Msg.red("Amount must be positive"));
			return 0;
		}
		GameProfile prof = firstProfileOrFail(ctx, "player");
		if (prof == null) return 0;
		String uuid = prof.getId().toString();

		int oldBal = ks().wallets().getBalance(uuid);
		if (oldBal < amt) {
			ctx.getSource().sendFailure(Msg.empty()
				.append(Msg.red("Cannot subtract "))
				.append(Msg.yellow(Msg.formatBalance(amt)))
				.append(Msg.red(" from "))
				.append(Msg.yellow(prof.getName()))
				.append(Msg.red(" (has "))
				.append(Msg.yellow(Msg.formatBalance(oldBal)))
				.append(Msg.red(")")));
			return 0;
		}

		if (!ks().wallets().removeBalance(uuid, amt)) {
			ctx.getSource().sendFailure(Msg.red("Balance write failed, check the server log"));
			return 0;
		}
		int newBal = ks().wallets().getBalance(uuid);
		ks().history().addEntry(uuid, "admin_subtract", amt, "Admin", null);

		ctx.getSource().sendSystemMessage(Msg.empty()
			.append(Msg.gold("Subtracted "))
			.append(Msg.red(Msg.formatBalance(amt)))
			.append(Msg.gold(" from "))
			.append(Msg.yellow(prof.getName()))
			.append(Msg.gold("'s balance ("))
			.append(Msg.gray(Msg.formatBalance(oldBal)))
			.append(Msg.gold(" -> "))
			.append(Msg.green(Msg.formatBalance(newBal)))
			.append(Msg.gold(")")));
		return 1;
	}

	private static int history(CommandContext<CommandSourceStack> ctx) {
		GameProfile prof = firstProfileOrFail(ctx, "player");
		if (prof == null) return 0;

		List<HistoryDao.Entry> history = ks().history().getHistory(prof.getId().toString());
		ctx.getSource().sendSystemMessage(Msg.gold("=== Transaction History for ")
			.append(Msg.yellow(prof.getName()))
			.append(Msg.gold(" ===")));

		if (history.isEmpty()) {
			ctx.getSource().sendSystemMessage(Msg.gray("No transactions yet"));
			return 1;
		}
		int toShow = Math.min(history.size(), 15);
		for (int i = 0; i < toShow; i++) {
			ctx.getSource().sendSystemMessage(WalletCommand.renderHistoryEntry(history.get(i)));
		}
		if (history.size() > 15) {
			ctx.getSource().sendSystemMessage(Msg.gray("... and " + (history.size() - 15) + " more"));
		}
		return 1;
	}

	/** [S] server-owned, [I] infinite stock, [SI] both. */
	private static MutableComponent shopFlagComponent(ShopDao.Shop shop) {
		String tag = (shop.isServer() ? "S" : "") + (shop.isInfinite() ? "I" : "");
		return tag.isEmpty() ? Msg.empty() : Msg.gold("[" + tag + "] ");
	}

	/** First few template item names, plain text - this row carries the [TP] link. */
	private static MutableComponent templateSummary(String itemTemplateJson) {
		try {
			JsonArray template = new Gson().fromJson(itemTemplateJson, JsonArray.class);
			StringBuilder names = new StringBuilder();
			int listed = Math.min(template.size(), 3);
			for (int i = 0; i < listed; i++) {
				if (i > 0) names.append(", ");
				JsonObject entry = template.get(i).getAsJsonObject();
				names.append(Msg.prettyId(entry.get("id").getAsString()));
			}
			MutableComponent out = Msg.white(names.toString());
			if (template.size() > listed) {
				out.append(Msg.gray(" +" + (template.size() - listed) + " more"));
			}
			return out;
		} catch (Exception e) {
			return Msg.white("items");
		}
	}

	private static int listShops(CommandContext<CommandSourceStack> ctx, GameProfile onlyOwner) {
		MinecraftServer srv = ctx.getSource().getServer();
		Map<String, ShopDao.Shop> shops = ks().shops().getAllShops();

		if (onlyOwner == null) {
			ctx.getSource().sendSystemMessage(Msg.gold("=== All Shops ==="));
		} else {
			ctx.getSource().sendSystemMessage(Msg.gold("=== Shops owned by ")
				.append(Msg.yellow(onlyOwner.getName()))
				.append(Msg.gold(" ===")));
		}

		if (shops.isEmpty() && onlyOwner == null) {
			ctx.getSource().sendSystemMessage(Msg.gray("No shops registered"));
			return 1;
		}

		String ownerUuid = onlyOwner != null ? onlyOwner.getId().toString() : null;
		int count = 0;
		for (Map.Entry<String, ShopDao.Shop> e : shops.entrySet()) {
			ShopDao.Shop shop = e.getValue();
			if (ownerUuid != null && !ownerUuid.equals(shop.owner())) continue;
			count++;

			String[] posParts = e.getKey().split("_");
			if (posParts.length < 3) continue; // hand-edited or legacy row
			String x = posParts[0], y = posParts[1], z = posParts[2];

			MutableComponent tpLink = Msg.clickable(Msg.lightPurple("[TP]"),
				"/tp @s " + x + " " + y + " " + z,
				Msg.gray("Click to teleport to " + x + ", " + y + ", " + z));

			MutableComponent shopMsg = Msg.empty()
				.append(Msg.yellow("[" + shop.type() + "] "))
				.append(shopFlagComponent(shop))
				.append(templateSummary(shop.itemTemplate()))
				.append(Msg.gray(" - "))
				.append(Msg.green(shop.price() + "$"));
			if (ownerUuid == null) {
				String ownerName = shop.isServer() ? "Server" : PlayerNames.byUuid(srv, shop.owner());
				shopMsg.append(Msg.gray(" by ")).append(Msg.aqua(ownerName));
			}
			shopMsg.append(Msg.gray(" ")).append(tpLink);
			ctx.getSource().sendSystemMessage(shopMsg);
		}

		if (onlyOwner != null && count == 0) {
			ctx.getSource().sendSystemMessage(Msg.gray("No shops found for this player"));
		} else {
			ctx.getSource().sendSystemMessage(Msg.gray("Total: " + count + " shops"));
		}
		return 1;
	}

	/** Both convert commands toggle, so the same command undoes itself. */
	private static int toggleShopFlag(CommandContext<CommandSourceStack> ctx, String flag) {
		ServerPlayer player = playerOrFail(ctx);
		if (player == null) return 0;

		SignRaytrace.Hit looking = SignRaytrace.shopSignLookedAt(player, ks().shopCache());
		ShopDao.Shop shop = looking != null ? ks().shops().getShop(looking.signKey()) : null;
		if (shop == null) {
			ctx.getSource().sendFailure(Msg.red("Look at a shop sign first"));
			return 0;
		}

		boolean isServer = "server".equals(flag) != shop.isServer();
		boolean isInfinite = "infinite".equals(flag) != shop.isInfinite();

		if (!ks().shops().setShopFlags(looking.signKey(), isServer, isInfinite)) {
			ctx.getSource().sendFailure(Msg.red("Could not update that shop"));
			return 0;
		}

		ctx.getSource().sendSystemMessage(Msg.gold("Shop is now ")
			.append(Msg.yellow(isServer ? "server-owned"
				: "owned by " + PlayerNames.byUuid(ctx.getSource().getServer(), shop.owner())))
			.append(Msg.gold(", "))
			.append(Msg.yellow(isInfinite ? "infinite stock" : "normal stock")));
		return 1;
	}
}
