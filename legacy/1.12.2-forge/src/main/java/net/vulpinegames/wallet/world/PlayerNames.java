package net.vulpinegames.wallet.world;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.vulpinegames.wallet.db.WalletDao;

import java.util.UUID;

public final class PlayerNames {
	private PlayerNames() {}

	/** Online name, then profile cache, then a shortened uuid. */
	public static String byUuid(MinecraftServer server, String uuid) {
		if (WalletDao.SERVER_UUID.equals(uuid)) return "Server";
		try {
			UUID id = UUID.fromString(uuid);
			EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(id);
			if (online != null) return online.getName();
			if (server.getPlayerProfileCache() != null) {
				GameProfile prof = server.getPlayerProfileCache().getProfileByUUID(id);
				if (prof != null && prof.getName() != null) return prof.getName();
			}
		} catch (Exception ignored) {
		}
		return uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
	}

	/**
	 * A player by name: online first, then the profile cache. Like the modern
	 * GameProfileArgument, an uncached name may be looked up with Mojang.
	 */
	public static GameProfile byName(MinecraftServer server, String name) {
		EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(name);
		if (online != null) return online.getGameProfile();
		try {
			GameProfile prof = server.getPlayerProfileCache().getGameProfileForUsername(name);
			if (prof != null && prof.getId() != null) return prof;
		} catch (Exception ignored) {
		}
		return null;
	}
}
