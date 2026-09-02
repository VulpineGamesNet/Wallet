package net.vulpinegames.wallet.platform.neoforge;

//? neoforge {

import net.vulpinegames.wallet.Wallet;
import net.neoforged.fml.common.Mod;

@Mod(Wallet.MOD_ID)
public class NeoforgeEntrypoint {

	public NeoforgeEntrypoint() {
		Wallet.onInitialize();
	}
}
//?}
