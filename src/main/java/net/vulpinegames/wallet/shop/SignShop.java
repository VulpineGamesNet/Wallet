package net.vulpinegames.wallet.shop;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.vulpinegames.wallet.Wallet;

/** Sign parsing and rewriting for shop signs. */
public final class SignShop {
	/**
	 * FramedBlocks' wall sign is a sign in behaviour only: it extends neither
	 * WallSignBlock nor SignBlockEntity (its block entity sits under
	 * FramedBlockEntity), so instanceof checks reject it. Matched by registry
	 * id and driven through its public getText/setText/isWaxed/setWaxed. Its
	 * FACING is vanilla HORIZONTAL_FACING - the same property WallSignBlock uses.
	 */
	private static final String FRAMED_WALL_SIGN = "framedblocks:framed_wall_sign";

	private SignShop() {}

	public record Parsed(String type, int price) {}

	/** Any block the shop treats as a wall sign: vanilla, or FramedBlocks' framed wall sign. */
	public static boolean isWallSign(BlockState state) {
		return state.getBlock() instanceof WallSignBlock
			|| FRAMED_WALL_SIGN.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	/**
	 * Text and wax access over either sign block entity. Vanilla goes direct;
	 * a framed sign goes through reflection - FramedBlocks is Forge/NeoForge
	 * only and this source set also builds for Fabric, so no compile-time
	 * dependency. A missing method fails closed: null text, not waxed.
	 */
	public record Sign(BlockEntity be) {
		/** Null when the block at pos is no wall sign (or carries no block entity). */
		public static Sign at(Level level, BlockPos pos) {
			if (!isWallSign(level.getBlockState(pos))) return null;
			BlockEntity be = level.getBlockEntity(pos);
			return be != null ? new Sign(be) : null;
		}

		public SignText frontText() {
			return be instanceof SignBlockEntity s ? s.getFrontText()
				: (SignText) call("getText", new Class[]{boolean.class}, true);
		}

		public void setFrontText(SignText text) {
			if (be instanceof SignBlockEntity s) s.setText(text, true);
			else call("setText", new Class[]{SignText.class, boolean.class}, text, true);
		}

		public boolean isWaxed() {
			return be instanceof SignBlockEntity s ? s.isWaxed() : Boolean.TRUE.equals(call("isWaxed", new Class[0]));
		}

		public void setWaxed(boolean waxed) {
			if (be instanceof SignBlockEntity s) s.setWaxed(waxed);
			else call("setWaxed", new Class[]{boolean.class}, waxed);
		}

		private Object call(String name, Class<?>[] types, Object... args) {
			try {
				return be.getClass().getMethod(name, types).invoke(be, args);
			} catch (ReflectiveOperationException | RuntimeException e) {
				Wallet.LOGGER.warn("[Wallet] {} lacks {} - FramedBlocks sign API changed?", be.getClass().getName(), name, e);
				return null;
			}
		}
	}

	/** [BUY]/[SELL] on line 1, digits from line 4. Null when not a shop sign. */
	public static Parsed parseSignText(Sign sign) {
		SignText text = sign.frontText();
		if (text == null) return null;
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
	public static void updateSignForShop(ServerLevel level, BlockPos pos, Sign sign, String shopType, int price) {
		SignText old = sign.frontText();
		if (old == null) return;
		SignText updated = old
			.setMessage(0, Component.literal("[" + shopType + "]").withStyle(ChatFormatting.YELLOW))
			.setMessage(3, Component.literal(price + "$").withStyle(ChatFormatting.GREEN));
		sign.setFrontText(updated);
		sign.setWaxed(true);
		sign.be().setChanged();
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, 3);
	}

	/** The block a wall sign hangs on sits opposite the way it faces. */
	public static BlockPos attachedBlockPos(BlockState signState, BlockPos signPos) {
		if (!isWallSign(signState) || !signState.hasProperty(WallSignBlock.FACING)) return null;
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
