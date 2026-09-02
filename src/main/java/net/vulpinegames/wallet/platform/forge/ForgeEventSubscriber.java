package net.vulpinegames.wallet.platform.forge;

//? forge {

/*import net.vulpinegames.wallet.Wallet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

@Mod.EventBusSubscriber
public class ForgeEventSubscriber {

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		net.vulpinegames.wallet.command.WalletCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		Wallet.onServerStarted(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		Wallet.onServerStopping(event.getServer());
	}

	// True for real, non-spectator server players only - deployers must not trade.
	private static ServerPlayer realPlayer(net.minecraft.world.entity.player.Player entity) {
		if (entity instanceof ServerPlayer p && !p.isSpectator() && !(p instanceof FakePlayer)) return p;
		return null;
	}

	// HIGHEST + cancel(SUCCESS): shop clicks are consumed before claim mods see
	// them, vanilla skips both item use and the off-hand pass.
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) return;
		ServerPlayer player = realPlayer(event.getEntity());
		if (player == null || !(event.getLevel() instanceof ServerLevel level)) return;
		if (Wallet.onRightClickBlock(player, level, event.getPos(), event.getItemStack())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (event.getHand() != InteractionHand.MAIN_HAND) return;
		ServerPlayer player = realPlayer(event.getEntity());
		if (player == null || event.getLevel().isClientSide()) return;
		if (Wallet.onRightClickItem(player, event.getItemStack())) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		}
	}

	@SubscribeEvent
	public static void onExplosion(ExplosionEvent.Detonate event) {
		if (event.getLevel() instanceof ServerLevel level) {
			Wallet.onExplosionDetonate(level, event.getAffectedBlocks());
		}
	}

	@SubscribeEvent
	public static void onPiston(PistonEvent.Pre event) {
		if (!(event.getLevel() instanceof ServerLevel level)) return;
		if (Wallet.active() == null || Wallet.active().shopCache().size() == 0) return;
		// Retraction of a NON-sticky piston moves nothing, so cancelling it over
		// a shop support block would jam the piston forever - guard only the
		// cases that actually move a block: extension, and sticky retraction.
		if (event.getPistonMoveType() == PistonEvent.PistonMoveType.RETRACT
			&& !level.getBlockState(event.getPos()).is(net.minecraft.world.level.block.Blocks.STICKY_PISTON)) return;
		var resolver = event.getStructureHelper();
		if (resolver == null || !resolver.resolve()) return;
		java.util.List<net.minecraft.core.BlockPos> moved = new java.util.ArrayList<>(resolver.getToPush());
		moved.addAll(resolver.getToDestroy());
		if (Wallet.onPistonMove(level, moved)) {
			event.setCanceled(true);
		}
	}

	// LOWEST, unlike the click handlers: a claim mod's veto must run first, or
	// we delete the shop row for a break that never happens.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!(event.getLevel() instanceof ServerLevel level)) return;
		ServerPlayer player = event.getPlayer() instanceof ServerPlayer p ? p : null;
		if (Wallet.onBlockBreak(player, level, event.getPos(), event.getState())) {
			event.setCanceled(true);
		}
	}
}
*///?}
