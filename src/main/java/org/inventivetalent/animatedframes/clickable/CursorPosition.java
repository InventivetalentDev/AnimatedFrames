package org.inventivetalent.animatedframes.clickable;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.ToString;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.inventivetalent.animatedframes.AnimatedFrame;
import org.inventivetalent.boundingbox.BoundingBox;
import org.inventivetalent.boundingbox.BoundingBoxAPI;
import org.inventivetalent.frameutil.BaseFrameMapAbstract;
import org.inventivetalent.vectors.d2.Vector2DDouble;
import org.inventivetalent.vectors.d2.Vector2DInt;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import java.util.*;

@Data
@ToString(callSuper = true,
		  doNotUseGetters = true)
public class CursorPosition {

	@Expose public final int x, y;
	Vector3DDouble vector;

	public CursorPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public Vector2DInt toVector() {
		return new Vector2DInt(this.x, this.y);
	}

	public static CursorResult calculateRaw(Player player, int cursorDistance) {
		CursorResult result = new CursorResult();

		final Location location = player.getLocation();
		final Vector3DDouble locationVector = new Vector3DDouble(location.toVector());
		final Vector3DDouble direction = new Vector3DDouble(location.getDirection());

		// Get the player's target block to find nearby entities
		Block targetBlock = player.getTargetBlock((Set<Material>) null, cursorDistance);
		if (targetBlock == null || targetBlock.getType() == Material.AIR) { return null; }

		// Get all entities close to the block
		List<Entity> entities = new ArrayList<>(targetBlock.getWorld().getNearbyEntities(targetBlock.getLocation().add(.5, .5, .5), 1, .5, 1));
		List<BoundingBox> boundingBoxes = new ArrayList<>();
		for (Iterator<Entity> iterator = entities.iterator(); iterator.hasNext(); ) {
			Entity entity = iterator.next();
			if (entity.getType() != EntityType.ITEM_FRAME) {
				iterator.remove(); // Filter non-ItemFrame entities
				continue;
			}

			// Get the ItemFrame boundingBox
			BoundingBox boundingBox = BoundingBoxAPI.getAbsoluteBoundingBox(entity);
			double partialX = boundingBox.minX - Math.floor(boundingBox.minX);
			boundingBox = boundingBox.expand(partialX == 0.125 ? 0.125 : 0, 0.125, partialX == 0.125 ? 0 : 0.125);// Expand the bounding box to fit the whole block (not just the center)
			boundingBoxes.add(boundingBox);
		}
		if (entities.isEmpty()) {
			return null; //There are no item-frames on the target block
		}

		Vector3DDouble vectorOnBlock;
		Vector3DDouble lastVector = null;
		double start = Math.max(0, targetBlock.getLocation().distance(player.getLocation()) - 2);
		doubleLoop:
		for (double d = start; d < cursorDistance; d += /*ACCURACY*/0.0125) {
			vectorOnBlock = direction.clone().multiply(d).add(0, player.getEyeHeight(), 0).add(locationVector);
			if (vectorOnBlock.toBukkitLocation(location.getWorld()).getBlock().getType() != Material.AIR) {// Block is solid -> we've hit the target block
				result.blockHit = true;
				break;
			}
			for (BoundingBox boundingBox : boundingBoxes) {
				if (boundingBox.contains(vectorOnBlock)) {
					result.entityHit = true;
					break doubleLoop;// We've hit one of the item frames (hopefully the target one)
				}
			}
			lastVector = vectorOnBlock;
		}
		if (lastVector != null) {
			// Find the frame closest to the target vector
			ItemFrame closestFrame = null;
			double closest = 2.0;
			for (Entity entity : entities) {
				double distance = lastVector.distance(new Vector3DDouble(entity.getLocation().toVector()));
				if (distance < closest) {
					closest = distance;
					closestFrame = (ItemFrame) entity;
				}
			}
			if (closestFrame == null) {
				return null;// This should never happen, since we filtered all the unwanted entities above
			}

			result.vector = lastVector;
			result.found = true;
		}
		return result;
	}

	public static CursorPosition convertVectorToCursor(Vector3DDouble targetVector, BaseFrameMapAbstract mapMenu) {
		if (targetVector == null || mapMenu == null) { return null; }

		double menuWidth = mapMenu.getBlockWidth() * 128.0D;
		double menuHeight = mapMenu.getBlockHeight() * 128.0D;

		Vector3DDouble mapVector = targetVector.clone().subtract(new Vector3DDouble(mapMenu.getBoundingBox().minX, mapMenu.getBoundingBox().minY, mapMenu.getBoundingBox().minZ));
		double vecX = mapVector.getX();
		double vecY = mapVector.getY();
		double vecZ = mapVector.getZ();

		vecX = vecX * menuWidth / mapMenu.getBlockWidth();
		vecZ = vecZ * menuWidth / mapMenu.getBlockWidth();
		vecY = vecY * menuHeight / mapMenu.getBlockHeight();

		vecY = menuHeight - vecY;// Flip Y around to match the image coordinates
		if (mapMenu.getFacing().isHorizontalModInverted()) {
			// Also flip X&Z if the direction is inverted
			vecX = menuWidth - vecX;
			vecZ = menuWidth - vecZ;
		}

		mapVector = new Vector3DDouble(vecX, vecY, vecZ);
		Vector2DDouble vector2d = mapMenu.getFacing().getPlane().to2D(mapVector);

		CursorPosition position = new CursorPosition(vector2d.getX().intValue(), vector2d.getY().intValue());
		position.vector = targetVector;
		return position;
	}

	public static CursorPosition calculateFor(Player player, BaseFrameMapAbstract frame) {

		CursorResult result = calculateRaw(player, 16);
		if (result == null || !result.found) {
			return null;
		}

		//			boolean menuContainsVector = mapMenu.boundingBox.contains(lastVector);
		//			if (!menuContainsVector) { -> disabled, when we hit an entity the vector is not inside of the boundingBox
		//				return null;
		//			}
		boolean contains = frame.getBoundingBox().expand(0.0625).contains(result.vector);
		if (!contains) {
			return null;
		}

		return convertVectorToCursor(result.vector, frame);
	}

	public static CursorMapQueryResult findMenuByCursor(Player player, Collection<AnimatedFrame> frames) {
		CursorMapQueryResult result = new CursorMapQueryResult();
		for (AnimatedFrame frame : frames) {
			CursorPosition position = calculateFor(player, frame);
			if (position != null) {
				result.found = true;
				result.position = position;
				result.clickable = frame;
				return result;
			}
		}
		return result;
	}

	@Data
	public static class CursorResult {
		boolean        found;
		Vector3DDouble vector;
		boolean        blockHit;
		boolean        entityHit;
	}

	@Data
	public static class CursorMapQueryResult {
		boolean        found;
		CursorPosition position;
		Clickable      clickable;
	}

}