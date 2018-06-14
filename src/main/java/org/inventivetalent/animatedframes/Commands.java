/*
 * Copyright 2015-2016 inventivetalent. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and contributors and should not be interpreted as representing official policies,
 *  either expressed or implied, of anybody else.
 */

package org.inventivetalent.animatedframes;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.inventivetalent.pluginannotations.PluginAnnotations;
import org.inventivetalent.pluginannotations.command.Command;
import org.inventivetalent.pluginannotations.command.Completion;
import org.inventivetalent.pluginannotations.command.Permission;
import org.inventivetalent.pluginannotations.message.MessageFormatter;
import org.inventivetalent.pluginannotations.message.MessageLoader;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;

public class Commands {

	static final MessageLoader MESSAGE_LOADER = PluginAnnotations.MESSAGE.newMessageLoader(Bukkit.getPluginManager().getPlugin("AnimatedFrames"), "config.yml", "message.command", null);

	private AnimatedFramesPlugin plugin;

	public Commands(AnimatedFramesPlugin plugin) {
		this.plugin = plugin;
	}

	@Command(name = "animatedframes",
			 aliases = {
					 "af",
					 "afhelp",
					 "framehelp"
			 },
			 usage = "",
			 max = 0,
			 fallbackPrefix = "animatedframes")
	public void helpCommand(final CommandSender sender) {
		sender.sendMessage("§6AnimatedFrames v" + plugin.getDescription().getVersion() + (plugin.updateAvailable ? " §a*Update available" : ""));
		sender.sendMessage(" ");

		int c = 0;
		if (sender.hasPermission("animatedframes.create")) {
			c++;
			sender.sendMessage("§e/afcreate <Name> <Image>");
			sender.sendMessage("§bCreate a new image");
			sender.sendMessage(" ");
		}
		if (sender.hasPermission("animatedframes.remove")) {
			c++;
			sender.sendMessage("§e/afremove <Name>");
			sender.sendMessage("§bRemove an existing image");
			sender.sendMessage(" ");
		}
		if (sender.hasPermission("animatedframes.list")) {
			c++;
			sender.sendMessage("§e/aflist");
			sender.sendMessage("§bGet a list of images");
			sender.sendMessage(" ");
		}

		if (c > 0) {
			sender.sendMessage("§eType §a/help <Command> §efor more information.");
		}
	}

