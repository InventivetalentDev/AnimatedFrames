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

import com.google.common.primitives.Ints;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.inventivetalent.animatedframes.decoder.GifDecoder;
import org.inventivetalent.frameutil.BaseFrameMapAbstract;
import org.inventivetalent.mapmanager.ArrayImage;
import org.inventivetalent.mapmanager.MapManagerPlugin;
import org.inventivetalent.mapmanager.TimingsHelper;
import org.inventivetalent.mapmanager.controller.MapController;
import org.inventivetalent.mapmanager.controller.MultiMapController;
import org.inventivetalent.mapmanager.manager.MapManager;
import org.inventivetalent.mapmanager.wrapper.MapWrapper;
import org.inventivetalent.mapmanager.wrapper.MultiWrapper;
import org.inventivetalent.reflection.minecraft.Minecraft;
import org.inventivetalent.vectors.d2.Vector2DDouble;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Data
@ToString(doNotUseGetters = true,
		  callSuper = true)
public class AnimatedFrame extends BaseFrameMapAbstract implements Runnable {

	static final int[][] NULL_INT_ARRAY = new int[0][0];

	@Expose private String name;
	@Expose private String imageSource;

	/* Block width, to be multiplied by 128 */
	@Expose private int width;
	@Expose private int height;
	private         int length;

	@Expose public UUID creator;

	@Expose public JsonObject meta = new JsonObject();

	protected         int[][]      itemFrameIds = NULL_INT_ARRAY;
	@Expose protected UUID[][]     itemFrameUUIDs;
	private           int[]        frameDelays  = new int[0];
	private           MapWrapper[] mapWrappers;

	protected int delayTicks = 0;

	private final Object    worldPlayersLock = new Object[0];
	private       Set<UUID> worldPlayers     = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

	private AnimatedFramesPlugin plugin       = (AnimatedFramesPlugin) Bukkit.getPluginManager().getPlugin("AnimatedFrames");
	private boolean              imageLoaded  = false;
	private boolean              playing      = false;
	private int                  currentFrame = 0;

	public Callback<Void> startCallback;

	private long timeSinceLastRefresh = 950;

	AnimatedFrame() {
		super();
	}

	public AnimatedFrame(ItemFrame baseFrame, Vector3DDouble firstCorner, Vector3DDouble secondCorner, String name, String imageSource, int width, int height) {
		super(baseFrame, firstCorner, secondCorner);
		this.name = name;
		this.imageSource = imageSource;
		this.width = width;
		this.height = height;
	}

	public AnimatedFrame(ItemFrame baseFrame, Vector3DDouble firstCorner, Vector3DDouble secondCorner, String name, String imageSource) {
		super(baseFrame, firstCorner, secondCorner);
		this.name = name;
		this.imageSource = imageSource;
		this.width = getBlockWidth();
		this.height = getBlockHeight();
	}

