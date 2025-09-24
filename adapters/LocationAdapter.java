package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.model.Location;
import net.runelite.api.coords.WorldPoint;
import lombok.Getter;

/**
 * Adapter that bridges RuneLite's WorldPoint to Elvarg's Location.
 * Provides bidirectional conversion between the two coordinate systems.
 */
@Getter
public class LocationAdapter extends Location {

    private final WorldPoint worldPoint;

    /**
     * Creates a LocationAdapter from RuneLite WorldPoint.
     */
    public LocationAdapter(WorldPoint worldPoint) {
        super(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());
        this.worldPoint = worldPoint;
    }

    /**
     * Creates a LocationAdapter from x, y, z coordinates.
     */
    public LocationAdapter(int x, int y, int z) {
        super(x, y, z);
        this.worldPoint = new WorldPoint(x, y, z);
    }

    /**
     * Converts a RuneLite WorldPoint to Elvarg Location.
     */
    public static LocationAdapter fromWorldPoint(WorldPoint worldPoint) {
        if (worldPoint == null) {
            return null;
        }
        return new LocationAdapter(worldPoint);
    }

    /**
     * Converts this adapter back to a RuneLite WorldPoint.
     */
    public WorldPoint toWorldPoint() {
        return worldPoint;
    }

    @Override
    public int getX() {
        return worldPoint != null ? worldPoint.getX() : super.getX();
    }

    @Override
    public int getY() {
        return worldPoint != null ? worldPoint.getY() : super.getY();
    }

    @Override
    public int getZ() {
        return worldPoint != null ? worldPoint.getPlane() : super.getZ();
    }

    @Override
    public Location clone() {
        return new LocationAdapter(worldPoint);
    }

    public Location transform(int x, int y, int z) {
        return new LocationAdapter(
            new WorldPoint(
                this.getX() + x,
                this.getY() + y,
                this.getZ() + z
            )
        );
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Location)) {
            return false;
        }
        Location loc = (Location) other;
        return loc.getX() == getX() && loc.getY() == getY() && loc.getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return worldPoint != null ? worldPoint.hashCode() : super.hashCode();
    }
}