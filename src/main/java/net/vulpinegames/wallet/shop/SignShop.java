package net.vulpinegames.wallet.shop;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/** Sign parsing and rewriting for shop signs. */
public final class SignShop {
	private SignShop() {}

	public record Parsed(String type, int price) {}

	/** [BUY]/[SELL] on line 1, digits from line 4. Null when not a shop sign. */
	public static Parsed parseSignText(SignBlockEntity sign) {
		SignText text = sign.getFrontText();
		String line1 = text.getMessage(0, false).getString().toUpperCase();
		String type = line1.contains("[BUY]") ? "BUY" : line1.contains("[SELL]") ? "SELL" : null;
		if (type == null) return null;

		String line4 = text.getMessage(3, false).getString();
		StringBuilder digits = new StringBuilder();
		for (char ch : line4.toCharArray()) {
			if (ch >= '0' && ch <= '9') digits.append(ch);
		}
		if (digits.isEmpty()) return null;
		try {
			int price = Integer.parseInt(digits.toString());
			return price >= 0 ? new Parsed(type, price) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Rewrite lines 1 and 4, wax the sign, and sync it - clients cache sign text. */
	public static void updateSignForShop(ServerLevel level, BlockPos pos, SignBlockEntity sign, String shopType, int price) {
		SignText old = sign.getFrontText();
		SignText updated = old
			.setMessage(0, Component.literal("[" + shopType + "]").withStyle(ChatFormatting.YELLOW))
			.setMessage(3, Component.literal(price + "$").withStyle(ChatFormatting.GREEN));
		sign.setText(updated, true);
		sign.setWaxed(true);
		sign.setChanged();
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, 3);
	}

	/** The block a wall sign hangs on sits opposite the way it faces. */
	public static BlockPos attachedBlockPos(BlockState signState, BlockPos signPos) {
		if (!(signState.getBlock() instanceof WallSignBlock)) return null;
		Direction facing = signState.getValue(WallSignBlock.FACING);
		return signPos.relative(facing.getOpposite());
	}

	/**
	 * A double chest is one container spanning two block positions, and its
	 * merged inventory (menu and item-handler alike) opens from EITHER half.
	 * Protection keyed to a single pos would leave the other half open, so
	 * every shop check resolves both. Returns the neighbouring half's pos, or
	 * null for a single chest / non-chest.
	 */
	public static BlockPos otherChestHalf(BlockState state, BlockPos pos) {
		if (!(state.getBlock() instanceof ChestBlock)) return null;
		if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
		return pos.relative(ChestBlock.getConnectedDirection(state));
	}
}
