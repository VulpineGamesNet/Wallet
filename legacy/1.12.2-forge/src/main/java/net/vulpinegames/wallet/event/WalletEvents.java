package net.vulpinegames.wallet.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.vulpinegames.wallet.Wallet;
import net.vulpinegames.wallet.WalletServer;
import net.vulpinegames.wallet.coin.CoinInteractions;
import net.vulpinegames.wallet.shop.ShopInteractions;
import net.vulpinegames.wallet.shop.ShopProtection;
import net.vulpinegames.wallet.shop.SignShop;

/** Forge event hooks - the 1.12.2 counterpart of the modern ForgeEventSubscriber. */
public final class WalletEvents {

	/** Real, non-spectator server players only - deployers and fake players must not trade. */
	private static EntityPlayerMP realPlayer(EntityPlayer entity) {
		if (entity instanceof EntityPlayerMP && !(entity instanceof FakePlayer) && !entity.isSpectator()) {
			return (EntityPlayerMP) entity;
		}
		return null;
	}

	private static void consume(PlayerInteractEvent event, EntityPlayerMP player) {
		event.setCanceled(true);
		if (event instanceof PlayerInteractEvent.RightClickBlock) {
			((PlayerInteractEvent.RightClickBlock) event).setCancellationResult(EnumActionResult.SUCCESS);
		} else if (event instanceof PlayerInteractEvent.RightClickItem) {
			((PlayerInteractEvent.RightClickItem) event).setCancellationResult(EnumActionResult.SUCCESS);
		}
		// The client already predicted the click (a block placed, a stack
		// shrunk); resend the inventory so nothing ghosts.
		player.sendContainerToPlayer(player.inventoryContainer);
	}

	// HIGHEST + cancel: shop clicks are consumed before anything else sees them.
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		World world = event.getWorld();
		if (world.isRemote) return;
		WalletServer ks = Wallet.active();
		if (ks == null) return;
		BlockPos pos = event.getPos();

		// Chest-open protection runs for both hands and for spectators and fake
		// players too - none of them may slip into a shop chest.
		if (event.getEntityPlayer() instanceof EntityPlayerMP
			&& ShopProtection.onRightClickShopChest(ks, (EntityPlayerMP) event.getEntityPlayer(), world, pos)) {
			event.setCanceled(true);
			event.setCancellationResult(EnumActionResult.FAIL);
			return;
		}

		if (event.getHand() != EnumHand.MAIN_HAND) return;
		EntityPlayerMP player = realPlayer(event.getEntityPlayer());
		if (player == null) return;
		ItemStack held = event.getItemStack();
		// A coin never interacts with blocks.
		if (CoinInteractions.shouldBlockCoinUse(held)) {
			consume(event, player);
			return;
		}
		if (SignShop.isWallSign(world.getBlockState(pos))
			&& ShopInteractions.onRightClickWallSign(ks, player, world, pos)) {
			consume(event, player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) return;
		WalletServer ks = Wallet.active();
		EntityPlayerMP player = realPlayer(event.getEntityPlayer());
		if (ks == null || player == null) return;
		if (CoinInteractions.onRightClickItem(ks, player, event.getItemStack())) {
			consume(event, player);
		}
	}

	@SubscribeEvent
	public void onExplosion(ExplosionEvent.Detonate event) {
		WalletServer ks = Wallet.active();
		if (ks == null || event.getWorld().isRemote) return;
		ShopProtection.filterExplosion(ks, event.getWorld(), event.getAffectedBlocks());
	}

	// LOWEST, unlike the click handlers: another mod's veto must run first, or
	// the shop row is deleted for a break that never happens.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onBlockBreak(BlockEvent.BreakEvent event) {
		WalletServer ks = Wallet.active();
		if (ks == null || event.getWorld().isRemote) return;
		EntityPlayerMP player = event.getPlayer() instanceof EntityPlayerMP ? (EntityPlayerMP) event.getPlayer() : null;
		if (ShopProtection.onBlockBreak(ks, player, event.getWorld(), event.getPos(), event.getState())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onBlockPlace(BlockEvent.PlaceEvent event) {
		WalletServer ks = Wallet.active();
		if (ks == null || event.getWorld().isRemote || !(event.getPlayer() instanceof EntityPlayerMP)) return;
		EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
		if (ShopProtection.onPlaceNearShop(ks, player, event.getWorld(), event.getPos(), event.getPlacedBlock())) {
			event.setCanceled(true);
			player.sendContainerToPlayer(player.inventoryContainer);
		}
	}
}
