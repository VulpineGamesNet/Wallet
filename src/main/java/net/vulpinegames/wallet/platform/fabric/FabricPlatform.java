package net.vulpinegames.wallet.platform.fabric;

//? fabric {

/*import net.vulpinegames.wallet.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public java.nio.file.Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public net.vulpinegames.wallet.shop.ShopInventory inventoryAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		// ponytail: vanilla container lookup only - sees both double-chest halves,
		// unlike the capability path the DB templates were made under. Fabric is a
		// build-only target today; revisit with the Transfer API if it ever ships.
		net.minecraft.world.Container container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, pos);
		return container != null ? new net.vulpinegames.wallet.shop.ContainerInventory(container) : null;
	}

	@Override
	public ModLoader loader() {
		return ModLoader.FABRIC;
	}

	@Override
	public String mcVersion() {
		return FabricLoader.getInstance().getRawGameVersion();
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}
}
*///?}
