package net.vulpinegames.wallet.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.chat.ItemText;

import java.util.ArrayList;
import java.util.List;

/**
 * The item_template column: serialize a chest, read rows back, match stacks.
 * 1.12.2 shape (items carry metadata, so it gets its own field):
 *   [{id, meta, count, nbt: "{...}"|null}]
 * Matching:
 *   strictItemMatch (default) and an "nbt" key present -> item + meta + NBT exact
 *     (nbt null = the stack must carry no tag)
 *   otherwise -> item id, plus meta for items with subtypes (wool colours,
 *     dyes); damage on tools is ignored
 * An oversized template drops "nbt" and falls back to the second rule, as the
 * modern builds do. Unknown ids match nothing - the shop reads out of stock.
 */
public final class ShopTemplates {
	private static final Gson GSON = new Gson();
	private static final int MAX_TEMPLATE_JSON = 60000;

	private ShopTemplates() {}

	public static final class Entry {
		public final String id;
		public final int meta;
		public final int count;
		final String nbt;
		int remaining;
		ItemStack match; // exact stack to equal, null -> id/meta matching
		Item item;       // resolved item, null when the id is unknown

		Entry(String id, int meta, int count, String nbt) {
			this.id = id;
			this.meta = meta;
			this.count = count;
			this.nbt = nbt;
			this.remaining = count;
		}
	}

	public static final class Missing {
		public final String id;
		public final int meta;
		public final int count;

		Missing(String id, int meta, int count) {
			this.id = id;
			this.meta = meta;
			this.count = count;
		}
	}

