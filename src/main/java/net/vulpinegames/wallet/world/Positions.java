package net.vulpinegames.wallet.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Key formats are a compat contract with rows the KubeJS scripts already wrote.
 * The scripts used KubeJS's getDimension().toString(), which prints the vanilla
 * ResourceKey form "ResourceKey[minecraft:dimension / minecraft:overworld]" -
 * level.dimension().toString() produces the same. VERIFY against a dump of
 * real sign_key/plot_key rows from both prod DBs before first deploy.
 */
public final class Positions {
	private Positions() {}

	public static String dimString(Level level) {
		return level.dimension().toString();
	}

	/** sign_key / chest_pos format: {@code x_y_z_<dimString>}. */
	public static String blockKey(Level level, BlockPos pos) {
		return pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + dimString(level);
	}

}
