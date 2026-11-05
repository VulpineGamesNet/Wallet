package net.vulpinegames.wallet.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * sign_key / chest_pos format. The modern builds append a dimension key
 * ("minecraft:overworld" on 1.20.1); 1.12.2 has no dimension registry key, and
 * modded dimensions can share one DimensionType, so the numeric dimension id
 * is the unambiguous choice: {@code x_y_z_<dimId>}, e.g. {@code 10_64_-5_0}.
 * RLCraft started with an empty database, so no legacy rows constrain this.
 */
public final class Positions {
	private Positions() {}

	public static String dimString(World world) {
		return String.valueOf(world.provider.getDimension());
	}

	public static String blockKey(World world, BlockPos pos) {
		return pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + dimString(world);
	}
}
