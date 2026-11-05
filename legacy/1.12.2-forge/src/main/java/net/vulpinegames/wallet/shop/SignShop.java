package net.vulpinegames.wallet.shop;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Sign parsing and rewriting for shop signs. 1.12.2 differences from the
 * modern builds: no sign waxing (a placed 1.12 sign cannot be re-edited in
 * vanilla, and Quark-style edit clicks on a registered shop are consumed by the
 * trade handler first), no FramedBlocks, and double chests have no TYPE
 * property - the other half is found by looking at the neighbours.
 */
public final class SignShop {
	private SignShop() {}

	public static final class Parsed {
		public final String type;
		public final int price;

		Parsed(String type, int price) {
			this.type = type;
			this.price = price;
		}
	}

	public static boolean isWallSign(IBlockState state) {
		return state.getBlock() instanceof BlockWallSign;
	}

	/** The sign block entity at pos, or null when it is no wall sign. */
	public static TileEntitySign signAt(World world, BlockPos pos) {
		if (!isWallSign(world.getBlockState(pos))) return null;
		TileEntity te = world.getTileEntity(pos);
		return te instanceof TileEntitySign ? (TileEntitySign) te : null;
	}

	/** [BUY]/[SELL] on line 1, digits from line 4. Null when not a shop sign. */
	public static Parsed parseSignText(TileEntitySign sign) {
		String line1 = line(sign, 0).toUpperCase();
		String type = line1.contains("[BUY]") ? "BUY" : line1.contains("[SELL]") ? "SELL" : null;
		if (type == null) return null;

		String line4 = line(sign, 3);
		StringBuilder digits = new StringBuilder();
		for (char ch : line4.toCharArray()) {
			if (ch >= '0' && ch <= '9') digits.append(ch);
		}
		if (digits.length() == 0) return null;
		try {
			int price = Integer.parseInt(digits.toString());
			return price >= 0 ? new Parsed(type, price) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String line(TileEntitySign sign, int index) {
		ITextComponent c = sign.signText[index];
		return c == null ? "" : TextFormatting.getTextWithoutFormattingCodes(c.getUnformattedText());
	}

	/** Rewrite lines 1 and 4 and push the change to clients. */
	public static void updateSignForShop(World world, BlockPos pos, TileEntitySign sign, String shopType, int price) {
		ITextComponent l1 = new TextComponentString("[" + shopType + "]");
		l1.getStyle().setColor(TextFormatting.YELLOW);
		ITextComponent l4 = new TextComponentString(price + "$");
		l4.getStyle().setColor(TextFormatting.GREEN);
		sign.signText[0] = l1;
		sign.signText[3] = l4;
		sign.markDirty();
		IBlockState state = world.getBlockState(pos);
		world.notifyBlockUpdate(pos, state, state, 3);
	}

	/** The block a wall sign hangs on sits opposite the way it faces. */
	public static BlockPos attachedBlockPos(IBlockState signState, BlockPos signPos) {
		if (!isWallSign(signState)) return null;
		EnumFacing facing = signState.getValue(BlockWallSign.FACING);
		return signPos.offset(facing.getOpposite());
	}

	/**
	 * A double chest is one container over two positions and opens from EITHER
	 * half, so every shop check resolves both. Returns the neighbouring half,
	 * or null for a single chest / non-chest. Same-type only: a trapped chest
	 * never pairs with a normal one.
	 */
	public static BlockPos otherChestHalf(World world, IBlockState state, BlockPos pos) {
		Block block = state.getBlock();
		if (!(block instanceof BlockChest)) return null;
		for (EnumFacing dir : EnumFacing.Plane.HORIZONTAL) {
			BlockPos n = pos.offset(dir);
			if (world.getBlockState(n).getBlock() == block) return n;
		}
		return null;
	}
}
