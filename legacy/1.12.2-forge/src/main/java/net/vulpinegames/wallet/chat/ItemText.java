package net.vulpinegames.wallet.chat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

/**
 * Item names in chat with a vanilla show_item hover. The modern builds' chat-link
 * token mode (itemChatLinks) targets a client mod that does not exist on 1.12.2,
 * so it is not ported.
 */
public final class ItemText {
	private ItemText() {}

	public static final class ItemRef {
		public final String id;
		public final int meta;
		public final int count;
		/** SNBT of the item tag, or null. Only used to render the hover. */
		public final String nbt;

		public ItemRef(String id, int meta, int count, String nbt) {
			this.id = id;
			this.meta = meta;
			this.count = count;
			this.nbt = nbt;
		}
	}

	public static ITextComponent itemComponent(ItemRef ref) {
		ItemStack stack = stackFor(ref);
		if (stack == null || stack.isEmpty()) return Msg.white(ref.id);
		ITextComponent name = new TextComponentString(stack.getDisplayName());
		name.getStyle().setColor(stack.getRarity().color);
		try {
			NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
			name.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new TextComponentString(tag.toString())));
		} catch (Exception ignored) {
		}
		return name;
	}

	private static ItemStack stackFor(ItemRef ref) {
		try {
			ResourceLocation rl = new ResourceLocation(ref.id);
			if (!ForgeRegistries.ITEMS.containsKey(rl)) return null;
			Item item = ForgeRegistries.ITEMS.getValue(rl);
			if (item == null) return null;
			ItemStack stack = new ItemStack(item, 1, ref.meta);
			if (ref.nbt != null) {
				try {
					stack.setTagCompound(net.minecraft.nbt.JsonToNBT.getTagFromJson(ref.nbt));
				} catch (Exception ignored) {
				}
			}
			return stack;
		} catch (Exception e) {
			return null;
		}
	}

	/** {@code [{id,count}] -> "3x item, 2x item"}. count 0 renders the name alone. */
	public static ITextComponent itemListComponent(List<ItemRef> items) {
		ITextComponent msg = Msg.empty();
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) msg.appendSibling(Msg.white(", "));
			if (items.get(i).count > 0) msg.appendSibling(Msg.white(items.get(i).count + "x "));
			msg.appendSibling(itemComponent(items.get(i)));
		}
		return msg;
	}

	/** Plain text for wallet_history, which stores a string, not a component. */
	public static String itemListText(List<ItemRef> items) {
		StringBuilder out = new StringBuilder();
		for (ItemRef item : items) {
			if (out.length() > 0) out.append(", ");
			if (item.count > 0) out.append(item.count).append("x ");
			ItemStack stack = stackFor(item);
			out.append(stack != null && !stack.isEmpty() ? stack.getDisplayName() : Msg.prettyId(item.id));
		}
		return out.toString();
	}
}
