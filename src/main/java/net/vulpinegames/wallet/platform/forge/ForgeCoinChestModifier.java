package net.vulpinegames.wallet.platform.forge;

//? forge {

/*import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.loot.CoinLoot;

import java.util.function.Supplier;

// Coin loot for chests on Forge 1.20.1, which has no neoforge:add_table. One
// modifier (data/forge/loot_modifiers/global_loot_modifiers.json ->
// wallet:coin_chest) sees every loot roll; which tables pay, and how much, is
// the coinLoot block of config/wallet.json. Lootr fills each player's copy of
// a container through LootTable.fill, so every player gets their own roll.
public class ForgeCoinChestModifier extends LootModifier {
	public static final Supplier<Codec<ForgeCoinChestModifier>> CODEC = Suppliers.memoize(() ->
		RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, ForgeCoinChestModifier::new)));

	public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
		DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Wallet.MOD_ID);

	public static final RegistryObject<Codec<ForgeCoinChestModifier>> COIN_CHEST =
		SERIALIZERS.register("coin_chest", CODEC);

	public ForgeCoinChestModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		// Block breaks and entity deaths run through here too - never pay those,
		// whatever a config regex happens to match.
		if (context.hasParam(LootContextParams.DAMAGE_SOURCE) || context.hasParam(LootContextParams.BLOCK_STATE)) {
			return generatedLoot;
		}
		var tableId = context.getQueriedLootTableId();
		if (tableId == null) return generatedLoot;
		generatedLoot.addAll(CoinLoot.rollChest(tableId.toString(), context.getRandom()));
		return generatedLoot;
	}

	@Override
	public Codec<? extends IGlobalLootModifier> codec() {
		return CODEC.get();
	}
}
*///?}