	@Command(name = "framecreate",
			 aliases = {
					 "afcreate",
					 "createframe",
					 "afc"
			 },
			 usage = "<Name> <Image>",
			 description = "Create a new image",
			 min = 2,
			 max = 2,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.create")
	public void frameCreate(final Player sender, final String name, final String image) {
		if (plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("create.error.exists", "create.error.exists"));
			return;
		}

		boolean validImage = false;
		try {
			new URL(image);
			validImage = true;
		} catch (MalformedURLException e) {
			try {
				if (!new File(image).exists()) { throw new FileNotFoundException(); }
				validImage = true;
			} catch (FileNotFoundException e1) {
			}
		}
		if (!validImage) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("create.error.invalidImage", "create.error.invalidImage"));
			return;
		}
		if (!checkImageType(image)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("create.error.unknownType", "create.error.unknownType"));
			// Just a warning -> carry on
		}

		sender.sendMessage("  ");
		sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.first", "create.setup.first"));
		plugin.interactListener.listenForEntityInteract(sender, new Callback<PlayerInteractEntityEvent>() {
			@Override
			public void call(PlayerInteractEntityEvent event) {
				if (event != null && event.getRightClicked().getType() == EntityType.ITEM_FRAME) {
					final ItemFrame firstFrame = (ItemFrame) event.getRightClicked();
					sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.set.first", "create.setup.set.first"));
					sender.sendMessage("  ");

					Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
						@Override
						public void run() {
							sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.second", "create.setup.second"));
							plugin.interactListener.listenForEntityInteract(sender, new Callback<PlayerInteractEntityEvent>() {
								@Override
								public void call(final PlayerInteractEntityEvent event) {
									if (event != null && event.getRightClicked().getType() == EntityType.ITEM_FRAME) {
										final ItemFrame secondFrame = (ItemFrame) event.getRightClicked();
										sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.set.second", "create.setup.set.second"));
										sender.sendMessage("  ");

										sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.complete", "create.setup.complete", new MessageFormatter() {
											@Override
											public String format(String key, String message) {
												return String.format(message, name, image);
											}
										}));
										sender.sendMessage("  ");
										plugin.frameExecutor.execute(new Runnable() {
											@Override
											public void run() {
												sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.loading", "create.setup.loading"));
												final AnimatedFrame frame = plugin.frameManager.createFrame(name, image, firstFrame, secondFrame);
												frame.creator = sender.getUniqueId();

												// Save frame & index
												sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.saving", "create.setup.saving"));
												plugin.frameManager.writeToFile(frame);
												plugin.frameManager.writeIndexToFile();

												frame.refresh();
												plugin.frameManager.startFrame(frame);

												sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.starting", "create.setup.starting"));
												frame.startCallback = new Callback<Void>() {
													@Override
													public void call(Void aVoid) {
														Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
															@Override
															public void run() {
																frame.addViewer(event.getPlayer());
																sender.sendMessage("  ");
																sender.sendMessage(MESSAGE_LOADER.getMessage("create.setup.started", "create.setup.started"));

																// Add players in the world
																Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
																	@Override
																	public void run() {
																		for (Player player : event.getPlayer().getWorld().getPlayers()) {
																			if (player.getUniqueId().equals(event.getPlayer().getUniqueId())) { continue; }// Skip the creator
																			frame.addViewer(player);
																		}
																	}
																}, 20);
															}
														}, 40);
													}
												};
												frame.setPlaying(true);
											}
										});
									}
								}
							});
						}
					}, 10);
				}
			}
		});
	}

	@Command(name = "frameremove",
			 aliases = {
					 "afremove",
					 "removeframe",
					 "afr",
					 "afrem",
			 },
			 usage = "<Name>",
			 description = "Remove an image",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.remove")
	public void frameRemove(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("delete.error.notFound", "delete.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);

		sender.sendMessage(MESSAGE_LOADER.getMessage("delete.stopping", "delete.stopping"));
		plugin.frameManager.stopFrame(frame);
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			@Override
			public void run() {
				frame.clearFrames();
				plugin.frameManager.removeFrame(frame);
				sender.sendMessage(MESSAGE_LOADER.getMessage("delete.removed", "delete.removed"));
			}
		}, 20);
	}

	@Completion(name = "frameremove")
	public void frameRemove(final List<String> completions, final Player sender, final String name) {
		//		if (name == null || name.isEmpty()) {
		for (AnimatedFrame frame : plugin.frameManager.getFrames()) {
			completions.add(frame.getName());
		}
		//		}
	}

	@Command(name = "framelist",
			 aliases = {
					 "aflist",
					 "listframes",
					 "afl"
			 },
			 usage = "",
			 description = "Get a list of frames",
			 max = 0,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.list")
	public void frameList(final Player sender) {
		sender.sendMessage("  ");

		Set<AnimatedFrame> frames = plugin.frameManager.getFrames();
		sender.sendMessage("§eFrames (" + frames.size() + "): ");
		for (AnimatedFrame frame : frames) {
			TextComponent component = new TextComponent(frame.getName());

			component.addExtra(new ComponentBuilder(" (" + (frame.isImageLoaded() ? (frame.isPlaying() ? "playing" : "stopped") : "loading") + ")").color(ChatColor.GRAY).create()[0]);

			Vector3DDouble teleportVector = frame.getBaseVector();
			ClickEvent teleportClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + teleportVector.getX() + " " + teleportVector.getY() + " " + teleportVector.getZ());
			HoverEvent teleportHover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Teleport to " + teleportVector.getX() + "," + teleportVector.getY() + "," + teleportVector.getZ()).color(ChatColor.GRAY).create());
			component.addExtra(new ComponentBuilder(" [Teleport]").color(ChatColor.YELLOW).bold(true).event(teleportClick).event(teleportHover).create()[0]);

			ClickEvent deleteClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/frameremove " + frame.getName());
			component.addExtra(new ComponentBuilder(" [Delete] ").color(ChatColor.RED).event(deleteClick).create()[0]);

			ClickEvent playClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/framestart " + frame.getName());
			ClickEvent pauseClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/framepause " + frame.getName());
			component.addExtra(new ComponentBuilder(frame.isPlaying() ? " [❚❚]" : " [►]").color(frame.isPlaying() ? ChatColor.YELLOW : ChatColor.GREEN).event(frame.isPlaying() ? pauseClick : playClick).create()[0]);

			ClickEvent stopClick = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/framestop " + frame.getName());
			component.addExtra(new ComponentBuilder(" [■]").color(frame.isPlaying() ? ChatColor.RED : ChatColor.GRAY).event(stopClick).create()[0]);

			sender.spigot().sendMessage(component);
		}
	}

	@Command(name = "framestart",
			 aliases = {
					 "afstart",
					 "startframe",
					 "frameresume",
					 "afresume",
					 "resumeframe"
			 },
			 usage = "<Name>",
			 description = "Start a stopped/paused frame",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.start")
	public void frameStart(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("start.error.notFound", "start.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);
		if (frame.isPlaying()) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("start.error.playing", "start.error.playing"));
			return;
		}

		sender.sendMessage(MESSAGE_LOADER.getMessage("start.starting", "start.starting"));
		frame.setPlaying(true);
		plugin.frameManager.startFrame(frame);
	}

	@Command(name = "framepause",
			 aliases = {
					 "afpause",
					 "pauseframe"
			 },
			 usage = "<Name>",
			 description = "Pauses a frame",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.pause")
	public void framePause(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("pause.error.notFound", "pause.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);
		if (!frame.isPlaying()) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("pause.error.notPlaying", "pause.error.notPlaying"));
			return;
		}

		sender.sendMessage(MESSAGE_LOADER.getMessage("pause.pausing", "pause.pausing"));
		plugin.frameManager.stopFrame(frame);
	}

	@Command(name = "framestop",
			 aliases = {
					 "afstop",
					 "stopframe"
			 },
			 usage = "<Name>",
			 description = "Stop a frame",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.stop")
	public void frameStop(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("stop.error.notFound", "stop.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);
		if (!frame.isPlaying()) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("stop.error.notPlaying", "stop.error.notPlaying"));
			return;
		}

		sender.sendMessage(MESSAGE_LOADER.getMessage("stop.stopping", "stop.stopping"));
		plugin.frameManager.stopFrame(frame);
		frame.setCurrentFrame(0);
	}

	@Command(name = "framenext",
			 aliases = {
					 "afnext",
					 "nextframe"
			 },
			 usage = "<Name>",
			 description = "Go to the next frame of the animation",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.nextframe")
	public void frameNext(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("next.error.notFound", "next.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);
		if (frame.isPlaying()) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("next.error.notPaused", "next.error.notPaused"));
			return;
		}

		sender.sendMessage(MESSAGE_LOADER.getMessage("next.success", "next.success"));

		if (frame.getCurrentFrame() >= frame.getLength()) {
			frame.goToFrameAndDisplay(0);
		} else {
			frame.goToFrameAndDisplay(frame.getCurrentFrame() + 1);
		}
	}

	@Command(name = "frameprevious",
			 aliases = {
					 "afprevious",
					 "previousframe",
					 "prevframe"
			 },
			 usage = "<Name>",
			 description = "Go to the previous frame of the animation",
			 min = 1,
			 max = 1,
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.previousframe")
	public void frameprevious(final CommandSender sender, final String name) {
		if (!plugin.frameManager.doesFrameExist(name)) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("previous.error.notFound", "previous.error.notFound"));
			return;
		}
		final AnimatedFrame frame = plugin.frameManager.getFrame(name);
		if (frame.isPlaying()) {
			sender.sendMessage(MESSAGE_LOADER.getMessage("previous.error.notPaused", "previous.error.notPaused"));
			return;
		}

		sender.sendMessage(MESSAGE_LOADER.getMessage("previous.success", "previous.success"));

		if (frame.getCurrentFrame() <= 0) {
			frame.goToFrameAndDisplay(frame.getLength() - 1);
		} else {
			frame.goToFrameAndDisplay(frame.getCurrentFrame() - 1);
		}
	}

	@Command(name = "placeframes",
			 aliases = {
					 "afplace",
					 "frameplace",
					 "generateframes",
					 "afp",
					 "afg"
			 },
			 usage = "",
			 description = "Place item frames",
			 fallbackPrefix = "animatedframes")
	@Permission("animatedframes.place")
	public void placeFrames(final Player sender) {
		sender.sendMessage("  ");
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			@Override
			public void run() {
				sender.sendMessage(MESSAGE_LOADER.getMessage("place.first", "place.first"));
				plugin.interactListener.listenForInteract(sender, new Callback<PlayerInteractEvent>() {
					@Override
					public void call(PlayerInteractEvent event) {
						final Block firstBlock = event.getClickedBlock();
						final BlockFace firstFace = event.getBlockFace();
						sender.sendMessage(MESSAGE_LOADER.getMessage("place.set.first", "place.set.first"));

						Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
							@Override
							public void run() {
								sender.sendMessage("  ");
								sender.sendMessage(MESSAGE_LOADER.getMessage("place.second", "place.second"));
								plugin.interactListener.listenForInteract(sender, new Callback<PlayerInteractEvent>() {
									@Override
									public void call(PlayerInteractEvent event) {
										final Block secondBlock = event.getClickedBlock();
										final BlockFace secondFace = event.getBlockFace();
										sender.sendMessage(MESSAGE_LOADER.getMessage("place.set.second", "place.set.second"));

										if (firstFace != secondFace) {
											sender.sendMessage(MESSAGE_LOADER.getMessage("place.error.face", "place.error.face"));
											return;
										}

										for (int x = firstBlock.getX(); x <= secondBlock.getX(); x++) {
											for (int y = firstBlock.getY(); y <= secondBlock.getY(); y++) {
												for (int z = firstBlock.getZ(); z <= secondBlock.getZ(); z++) {
													Location location = new Location(firstBlock.getWorld(), x, y, z);
													location = location.getBlock().getRelative(secondFace).getLocation();
													ItemFrame frame = location.getWorld().spawn(location, ItemFrame.class);
													frame.setFacingDirection(secondFace.getOppositeFace());
												}
											}
										}

										sender.sendMessage(" ");
										sender.sendMessage(MESSAGE_LOADER.getMessage("place.done", "place.done"));
									}
								});
							}
						}, 10);
					}
				});
			}
		}, 5);
	}

	boolean checkImageType(String url) {
		return url.endsWith(".png") || url.endsWith(".gif") || url.endsWith(".jpg") || url.endsWith(".jpeg");
	}

}
