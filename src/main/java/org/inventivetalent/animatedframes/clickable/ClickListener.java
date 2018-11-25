package org.inventivetalent.animatedframes.clickable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.inventivetalent.animatedframes.AnimatedFrame;
import org.inventivetalent.animatedframes.AnimatedFramesPlugin;
import org.inventivetalent.mapmanager.event.MapInteractEvent;
import org.inventivetalent.reflection.minecraft.Minecraft;

import java.util.Set;

public class ClickListener implements Listener {

	private AnimatedFramesPlugin plugin;

	public ClickListener(AnimatedFramesPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void on(final MapInteractEvent event) {
//		if (event.getItemFrame().hasMetadata("ANIMATED_FRAMES_META")) {
//			System.out.println("No meta");
//			return;
//		}
		Bukkit.getScheduler().runTask(plugin, new Runnable() {
			@Override
			public void run() {
				if (event.getActionID() == 2) {// interact_at
					return;
				}
				if (event.getHandID() != 0) { return; }

				handleInteract(event.getPlayer(), event, event.getActionID());
			}
		});
	}

	@EventHandler
	public void on(PlayerInteractEvent event) {
		if (Minecraft.VERSION.newerThan(Minecraft.Version.v1_9_R1)) {
			if (event.getHand() != EquipmentSlot.HAND) { return; }
		}

		int actionId = 0;
		switch (event.getAction()) {
			case RIGHT_CLICK_AIR:
			case RIGHT_CLICK_BLOCK:
				actionId = 0;
				break;
			case LEFT_CLICK_AIR:
			case LEFT_CLICK_BLOCK:
				actionId = 1;
				break;
		}
		handleInteract(event.getPlayer(), event, actionId);
	}

	void handleInteract(final Player player, Cancellable cancellable, final int action/* 0 = interact (right-click), 1 = attack (left-click) */) {

		Block targetBlock = player.getTargetBlock((Set<Material>) null, 16);
		if (targetBlock != null && targetBlock.getType() != Material.AIR) {
			Set<AnimatedFrame> frames = plugin.frameManager.getFramesInWorld(player.getWorld().getName());
			frames.removeIf(f -> !f.isClickable());

			final CursorPosition.CursorMapQueryResult queryResult = CursorPosition.findMenuByCursor(player, frames);

			if (queryResult != null && queryResult.isFound()) {
				Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
					@Override
					public void run() {
						queryResult.getClickable().handleClick(player, queryResult.getPosition(), action);
					}
				});

//				cancellable.setCancelled(true);
			}
		}

	}

}
