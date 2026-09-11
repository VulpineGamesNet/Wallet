package net.vulpinegames.wallet.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.vulpinegames.wallet.world.Positions;

/**
 * "The shop sign the player is looking at" - used only by the admin
 * convert-to-* / server-balance commands, never per tick.
 */
public final class SignRaytrace {
	private SignRaytrace() {}

	public record Hit(BlockPos pos, String signKey) {}

	public static Hit shopSignLookedAt(ServerPlayer player, ShopCache cache) {
		try {
			HitResult hr = player.pick(5, 0.0F, false);
			if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) return null;
			BlockPos pos = bhr.getBlockPos();
			SignShop.Sign sign = SignShop.Sign.at(player.level(), pos);
			if (sign == null || !sign.isWaxed()) return null;
			String signKey = Positions.blockKey(player.level(), pos);
			if (!cache.contains(signKey)) return null;
			return new Hit(pos, signKey);
		} catch (Exception e) {
			return null;
		}
	}
}