	@Override
	public void run() {
		if (!imageLoaded) {
			MapManager mapManager = ((MapManagerPlugin) Bukkit.getPluginManager().getPlugin("MapManager")).getMapManager();
			try {
				File cacheDir = new File(new File(plugin.getDataFolder(), "cache"), this.name);
				if (!cacheDir.exists()) {
					cacheDir.mkdirs();

					plugin.getLogger().info("Generating image data for " + getName() + "...");

					File file = plugin.frameManager.downloadOrGetImage(this.imageSource);
					GifDecoder decoder = new GifDecoder();
					decoder.read(new FileInputStream(file));

					if ((this.length = decoder.getFrameCount()) <= 0) {
						plugin.getLogger().info("Animation length for '" + getName() + "' is zero. Creating non-animated image.");
						this.length = 1;

						BufferedImage image = ImageIO.read(file);
						if (image == null) {
							throw new RuntimeException("Failed to read the given image. Please make sure you're using a valid source");
						}
						image = scaleImage(image);
						MapWrapper mapWrapper = mapManager.wrapMultiImage(image, this.height, this.width);
						this.frameDelays = new int[] { 500 };
						this.mapWrappers = new MapWrapper[] { mapWrapper };
						image.flush();

						File cacheFile = new File(cacheDir, this.name + "_0.afc");
						cacheFile.createNewFile();
						try (FileOutputStream out = new FileOutputStream(cacheFile)) {
							out.write(Ints.toByteArray(500));
							ArrayImage.writeMultiToSream(((MultiWrapper) mapWrapper).getMultiContent(), out);
						}
					} else {
						this.frameDelays = new int[this.length];
						this.mapWrappers = new MapWrapper[this.length];
						for (int i = 0; i < this.length; i++) {
							plugin.getLogger().info("Generating Frame " + (i + 1) + "/" + this.length + " for " + getName() + "...");

							BufferedImage image = scaleImage(decoder.getFrame(i));
							int delay = decoder.getDelay(i);
							if (delay == 0) {
								plugin.getLogger().warning("Frame has no delay information, falling back to default (" + plugin.defaultDelay + ")");
								delay = plugin.defaultDelay;
							}
							this.frameDelays[i] = delay;
							MapWrapper wrapper = mapManager.wrapMultiImage(image, this.height, this.width);
							this.mapWrappers[i] = wrapper;
							image.flush();

							File cacheFile = new File(cacheDir, this.name + "_" + i + ".afc");
							cacheFile.createNewFile();
							try (FileOutputStream out = new FileOutputStream(cacheFile)) {
								out.write(Ints.toByteArray(delay));
								ArrayImage.writeMultiToSream(((MultiWrapper) wrapper).getMultiContent(), out);
							}
						}
					}

					// Reset all images
					for (Object object : decoder.frames) {
						((GifDecoder.GifFrame) object).image.flush();
					}
					decoder.frames.clear();

				} else {
					plugin.getLogger().info("Reading " + getName() + " from cache...");

					String[] fileList = cacheDir.list();
					this.length = fileList.length;
					this.frameDelays = new int[this.length];
					this.mapWrappers = new MapWrapper[this.length];

					for (int i = 0; i < this.length; i++) {
						plugin.getLogger().info("Reading Frame " + (i + 1) + "/" + this.length + " of " + getName() + "...");

						File cacheFile = new File(cacheDir, this.name + "_" + i + ".afc");
						cacheFile.createNewFile();
						try (FileInputStream in = new FileInputStream(cacheFile)) {
							byte[] lengthBytes = new byte[4];
							in.read(lengthBytes, 0, 4);
							this.frameDelays[i] = Ints.fromByteArray(lengthBytes);

							ArrayImage[][] images = ArrayImage.readMultiFromStream(in);
							this.mapWrappers[i] = mapManager.wrapMultiImage(images);
						} catch (IOException readE) {
							throw new RuntimeException("Your cached frame data appears to be invalid. Please delete the plugins/AnimatedFrames/cache directory and restart your server", readE);
						}
					}
				}

				imageLoaded = true;
			} catch (IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Failed to load image '" + getName() + "'", e);
				throw new RuntimeException("Failed to load image");
			}
		}

		while (!this.playing) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				plugin.getLogger().warning("playing-delay for '" + getName() + "' has been interrupted");
				return;
			}
		}

		if (AnimatedFramesPlugin.synchronizedStart) {
			while (System.currentTimeMillis() < AnimatedFramesPlugin.synchronizedTime) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					plugin.getLogger().warning("synchronized start delay for '" + getName() + "' has been interrupted");
					return;
				}
			}
		}

		while (this.playing && this.plugin.isEnabled()) {
			if (startCallback != null) {
				startCallback.call(null);
				startCallback = null;
			}

			if (timeSinceLastRefresh++ > 10000) {
				timeSinceLastRefresh = 0;
				refresh();
			}
			if (delayTicks++ >= this.frameDelays[this.currentFrame]) {
				delayTicks = 0;
				if (Bukkit.getOnlinePlayers().isEmpty()) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						plugin.getLogger().warning("Animation thread for " + getName() + " interrupted");
					}
					continue;
				}

				displayCurrentFrame();

				this.currentFrame++;
				if (this.currentFrame >= this.length) { this.currentFrame = 0; }
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				plugin.getLogger().log(Level.WARNING, "Frame interrupted", e);
			}

		}
	}

	private void displayCurrentFrame() {
		MultiMapController controller = ((MultiMapController) this.mapWrappers[this.currentFrame].getController());
		for (Iterator<UUID> iterator = this.worldPlayers.iterator(); iterator.hasNext(); ) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(iterator.next());
			Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;
			if (player != null) {
				if (player.getWorld().getName().equals(worldName)) {
					if (player.getLocation().distanceSquared(baseVector.toBukkitLocation(getWorld())) < plugin.maxAnimateDistanceSquared) {
						controller.showInFrames(player.getPlayer(), this.itemFrameIds);
					}
				}
			} else {
				iterator.remove();
				if (offlinePlayer != null) {
					for (MapWrapper wrapper : this.mapWrappers) {
						wrapper.getController().removeViewer(offlinePlayer);
					}
				}
			}
		}
	}

	public void goToFrameAndDisplay(int frame) {
		if (frame < 0) { throw new IllegalArgumentException("frame must be >= 0"); }
		if (frame > this.length) { throw new IllegalArgumentException("frame can't be higher than the animation length"); }
		this.currentFrame = frame;
		displayCurrentFrame();
	}

	BufferedImage scaleImage(BufferedImage original) {
		int type = original.getType();
		if (plugin.fixImageTypes) {
			if (type == 0) {
				type = 5;
			}
		}

		BufferedImage scaledImage = new BufferedImage(128 * this.width, 128 * this.height, type);
		Graphics scaledGraphics = scaledImage.getGraphics();

		Image instance = original.getScaledInstance(128 * this.width, 128 * this.height, Image.SCALE_FAST);
		scaledGraphics.drawImage(instance, 0, 0, null);

		instance.flush();
		scaledGraphics.dispose();
		return scaledImage;
	}

	public void refresh() {
		refreshFrames();
	}

	public void addViewer(Player player) {
		if (this.mapWrappers != null) {
			for (MapWrapper wrapper : mapWrappers) {
				if (wrapper == null) {
					plugin.getLogger().warning("Null-element in MapWrapper array of " + getName());
					continue;
				}
				MapController controller = wrapper.getController();
				controller.addViewer(player);
				controller.sendContent(player);
			}
		}
		synchronized (this.worldPlayersLock) {
			this.worldPlayers.add(player.getUniqueId());
		}
	}

	public void removeViewer(OfflinePlayer player) {
		boolean empty;
		synchronized (this.worldPlayersLock) {
			this.worldPlayers.remove(player.getUniqueId());
			empty = this.worldPlayers.isEmpty();
		}
		if (this.mapWrappers != null) {
			for (MapWrapper wrapper : mapWrappers) {
				if (wrapper == null) {
					plugin.getLogger().warning("Null-element in MapWrapper array of " + getName());
					continue;
				}
				MapController controller = wrapper.getController();
				if (empty) {
					controller.clearViewers();
				} else {
					controller.removeViewer(player);
				}
			}
		}
	}

	public void refreshFrames() {
		final Plugin plugin = Bukkit.getPluginManager().getPlugin("AnimatedFrames");
		if (!plugin.isEnabled()) { return; }
		Bukkit.getScheduler().runTask(plugin, new Runnable() {
			@Override
			public void run() {
				TimingsHelper.startTiming("AnimatedFrames - [" + getName() + "] refreshItemFrames");

				final World world = getWorld();
				if (world.getPlayers().isEmpty()) {
					itemFrameIds = NULL_INT_ARRAY;
				} else {
					itemFrameIds = new int[width][height];
					itemFrameUUIDs = new UUID[width][height];

					Vector2DDouble startVector = minCorner2d;

					Collection<? extends Entity> entities;
					if (Minecraft.VERSION.olderThan(Minecraft.Version.v1_8_R2)) {
						entities = world.getEntitiesByClass(ItemFrame.class);
					} else {
						int size = Math.max(width, height);
						entities = world.getNearbyEntities(baseVector.toBukkitLocation(world), size, size, size);
					}
					for (Entity entity : entities) {
						if (entity instanceof ItemFrame) {
							if (((ItemFrame) entity).getFacing() == facing.getFrameDirection()) {
								if (boundingBox.expand(0.1).contains(new Vector3DDouble(entity.getLocation()))) {
									for (int y1 = 0; y1 < getBlockHeight(); y1++) {
										for (int x1 = 0; x1 < getBlockWidth(); x1++) {
											int x = facing.isHorizontalModInverted() ? (getBlockWidth() - 1 - x1) : x1;
											int y = facing.isVerticalModInverted() ? (getBlockHeight() - 1 - y1) : y1;
											Vector3DDouble vector3d = facing.getPlane().to3D(startVector.add(x, y), baseVector.getX(), baseVector.getZ(), baseVector.getY());
											if (entity.getLocation().getBlockZ() == vector3d.getZ().intValue()) {
												if (entity.getLocation().getBlockX() == vector3d.getX().intValue()) {
													if (entity.getLocation().getBlockY() == vector3d.getY().intValue()) {
														itemFrameIds[x1][y1] = entity.getEntityId();
														itemFrameUUIDs[x1][y1] = entity.getUniqueId();

														entity.setMetadata("ANIMATED_FRAMES_META", new FixedMetadataValue(plugin, AnimatedFrame.this));
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
				TimingsHelper.stopTiming("AnimatedFrames - [" + getName() + "] refreshItemFrames");
			}
		});
	}

	public void clearFrames() {
		if (this.mapWrappers != null) {
			for (MapWrapper wrapper : this.mapWrappers) {
				for (UUID uuid : worldPlayers) {
					Player player = Bukkit.getPlayer(uuid);
					if (player != null) {
						((MultiMapController) wrapper.getController()).clearFrames(player, this.itemFrameIds);
					}
				}
				wrapper.getController().clearViewers();
			}
			for (int[] iA : this.itemFrameIds) {
				for (int i : iA) {
					ItemFrame itemFrame = MapManagerPlugin.getItemFrameById(getWorld(), i);
					if (itemFrame != null) {
						itemFrame.removeMetadata("ANIMATED_FRAMES_META", plugin);
					}
				}
			}
		}
	}

	protected MapWrapper[] getWrappers() {
		return this.mapWrappers;
	}

	protected void setContent(MapWrapper[] wrappers, int[] delays) {
		this.length = wrappers.length;
		this.frameDelays = delays;
		this.currentFrame = Math.min(this.currentFrame, this.length - 1);
		this.mapWrappers = wrappers;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) { return true; }
		if (o == null || getClass() != o.getClass()) { return false; }
		if (!super.equals(o)) { return false; }

		AnimatedFrame frame = (AnimatedFrame) o;

		if (width != frame.width) { return false; }
		if (height != frame.height) { return false; }
		if (name != null ? !name.equals(frame.name) : frame.name != null) { return false; }
		return imageSource != null ? imageSource.equals(frame.imageSource) : frame.imageSource == null;

	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (name != null ? name.hashCode() : 0);
		result = 31 * result + (imageSource != null ? imageSource.hashCode() : 0);
		result = 31 * result + width;
		result = 31 * result + height;
		return result;
	}
}
