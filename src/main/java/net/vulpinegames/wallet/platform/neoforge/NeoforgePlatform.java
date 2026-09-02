package net.vulpinegames.wallet.platform.neoforge;

//? neoforge {

import net.vulpinegames.wallet.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.VersionInfo;

public class NeoforgePlatform implements Platform {

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
		net.neoforged.neoforge.items.IItemHandler handler =
			level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
		if (handler != null) return new NeoforgeItemHandlerInventory(handler);
		net.minecraft.world.Container container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, pos);
		return container != null ? new net.vulpinegames.wallet.shop.ContainerInventory(container) : null;
	}

	@Override
	public ModLoader loader() {
		return ModLoader.NEOFORGE;
	}

	@Override
	public String mcVersion() {
		return "";
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !FMLLoader/*? if > 1.21.7 {*//*.getCurrent()*//*?}*/.isProduction();
	}
}
//?}
