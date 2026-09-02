package net.vulpinegames.wallet.coin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.Msg;

/** Right-click deposits, ported from the script. */
public final class CoinInteractions {
	private CoinInteractions() {}

	/** Coin used on a block: swallow the click so nothing else happens. */
	public static boolean shouldBlockCoinUse(ItemStack item) {
		return Coins.denomOf(item) != null;
	}

	/** Coin used on air: deposit one (stack while crouching). True = handled. */
	public static boolean onRightClickItem(WalletServer ks, ServerPlayer player, ItemStack item) {
		CoinDenomination denom = Coins.denomOf(item);
		if (denom == null) return false;
		if (!ks.database().isAvailable()) return false;

		boolean depositAll = player.isCrouching();
		int countToDeposit = depositAll ? item.getCount() : 1;
		int valueToDeposit = denom.value() * countToDeposit;
		String uuid = player.getStringUUID();

		if (!ks.wallets().addBalance(uuid, valueToDeposit)) {
			player.sendSystemMessage(Msg.red("Deposit failed - try again"));
			return true;
		}
		item.shrink(countToDeposit);
		ks.history().addEntry(uuid, "deposit", valueToDeposit, null,
			countToDeposit + "x $" + denom.value() + " coin" + (countToDeposit > 1 ? "s" : ""));

		player.sendSystemMessage(Msg.empty()
			.append(Msg.gold("Deposited "))
			.append(Msg.green(Msg.formatBalance(valueToDeposit)))
			.append(Msg.gray(" (" + countToDeposit + "x $" + denom.value() + ")"))
			.append(Msg.gold(" | Balance: "))
			.append(Msg.green(Msg.formatBalance(ks.wallets().getBalance(uuid)))));
		return true;
	}
}
