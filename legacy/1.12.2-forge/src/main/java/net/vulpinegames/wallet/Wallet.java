package net.vulpinegames.wallet;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.vulpinegames.wallet.command.WalletCommand;
import net.vulpinegames.wallet.event.WalletEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Minecraft 1.12.2 Forge entry point. Server-side only: acceptableRemoteVersions
 * "*" lets vanilla and modded clients without Wallet join.
 */
@Mod(modid = Wallet.MOD_ID, name = Wallet.MOD_NAME, version = Wallet.MOD_VERSION,
	acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class Wallet {
	public static final String MOD_ID = "wallet";
	public static final String MOD_NAME = "Wallet";
	public static final String MOD_VERSION = "0.1.0-alpha.2";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static WalletServer active;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		LOGGER.info("Initializing {} {} on Forge 1.12.2", MOD_ID, MOD_VERSION);
		MinecraftForge.EVENT_BUS.register(new WalletEvents());
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new WalletCommand());
	}

	@Mod.EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		active = WalletServer.start(server, configDir());
	}

	@Mod.EventHandler
	public void serverStopping(FMLServerStoppingEvent event) {
		if (active != null) {
			active.stop();
			active = null;
		}
	}

	/** The running server state, null while no server is up. */
	public static WalletServer active() {
		return active;
	}

	public static Path configDir() {
		return Loader.instance().getConfigDir().toPath();
	}

	public static boolean isModLoaded(String modId) {
		return Loader.isModLoaded(modId);
	}
}
