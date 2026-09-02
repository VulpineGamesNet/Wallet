package net.vulpinegames.wallet.platform.fabric;

//? fabric {

/*import net.vulpinegames.wallet.Wallet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

// NOTE: Fabric API has no explosion-blocklist or piston event, so the
// explosion/piston shop protection is forge-like-only. Fabric is a build-only
// target today; revisit with a mixin if it ever ships to a server.
public class FabricEventSubscriber {

	// Fabric has no event priority - an explicit early phase runs our handlers
	// before default-phase listeners (claim mods), mirroring HIGHEST on Forge.
	// tryParse: same public API on both targets, no stonecutter nesting needed.
	private static final ResourceLocation EARLY = ResourceLocation.tryParse("wallet:early");
	private static final ResourceLocation LATE = ResourceLocation.tryParse("wallet:late");

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(Wallet::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(Wallet::onServerStopping);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			net.vulpinegames.wallet.command.WalletCommand.register(dispatcher));

		UseBlockCallback.EVENT.addPhaseOrdering(EARLY, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(EARLY, (player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp) || sp.isSpectator() || !(level instanceof ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			if (Wallet.onRightClickBlock(sp, sl, hitResult.getBlockPos(), player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
				return InteractionResultHolder.pass(player.getItemInHand(hand));
			}
			if (Wallet.onRightClickItem(sp, player.getItemInHand(hand))) {
				return InteractionResultHolder.success(player.getItemInHand(hand));
			}
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		});

		// Late phase, unlike the click handlers: a claim mod's veto must run
		// first, or we delete the shop row for a break that never happens.
		PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, LATE);
		PlayerBlockBreakEvents.BEFORE.register(LATE, (level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return true;
			return !Wallet.onBlockBreak(sp, sl, pos, state);
		});
	}
}
*///?}
