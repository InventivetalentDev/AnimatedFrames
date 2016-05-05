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

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

	private AnimatedFramesPlugin plugin;

	public PlayerListener(AnimatedFramesPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void on(final PlayerJoinEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				for (AnimatedFrame frame : plugin.frameManager.getFrames()) {
					frame.addViewer(event.getPlayer());
				}
			}
		}, 20);
	}

	@EventHandler
	public void on(PlayerQuitEvent event) {
		for (AnimatedFrame frame : plugin.frameManager.getFrames()) {
			frame.removeViewer(event.getPlayer());
		}
	}

	@EventHandler
	public void on(final PlayerChangedWorldEvent event) {
		for (AnimatedFrame frame : plugin.frameManager.getFramesInWorld(event.getFrom().getName())) {
			frame.removeViewer(event.getPlayer());
		}
		final String newWorld = event.getPlayer().getWorld().getName();
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				for (AnimatedFrame frame : plugin.frameManager.getFramesInWorld(newWorld)) {
					frame.addViewer(event.getPlayer());
				}
			}
		}, 20);
	}

}
