package net.vulpinegames.wallet.platform.forge;

//? forge {

/*import net.vulpinegames.wallet.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLLoader;

public class ForgePlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public java.nio.file.Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public net.vulpinegames.wallet.shop.ShopInventory inventoryAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			var cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
			if (cap.isPresent()) return new ForgeItemHandlerInventory(cap.get());
		}
		net.minecraft.world.Container container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, pos);
		return container != null ? new net.vulpinegames.wallet.shop.ContainerInventory(container) : null;
	}

	@Override
	public ModLoader loader() {
		return ModLoader.FORGE;
	}

	@Override
	public String mcVersion() {
		return "";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader.isProduction();
	}
}
*///?}
