package net.vulpinegames.wallet.chat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Item names in chat, same rendering as the scripts:
 * itemChatLinks config on -> emit the chat-link token a client-side mod
 * replaces with the item's icon (ATM10's pack ships it; players without it
 * read the raw token). Off (default, DC) -> vanilla show_item hover.
 * Note: a line carrying a token is rebuilt client-side, which drops click
 * events on its other parts - rows with a [TP] link render plain names instead.
 */
public final class ItemText {
	private ItemText() {}

	private static boolean links() {
		net.vulpinegames.wallet.WalletServer ks = net.vulpinegames.wallet.Wallet.active();
		return ks != null && ks.config().itemChatLinks;
	}

	public record ItemRef(String id, int count) {}

	public static MutableComponent itemComponent(String itemId) {
		ItemStack stack = stackFor(itemId);
		// An id from a mod that has since been removed resolves to air - neither
		// the client mod nor a hover can render that, fall back to the raw id.
		if (stack == null || stack.isEmpty()) return Msg.white(itemId);

		if (links()) return Msg.white("[JEI:v1:item_stack;" + itemId + "]");

		// getDisplayName hardcodes square brackets, but its *style* carries the
		// show_item hover - keep the style, supply our own unbracketed text.
		MutableComponent name = stack.getHoverName().copy();
		try {
			return Msg.empty().append(name).setStyle(stack.getDisplayName().getStyle());
		} catch (Exception e) {
			return name;
		}
	}

	private static ItemStack stackFor(String itemId) {
		try {
			ResourceLocation rl = ResourceLocation.tryParse(itemId);
			if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
			return new ItemStack(BuiltInRegistries.ITEM.get(rl));
		} catch (Exception e) {
			return null;
		}
	}

	/** {@code [{id,count}] -> "3x item, 2x item"}. count 0 renders the name alone. */
	public static MutableComponent itemListComponent(List<ItemRef> items) {
		MutableComponent msg = Msg.empty();
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) msg.append(Msg.white(", "));
			if (items.get(i).count() > 0) msg.append(Msg.white(items.get(i).count() + "x "));
			msg.append(itemComponent(items.get(i).id()));
		}
		return msg;
	}

	/** Plain text for wallet_history, which stores a string, not a component. */
	public static String itemListText(List<ItemRef> items) {
		StringBuilder out = new StringBuilder();
		for (ItemRef item : items) {
			if (!out.isEmpty()) out.append(", ");
			if (item.count() > 0) out.append(item.count()).append("x ");
			out.append(Msg.prettyId(item.id()));
		}
		return out.toString();
	}
}
