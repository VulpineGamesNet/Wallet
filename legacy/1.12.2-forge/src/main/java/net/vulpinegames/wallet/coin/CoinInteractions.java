package net.vulpinegames.wallet.coin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.chat.Msg;

/** Right-click deposits. */
public final class CoinInteractions {
	private CoinInteractions() {}

	/** Coin used on a block: swallow the click so nothing else happens. */
	public static boolean shouldBlockCoinUse(ItemStack item) {
		return Coins.denomOf(item) != null;
	}

	/** Coin used on air: deposit one (stack while crouching). True = handled. */
	public static boolean onRightClickItem(WalletServer ks, EntityPlayerMP player, ItemStack item) {
		CoinDenomination denom = Coins.denomOf(item);
		if (denom == null) return false;
		if (!ks.database().isAvailable()) return false;

		boolean depositAll = player.isSneaking();
		int countToDeposit = depositAll ? item.getCount() : 1;
		int valueToDeposit = denom.value * countToDeposit;
		String uuid = player.getCachedUniqueIdString();

		if (!ks.wallets().addBalance(uuid, valueToDeposit)) {
			player.sendMessage(Msg.red("Deposit failed - try again"));
			return true;
		}
		item.shrink(countToDeposit);
		ks.history().addEntry(uuid, "deposit", valueToDeposit, null,
			countToDeposit + "x $" + denom.value + " coin" + (countToDeposit > 1 ? "s" : ""));

		player.sendMessage(Msg.empty()
			.appendSibling(Msg.gold("Deposited "))
			.appendSibling(Msg.green(Msg.formatBalance(valueToDeposit)))
			.appendSibling(Msg.gray(" (" + countToDeposit + "x $" + denom.value + ")"))
			.appendSibling(Msg.gold(" | Balance: "))
			.appendSibling(Msg.green(Msg.formatBalance(ks.wallets().getBalance(uuid)))));
		return true;
	}
}
