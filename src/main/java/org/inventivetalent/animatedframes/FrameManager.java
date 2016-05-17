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

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Synchronized;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.inventivetalent.animatedframes.event.*;
import org.inventivetalent.mapmanager.TimingsHelper;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@ToString
@EqualsAndHashCode
public class FrameManager {

	public static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

	private AnimatedFramesPlugin plugin;

	private File saveDirectory;
	private File indexFile;

	private File imageDirectory;

	@Expose @SerializedName("frames") private final Map<String, AnimatedFrame> frameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	public FrameManager(AnimatedFramesPlugin plugin) {
		this.plugin = plugin;

		try {
			this.saveDirectory = new File(new File(plugin.getDataFolder(), "saves"), "frames");
			if (!this.saveDirectory.exists()) { this.saveDirectory.mkdirs(); }
			this.indexFile = new File(this.saveDirectory, "index.afi");
			if (!this.indexFile.exists()) { this.indexFile.createNewFile(); }

			this.imageDirectory = new File(plugin.getDataFolder(), "images");
			if (!this.imageDirectory.exists()) { this.imageDirectory.mkdirs(); }
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Synchronized
	public AnimatedFrame getFrame(String name) {
		return frameMap.get(name);
	}

	@Synchronized
	public boolean doesFrameExist(String name) {
		return frameMap.containsKey(name);
	}

	@Synchronized
	public AnimatedFrame createFrame(String name, String source, ItemFrame firstFrame, ItemFrame secondFrame) {
		if (frameMap.containsKey(name)) {
			throw new IllegalArgumentException("Frame '" + name + "' already exists");
		}
		JsonObject meta = new JsonObject();
		AsyncFrameCreationEvent creationEvent = new AsyncFrameCreationEvent(name, source, firstFrame, secondFrame, meta);
		Bukkit.getPluginManager().callEvent(creationEvent);
		name = creationEvent.getName();
		source = creationEvent.getSource();

		AnimatedFrame frame = new AnimatedFrame(firstFrame, new Vector3DDouble(firstFrame.getLocation().toVector()), new Vector3DDouble(secondFrame.getLocation().toVector()), name, source);
		frameMap.put(frame.getName(), frame);
		frame.setMeta(meta);

		return frame;
	}

	public void startFrame(AnimatedFrame frame) {
		plugin.frameExecutor.execute(frame);
	}

	@Synchronized
	private void addFrame(String name, AnimatedFrame frame) {
		if (frameMap.containsKey(name)) {
			throw new IllegalArgumentException("Frame '" + name + "' already exists");
		}
		frameMap.put(name, frame);
	}

	public void stopFrame(AnimatedFrame frame) {
		frame.setPlaying(false);
	}

	@Synchronized
	public void removeFrame(AnimatedFrame frame) {
		if (!frameMap.containsKey(frame.getName())) {
			throw new IllegalArgumentException("Frame '" + frame.getName() + "' does not exists");
		}
		frameMap.remove(frame.getName());

		File imageFile = getImageFile(frame.getImageSource());
		if (imageFile != null && imageFile.exists()) {
			imageFile.delete();
		}
	}

	public boolean downloadImage(String source) {
		try {
			File targetFile = getImageFile(source);
			targetFile.delete();
			try {// Try to load from URL
				URL url = new URL(source);
				URLConnection connection = url.openConnection();
				connection.setRequestProperty("User-Agent", "AnimatedFrames/" + plugin.getDescription().getVersion());
				try (InputStream in = connection.getInputStream()) {
					Files.copy(in, targetFile.toPath());
				}
				plugin.getLogger().info("Downloaded '" + source + "' to '" + targetFile + "'.");
				return true;
			} catch (MalformedURLException malformedUrl) {
				try {// Try to load from file instead
					File file = new File(source);
					if (!file.exists()) { throw new FileNotFoundException(); }
					Files.copy(file.toPath(), targetFile.toPath());
					plugin.getLogger().info("Copied '" + source + " to '" + targetFile + "'.");
					return true;
				} catch (FileNotFoundException fileNotFound) {
					plugin.getLogger().log(Level.WARNING, "Source '" + source + "' is no valid URL", malformedUrl);
					plugin.getLogger().log(Level.WARNING, "Source '" + source + "' is no valid File or doesn't exist", fileNotFound);
				}
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not download image from '" + source + "'", e);
		}
		return false;
	}

	public File downloadOrGetImage(String source) {
		File file = getImageFile(source);

		AsyncImageRequestEvent requestEvent = new AsyncImageRequestEvent(source, file, !file.exists());
		Bukkit.getPluginManager().callEvent(requestEvent);

		file = requestEvent.getImageFile();
		if (file.exists()) {// Already downloaded
			if (!requestEvent.isShouldDownload()) {
				return file;
			} else {
				file.delete();
			}
		}

		// Download first
		if (downloadImage(source)) {
			if (!file.exists()) {
				throw new IllegalStateException("Downloaded file doesn't exist");
			}
			return file;
		}
		return null;
	}

	File getImageFile(String source) {
		return new File(imageDirectory, BaseEncoding.base64Url().encode(source.getBytes()));
	}

	@Synchronized
	public Set<String> getFrameNames() {
		return new HashSet<>(frameMap.keySet());
	}

	@Synchronized
	public Set<AnimatedFrame> getFrames() {
		return new HashSet<>(frameMap.values());
	}

	@Synchronized
	public Set<AnimatedFrame> getFramesInWorld(String worldName) {
		Set<AnimatedFrame> frames = new HashSet<>();
		for (AnimatedFrame frame : frameMap.values()) {
			if (frame.getWorldName().equals(worldName)) {
				frames.add(frame);
			}
		}
		return frames;
	}

	@Synchronized
	public int size() {
		return frameMap.size();
	}

	public void writeFramesToFile() {
		if (size() <= 0) {
			plugin.getLogger().info("No frames found");
			return;
		}

//		TimingsHelper.startTiming("AnimatedFrames - writeToFile");

		for (AnimatedFrame frame : getFrames()) {
			plugin.getLogger().fine("Saving '" + frame.getName() + "'...");
			try {
				File saveFile = new File(saveDirectory, URLEncoder.encode(frame.getName(), "UTF-8") + ".afd");
				if (!saveFile.exists()) { saveFile.createNewFile(); }
				try {
					Bukkit.getPluginManager().callEvent(new FrameSaveEvent(frame, saveFile));
				} catch (Throwable throwable) {
					plugin.getLogger().log(Level.WARNING, "Unhandled exception in FrameSaveEvent for '" + frame.getName() + "'", throwable);
				}
				try (Writer writer = new FileWriter(saveFile)) {
					GSON.toJson(frame, writer);
				}
			} catch (IOException e) {
				plugin.getLogger().log(Level.WARNING, "Failed to save Frame '" + frame.getName() + "'", e);
			}
		}

		try {
			try (Writer writer = new FileWriter(indexFile)) {
				new Gson().toJson(getFrameNames(), writer);
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Failed to save Frame-Index file", e);
		}

//		TimingsHelper.stopTiming("AnimatedFrames - writeToFile");
	}

	public void readFramesFromFile() {
//		TimingsHelper.startTiming("AnimatedFrames - readFromFile");

		Set<String> index;
		try {
			try (Reader reader = new FileReader(indexFile)) {
				index = (Set<String>) new Gson().fromJson(reader, HashSet.class);
			}
		} catch (IOException e) {
			TimingsHelper.stopTiming("MapMenu - readFromFile");
			throw new RuntimeException("Failed to load Menu-Index file", e);
		}
		if (index == null) {
			plugin.getLogger().info("No index found > First time startup or data deleted");
			return;
		}

		for (String name : index) {
			try {
				File file = new File(saveDirectory, URLEncoder.encode(name, "UTF-8") + ".afd");
				try (Reader reader = new FileReader(file)) {
					AnimatedFrame loadedFrame = GSON.fromJson(reader, AnimatedFrame.class);
					frameMap.put(loadedFrame.getName(), loadedFrame);
					try {
						Bukkit.getPluginManager().callEvent(new AsyncFrameLoadEvent(file, loadedFrame));
					} catch (Throwable throwable) {
						plugin.getLogger().log(Level.WARNING, "Unhandled exception in FrameLoadEvent for '" + loadedFrame.getName() + "'", throwable);
					}
				}
			} catch (IOException e) {
				plugin.getLogger().log(Level.WARNING, "Failed to load Menu '" + name + "'", e);
			}
		}
		final AtomicInteger startCounter = new AtomicInteger(1);
		final int toStart = size();

		if (AnimatedFramesPlugin.synchronizedStart) {
			long startDelay = (2500 * toStart);
			plugin.getLogger().info("Starting all frames in " + (startDelay / 1000.0) + " seconds.");
			AnimatedFramesPlugin.synchronizedTime = System.currentTimeMillis() + startDelay;
		}
		for (final AnimatedFrame loadedFrame : getFrames()) {
			try {
				Bukkit.getPluginManager().callEvent(new AsyncFrameStartEvent(loadedFrame));
			} catch (Throwable throwable) {
				plugin.getLogger().log(Level.WARNING, "Unhandled exception in FrameStartEvent for '" + loadedFrame.getName() + "'");
			}

			loadedFrame.startCallback = new Callback<Void>() {
				@Override
				public void call(Void aVoid) {
					plugin.getLogger().info("Started '" + loadedFrame.getName() + "' (" + startCounter.getAndIncrement() + "/" + toStart + ")");
				}
			};
			loadedFrame.refresh();
			startFrame(loadedFrame);
			loadedFrame.setPlaying(true);
		}

//		TimingsHelper.stopTiming("AnimatedFrames - readFromFile");
	}

}
