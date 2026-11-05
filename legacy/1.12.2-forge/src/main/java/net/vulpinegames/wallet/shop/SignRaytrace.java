package net.vulpinegames.wallet.shop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.vulpinegames.wallet.world.Positions;

/**
 * "The shop sign the player is looking at", for the admin convert commands.
 * Entity.rayTrace is client-only on 1.12.2, so the ray is built by hand.
 */
public final class SignRaytrace {
	private SignRaytrace() {}

	public static final class Hit {
		public final BlockPos pos;
		public final String signKey;

		Hit(BlockPos pos, String signKey) {
			this.pos = pos;
			this.signKey = signKey;
		}
	}

	public static Hit shopSignLookedAt(EntityPlayerMP player, ShopCache cache) {
		try {
			Vec3d eye = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
			Vec3d end = eye.add(player.getLookVec().scale(5.0D));
			RayTraceResult hit = player.world.rayTraceBlocks(eye, end, false, false, true);
			if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return null;
			BlockPos pos = hit.getBlockPos();
			if (SignShop.signAt(player.world, pos) == null) return null;
			String signKey = Positions.blockKey(player.world, pos);
			if (!cache.contains(signKey)) return null;
			return new Hit(pos, signKey);
		} catch (Exception e) {
			return null;
		}
	}
}
