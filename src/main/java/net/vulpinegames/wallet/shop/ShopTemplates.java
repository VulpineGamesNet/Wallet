package net.vulpinegames.wallet.shop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.vulpinegames.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

//? if >1.20.1 {
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
//?}
//? if <=1.20.1 {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
*///?}

/**
 * The item_template column: serialize a chest, read rows back, match stacks.
 * Two legacy shapes exist and both must keep trading:
 *   ATM10: [{id, count, c: "[minecraft:...=...]"}]  (component string, exact match)
 *   DC:    [{id, count, nbt: "{...}"|null}]         (SNBT stored, id-only match)
 * New writes keep the same per-version shape so rollback to the scripts stays
 * possible. Unknown item ids (mod removed from the pack) parse to an id-only
 * entry that matches nothing - the shop reads as out of stock, never a crash.
 */
public final class ShopTemplates {
	private static final Gson GSON = new Gson();
	private static final int MAX_TEMPLATE_JSON = 60000;

	private ShopTemplates() {}

	public static final class Entry {
		public final String id;
		public final int count;
		int remaining;
		ItemStack match; // null -> match by id string alone

		Entry(String id, int count) {
			this.id = id;
			this.count = count;
			this.remaining = count;
		}

		public int remaining() {
			return remaining;
		}
	}

	public record Missing(String id, int count) {}

	public static List<Entry> templateEntries(Level level, String itemTemplateJson, boolean strictMatch) {
		List<Entry> entries = new ArrayList<>();
		try {
			JsonArray arr = GSON.fromJson(itemTemplateJson, JsonArray.class);
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				Entry entry = new Entry(o.get("id").getAsString(), o.get("count").getAsInt());
				if (strictMatch) {
					entry.match = parseMatchStack(level, entry.id, o);
				}
				entries.add(entry);
			}
		} catch (Exception e) {
			Wallet.LOGGER.error("[Wallet] Cannot parse shop template: {}", e.toString());
		}
		return entries;
	}

	/** The stack an entry must equal exactly, or null for id-only matching. */
	private static ItemStack parseMatchStack(Level level, String id, JsonObject o) {
		//? if >1.20.1 {
		String c = o.has("c") && !o.get("c").isJsonNull() ? o.get("c").getAsString() : "";
		if (c.isEmpty()) return null;
		try {
			ItemParser parser = new ItemParser(level.registryAccess());
			ItemParser.ItemResult result = parser.parse(new StringReader(id + c));
			return new ItemStack(result.item(), 1, result.components());
		} catch (Exception e) {
			// A component from a mod that has since been removed no longer parses.
			Wallet.LOGGER.warn("[Wallet] Cannot parse shop item {}{}, matching by id: {}", id, c, e.toString());
			return null;
		}
		//?}
		//? if <=1.20.1 {
		/*String nbt = o.has("nbt") && !o.get("nbt").isJsonNull() ? o.get("nbt").getAsString() : null;
		if (nbt == null) return null;
		try {
			ResourceLocation rl = ResourceLocation.tryParse(id);
			if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl), 1);
			stack.setTag(TagParser.parseTag(nbt));
			return stack;
		} catch (Exception e) {
			Wallet.LOGGER.warn("[Wallet] Cannot parse shop item {} nbt, matching by id: {}", id, e.toString());
			return null;
		}
		*///?}
	}

	public static boolean stackMatches(ItemStack stack, Entry entry) {
		if (entry.match != null) {
			//? if >1.20.1 {
			return ItemStack.isSameItemSameComponents(stack, entry.match);
			//?}
			//? if <=1.20.1 {
			/*return ItemStack.isSameItemSameTags(stack, entry.match);
			*///?}
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry.id);
	}

	/**
	 * How much of `stack` the still-unfilled entries can take. Counts against
	 * them, so calling it once per slot walks the whole template down to zero.
	 */
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
			if (entry.remaining > 0) missing.add(new Missing(entry.id, entry.remaining));
		}
		return missing;
	}

	public static List<Missing> getMissingItems(Level level, ShopInventory inv, String itemTemplateJson, boolean strict) {
		List<Entry> entries = templateEntries(level, itemTemplateJson, strict);
		if (inv == null) return stillMissing(entries);
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack != null && !stack.isEmpty()) claimFromStack(stack, entries);
		}
		return stillMissing(entries);
	}

	/**
	 * Returns the stacks actually taken, so a trade hands over the real items -
	 * components and all. `keep` copies instead of extracting: an infinite shop
	 * hands out the real item without draining the chest.
	 */
	public static List<ItemStack> removeItems(Level level, ShopInventory inv, String itemTemplateJson, boolean strict, boolean keep) {
		List<Entry> entries = templateEntries(level, itemTemplateJson, strict);
		List<ItemStack> removed = new ArrayList<>();
		if (inv == null) return removed;
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack == null || stack.isEmpty()) continue;
			int take = claimFromStack(stack, entries);
			if (take <= 0) continue;
			if (keep) {
				removed.add(stack.copyWithCount(take));
			} else {
				removed.add(inv.extract(i, take));
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

	/** The script's rule: one empty slot per template entry. */
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

	/** Serialize a chest into the per-version legacy shape. "[]" = empty. */
	public static String serializeInventory(Level level, ShopInventory inv) {
		if (inv == null) return "[]";
		JsonArray items = new JsonArray();
		for (int i = 0; i < inv.slots(); i++) {
			ItemStack stack = inv.get(i);
			if (stack == null || stack.isEmpty()) continue;
			JsonObject o = new JsonObject();
			o.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			o.addProperty("count", stack.getCount());
			//? if >1.20.1 {
			o.addProperty("c", componentsOf(level, stack));
			//?}
			//? if <=1.20.1 {
			/*if (stack.getTag() != null) o.addProperty("nbt", stack.getTag().toString());
			else o.add("nbt", com.google.gson.JsonNull.INSTANCE);
			*///?}
			items.add(o);
		}

		String json = GSON.toJson(items);
		// item_template is a TEXT column. A chest of shulkers carries its whole
		// contents in a component, and storing nothing at all would lose the shop -
		// drop the exact-match data instead and match by id, as shops did before.
		if (json.length() > MAX_TEMPLATE_JSON) {
			for (int i = 0; i < items.size(); i++) {
				JsonObject o = items.get(i).getAsJsonObject();
				o.remove("c");
				o.remove("nbt");
			}
			json = GSON.toJson(items);
			Wallet.LOGGER.warn("[Wallet] Shop items too large to store exactly, matching by id only");
		}
		return json;
	}

	//? if >1.20.1 {
	/**
	 * "[minecraft:custom_model_data=719001]" - the same syntax the vanilla item
	 * parser reads back, and the same string KubeJS's getComponentString stored.
	 * "" = no components, which keeps the stored template small.
	 */
	private static String componentsOf(Level level, ItemStack stack) {
		try {
			String full = new ItemInput(stack.getItemHolder(), stack.getComponentsPatch())
				.serialize(level.registryAccess());
			int bracket = full.indexOf('[');
			return bracket == -1 ? "" : full.substring(bracket);
		} catch (Exception e) {
			return "";
		}
	}
	//?}
}
