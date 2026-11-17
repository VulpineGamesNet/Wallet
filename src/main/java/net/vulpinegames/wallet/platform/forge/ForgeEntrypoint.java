package net.vulpinegames.wallet.platform.forge;

//? forge {

/*import net.vulpinegames.wallet.Wallet;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

@Mod(Wallet.MOD_ID)
public class ForgeEntrypoint {

	public ForgeEntrypoint() {
		// Server-side only: clients without the mod may join.
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
			() -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (version, fromServer) -> fromServer));
		// Chest coin loot (wallet:coin_chest); inert until coinLoot is configured.
		ForgeCoinChestModifier.SERIALIZERS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
		Wallet.onInitialize();
	}
}
*///?}
