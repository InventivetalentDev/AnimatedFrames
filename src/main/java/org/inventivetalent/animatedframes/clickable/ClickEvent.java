package org.inventivetalent.animatedframes.clickable;

import com.google.gson.annotations.Expose;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.inventivetalent.vectors.Vectors;
import org.inventivetalent.vectors.d2.Vector2DInt;

@Data
public class ClickEvent {

	@Expose public Vector2DInt min;
	@Expose public Vector2DInt max;

	@Expose public String action;

	public ClickEvent(Vector2DInt vectorA, Vector2DInt vectorB, String action) {
		this.min = Vectors.min(vectorA, vectorB);
		this.max = Vectors.max(vectorA, vectorB);

		if (action.startsWith("/")) {
			action = action.substring(1);
		}
		this.action = action;
	}

	public boolean contains(Vector2DInt pos) {
		return pos.getX() > this.min.getX() && pos.getY() > this.min.getY() && pos.getX() < this.max.getX() && pos.getY() < this.max.getY();
	}

	public boolean contains(int x, int y) {
		return x > this.min.getX() && y > this.min.getY() && x < this.max.getX() && y < this.max.getY();
	}

	public void executeFor(Player player) {
		//TODO: might want to add more action types
		Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AnimatedFrames"),()->{
			player.performCommand(this.action);
		});
	}

}
