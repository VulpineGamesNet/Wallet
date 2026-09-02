package net.vulpinegames.wallet.world;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.vulpinegames.wallet.db.WalletDao;

import java.util.Optional;
import java.util.UUID;

public final class PlayerNames {
	private PlayerNames() {}

	/** Online name, then profile cache, then a shortened uuid - like the script. */
	public static String byUuid(MinecraftServer server, String uuid) {
		if (WalletDao.SERVER_UUID.equals(uuid)) return "Server";
		try {
			UUID id = UUID.fromString(uuid);
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			if (online != null) return online.getName().getString();
			if (server.getProfileCache() != null) {
				Optional<GameProfile> prof = server.getProfileCache().get(id);
				if (prof.isPresent()) return prof.get().getName();
			}
		} catch (Exception ignored) {
		}
		return uuid.substring(0, 8) + "...";
	}
}
