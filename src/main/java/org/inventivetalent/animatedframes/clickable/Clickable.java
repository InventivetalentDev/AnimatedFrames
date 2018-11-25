package org.inventivetalent.animatedframes.clickable;

import org.bukkit.entity.Player;

public interface Clickable {

	boolean isClickable();

	void handleClick(Player player, CursorPosition position, int action);

}
