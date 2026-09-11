package net.vulpinegames.wallet;

import net.vulpinegames.wallet.platform.Platform;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? fabric {
/*import net.vulpinegames.wallet.platform.fabric.FabricPlatform;
*///?} neoforge {
import net.vulpinegames.wallet.platform.neoforge.NeoforgePlatform;
 //?} forge {
/*import net.vulpinegames.wallet.platform.forge.ForgePlatform;
 *///?}

@SuppressWarnings("LoggingSimilarMessage")
public class Wallet {

	public static final String MOD_ID = /*$ mod_id*/ "wallet";
	public static final String MOD_VERSION = /*$ mod_version*/ "0.1.0";
	public static final String MOD_FRIENDLY_NAME = /*$ mod_name*/ "Wallet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	private static WalletServer active;

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, Wallet.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		active = WalletServer.start(server, xplat().configDir());
	}

	public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		if (active != null) {
			active.stop();
			active = null;
		}
	}

	/** The running server state, null while no server is up. */
	public static WalletServer active() {
		return active;
	}

	/**
	 * Main-hand server-side right-click on a block. True = the click is ours:
	 * the caller cancels the event with SUCCESS so vanilla neither uses the
	 * held item nor processes the off-hand, and claim mods never see it.
	 */
	/**
	 * Shop-chest open PROTECTION. Runs for every right-click on a block,
	 * regardless of hand or spectator status - a cheat client sending an
	 * off-hand use-on packet, or a spectator, must not slip past the guard.
	 * True = cancel.
	 */
	public static boolean onProtectBlock(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		if (active == null) return false;
		return net.vulpinegames.wallet.shop.ShopProtection.onRightClickShopChest(active, player, level, pos);
	}

	/**
	 * Shop TRADE / creation / coin deposit. Only the caller's real main-hand
	 * player path reaches here. True = consumed, cancel with SUCCESS.
	 */
	public static boolean onRightClickBlock(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
			net.minecraft.world.item.ItemStack held) {
		if (active == null) return false;
		// A coin never interacts with blocks - the script canceled these too.
		if (net.vulpinegames.wallet.coin.CoinInteractions.shouldBlockCoinUse(held)) return true;
		if (net.vulpinegames.wallet.shop.SignShop.isWallSign(level.getBlockState(pos))) {
			return net.vulpinegames.wallet.shop.ShopInteractions.onRightClickWallSign(active, player, level, pos);
		}
		return false;
	}

	/** Strip shop blocks (signs, chests, supports) from an explosion. */
	public static void onExplosionDetonate(net.minecraft.server.level.ServerLevel level,
			java.util.List<net.minecraft.core.BlockPos> affected) {
		if (active == null) return;
		net.vulpinegames.wallet.shop.ShopProtection.filterExplosion(active, level, affected);
	}

	/** True = cancel a piston action that would move/destroy shop blocks. */
	public static boolean onPistonMove(net.minecraft.server.level.ServerLevel level,
			Iterable<net.minecraft.core.BlockPos> moved) {
		if (active == null) return false;
		return net.vulpinegames.wallet.shop.ShopProtection.shouldCancelPiston(active, level, moved);
	}

	/** Main-hand right-click into air with an item. True = handled (cancel). */
	public static boolean onRightClickItem(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.world.item.ItemStack held) {
		if (active == null) return false;
		return net.vulpinegames.wallet.coin.CoinInteractions.onRightClickItem(active, player, held);
	}

	/** Server-side block break. True = cancel. */
	public static boolean onBlockBreak(net.minecraft.server.level.ServerPlayer player,
			net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
			net.minecraft.world.level.block.state.BlockState state) {
		if (active == null) return false;
		return net.vulpinegames.wallet.shop.ShopProtection.onBlockBreak(active, player, level, pos, state);
	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, Wallet.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	public static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		/*return new FabricPlatform();
		*///?} neoforge {
		return new NeoforgePlatform();
		 //?} forge {
		/*return new ForgePlatform();
		 *///?}
	}

	private static ResourceLocation id(String path) {
		//? > 1.20.1 {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
		 //?} <= 1.20.1 {
		/*return new ResourceLocation(MOD_ID, path);
		*///?}
	}

	private static ResourceLocation id(String namespace, String path) {
		//? > 1.20.1 {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
		 //?} <= 1.20.1 {
		/*return new ResourceLocation(namespace, path);
		*///?}
	}
}
