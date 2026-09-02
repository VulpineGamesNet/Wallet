package net.vulpinegames.wallet.platform;

public interface Platform {
	boolean isModLoaded(String modId);

	java.nio.file.Path configDir();

	/**
	 * The container at a block position, for the shop engine. Forge/NeoForge
	 * back it with the item-handler capability - what KubeJS wrapped, so
	 * double-chest and modded-container behavior match the templates already
	 * in the DB. Fabric falls back to the vanilla Container lookup. Null when
	 * the block holds no inventory.
	 */
	net.vulpinegames.wallet.shop.ShopInventory inventoryAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);

	ModLoader loader();

	String mcVersion();

	boolean isDevelopmentEnvironment();

	default boolean isDebug() {
		return isDevelopmentEnvironment();
	}

	enum ModLoader {
		FABRIC, NEOFORGE, FORGE, QUILT
	}
}