	public static List<Entry> templateEntries(String itemTemplateJson, boolean strictMatch) {
		List<Entry> entries = new ArrayList<>();
		try {
			JsonArray arr = GSON.fromJson(itemTemplateJson, JsonArray.class);
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				String id = o.get("id").getAsString();
				int meta = o.has("meta") ? o.get("meta").getAsInt() : 0;
				boolean hasNbtKey = o.has("nbt");
				String nbt = hasNbtKey && !o.get("nbt").isJsonNull() ? o.get("nbt").getAsString() : null;
				Entry entry = new Entry(id, meta, o.get("count").getAsInt(), nbt);
				entry.item = itemById(id);
				if (strictMatch && hasNbtKey && entry.item != null) {
					entry.match = exactStack(entry);
				}
				entries.add(entry);
			}
		} catch (Exception e) {
			Wallet.LOGGER.error("[Wallet] Cannot parse shop template: {}", e.toString());
		}
		return entries;
	}

	private static Item itemById(String id) {
		try {
			ResourceLocation rl = new ResourceLocation(id);
			return ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static ItemStack exactStack(Entry entry) {
		ItemStack stack = new ItemStack(entry.item, 1, entry.meta);
		if (entry.nbt == null) return stack;
		try {
			stack.setTagCompound(JsonToNBT.getTagFromJson(entry.nbt));
			return stack;
		} catch (Exception e) {
			Wallet.LOGGER.warn("[Wallet] Cannot parse shop item {} nbt, matching by id: {}", entry.id, e.toString());
			return null;
		}
	}

	public static boolean stackMatches(ItemStack stack, Entry entry) {
		if (entry.item == null || stack.getItem() != entry.item) return false;
		if (entry.match != null) {
			return stack.getMetadata() == entry.match.getMetadata()
				&& ItemStack.areItemStackTagsEqual(stack, entry.match);
		}
		return !entry.item.getHasSubtypes() || stack.getMetadata() == entry.meta;
	}

	/** How much of `stack` the still-unfilled entries can take, counted against them. */
	static int claimFromStack(ItemStack stack, List<Entry> entries) {
		int available = stack.getCount();
		int claimed = 0;
		for (Entry entry : entries) {
			if (available <= 0) break;
			if (entry.remaining <= 0 || !stackMatches(stack, entry)) continue;
			int used = Math.min(entry.remaining, available);
			entry.remaining -= used;
			available -= used;
			claimed += used;
		}
		return claimed;
	}

	static List<Missing> stillMissing(List<Entry> entries) {
		List<Missing> missing = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.remaining > 0) missing.add(new Missing(entry.id, entry.meta, entry.remaining));
		}
		return missing;
	}

	public static List<Missing> getMissingItems(ShopInventory inv, String itemTemplateJson, boolean strict) {
		List<Entry> entries = templateEntries(itemTemplateJson, strict);
		if (inv == null) return stillMissing(entries);
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack != null && !stack.isEmpty()) claimFromStack(stack, entries);
		}
		return stillMissing(entries);
	}

	/**
	 * The stacks actually taken, so a trade hands over the real items, NBT and
	 * all. keep = copy instead of extract (infinite shops).
	 */
	public static List<ItemStack> removeItems(ShopInventory inv, String itemTemplateJson, boolean strict, boolean keep) {
		List<Entry> entries = templateEntries(itemTemplateJson, strict);
		List<ItemStack> removed = new ArrayList<>();
		if (inv == null) return removed;
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack == null || stack.isEmpty()) continue;
			int take = claimFromStack(stack, entries);
			if (take <= 0) continue;
			if (keep) {
				ItemStack copy = stack.copy();
				copy.setCount(take);
				removed.add(copy);
			} else {
				ItemStack got = inv.extract(i, take);
				if (!got.isEmpty()) removed.add(got);
			}
		}
		return removed;
	}

	/** Returns the stacks that did not fit, for the caller to hand back. */
	public static List<ItemStack> addItems(ShopInventory inv, List<ItemStack> stacks) {
		List<ItemStack> leftover = new ArrayList<>();
		if (inv == null) return stacks;
		for (ItemStack stack : stacks) {
			ItemStack remaining = inv.insert(stack);
			if (remaining != null && !remaining.isEmpty()) leftover.add(remaining);
		}
		return leftover;
	}

	/**
	 * Insert every stack or none. On a partial fit whatever already went in is
	 * taken back out, so the caller can hand the full list back and unwind the
	 * trade without leaving items behind. Returns the stacks to hand back;
	 * empty means everything was stored.
	 */
	public static List<ItemStack> addAllOrNothing(ShopInventory inv, List<ItemStack> stacks) {
		List<ItemStack> handBack = new ArrayList<>();
		List<ItemStack> stored = new ArrayList<>();
		for (ItemStack stack : stacks) {
			ItemStack remaining = inv.insert(stack.copy());
			int left = remaining == null ? 0 : remaining.getCount();
			if (left > 0) handBack.add(remaining);
			if (stack.getCount() > left) {
				ItemStack in = stack.copy();
				in.setCount(stack.getCount() - left);
				stored.add(in);
			}
		}
		if (handBack.isEmpty()) return handBack;
		for (ItemStack in : stored) handBack.addAll(takeBack(inv, in));
		return handBack;
	}

	/** Extract up to like.getCount() items exactly equal to `like` (item, meta, NBT). */
	private static List<ItemStack> takeBack(ShopInventory inv, ItemStack like) {
		List<ItemStack> got = new ArrayList<>();
		int need = like.getCount();
		for (int i = 0; i < inv.slots() && need > 0; i++) {
			ItemStack s = inv.get(i);
			if (s == null || s.isEmpty() || !ItemStack.areItemsEqual(s, like) || !ItemStack.areItemStackTagsEqual(s, like)) continue;
			ItemStack out = inv.extract(i, Math.min(need, s.getCount()));
			if (!out.isEmpty()) {
				got.add(out);
				need -= out.getCount();
			}
		}
		if (need > 0) Wallet.LOGGER.warn("[Wallet] Could not take back {}x {} from a shop container", need, like.getItem().getRegistryName());
		return got;
	}

	/** One empty slot per template entry, the modern builds' rule. */
	public static boolean chestHasSpace(ShopInventory inv, String itemTemplateJson) {
		if (inv == null) return false;
		int templateSize;
		try {
			templateSize = GSON.fromJson(itemTemplateJson, JsonArray.class).size();
		} catch (Exception e) {
			return false;
		}
		int emptySlots = 0;
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack == null || stack.isEmpty()) emptySlots++;
		}
		return emptySlots >= templateSize;
	}

	/** Serialize a chest into the 1.12.2 template shape. "[]" = empty. */
	public static String serializeInventory(ShopInventory inv) {
		if (inv == null) return "[]";
		JsonArray items = new JsonArray();
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) continue;
			JsonObject o = new JsonObject();
			o.addProperty("id", stack.getItem().getRegistryName().toString());
			o.addProperty("meta", stack.getMetadata());
			o.addProperty("count", stack.getCount());
			if (stack.getTagCompound() != null) o.addProperty("nbt", stack.getTagCompound().toString());
			else o.add("nbt", JsonNull.INSTANCE);
			items.add(o);
		}

		String json = GSON.toJson(items);
		// item_template is a TEXT column; storing nothing would lose the shop, so
		// drop the exact-match data instead and match by id + meta.
		if (json.length() > MAX_TEMPLATE_JSON) {
			for (JsonElement e : items) e.getAsJsonObject().remove("nbt");
			json = GSON.toJson(items);
			Wallet.LOGGER.warn("[Wallet] Shop items too large to store exactly, matching by id only");
		}
		return json;
	}

	/** Template rows as chat references, NBT kept for the hover. */
	public static List<ItemText.ItemRef> refs(String itemTemplateJson) {
		List<ItemText.ItemRef> refs = new ArrayList<>();
		try {
			JsonArray arr = GSON.fromJson(itemTemplateJson, JsonArray.class);
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				String nbt = o.has("nbt") && !o.get("nbt").isJsonNull() ? o.get("nbt").getAsString() : null;
				refs.add(new ItemText.ItemRef(o.get("id").getAsString(),
					o.has("meta") ? o.get("meta").getAsInt() : 0, o.get("count").getAsInt(), nbt));
			}
		} catch (Exception ignored) {
		}
		return refs;
	}

	public static List<ItemText.ItemRef> refs(List<Missing> missing) {
		List<ItemText.ItemRef> refs = new ArrayList<>();
		for (Missing m : missing) refs.add(new ItemText.ItemRef(m.id, m.meta, m.count, null));
		return refs;
	}
}
