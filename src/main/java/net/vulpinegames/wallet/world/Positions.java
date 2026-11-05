package net.vulpinegames.wallet.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Key formats are a compat contract with rows the KubeJS scripts already wrote.
 * KubeJS 6 on DeceasedCraft stored a raw ID such as "minecraft:overworld";
 * the 1.21 ATM10 database also has ResourceKey-form keys. Preserve the
 * existing 1.21 behavior while matching DeceasedCraft's deployed rows.
 */
public final class Positions {
	private Positions() {}

	public static String dimString(Level level) {
		//? if <=1.20.1 {
		/*return level.dimension().location().toString();
		*///?} else {
		return level.dimension().toString();
		//?}
	}

	/** sign_key / chest_pos format: {@code x_y_z_<dimString>}. */
	public static String blockKey(Level level, BlockPos pos) {
		return pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + dimString(level);
	}

}
