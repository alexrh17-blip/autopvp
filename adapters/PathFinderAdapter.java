package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.collision.RegionManager;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.movement.path.PathFinder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * Adapter that bridges RuneLite's pathfinding system to Elvarg's PathFinder.
 * Provides collision detection, movement calculation, and line-of-sight checks.
 */
@Slf4j
public class PathFinderAdapter {

    private final Client client;

    // Direction constants from PathFinder
    public static final int WEST = 0x1280108, EAST = 0x1280180, SOUTH = 0x1280102,
            NORTH = 0x1280120, SOUTHEAST = 0x1280183, SOUTHWEST = 0x128010e,
            NORTHEAST = 0x12801e0, NORTHWEST = 0x1280138;

    public PathFinderAdapter(Client client) {
        this.client = client;
    }

    /**
     * Check if two locations are diagonal to each other.
     */
    public static boolean isDiagonalLocation(Mobile attacker, Mobile defender) {
        return PathFinder.isDiagonalLocation(attacker, defender);
    }

    /**
     * Check if two locations are in diagonal block.
     */
    public static boolean isInDiagonalBlock(Location attacker, Location defender) {
        return PathFinder.isInDiagonalBlock(attacker, defender);
    }

    /**
     * Calculate a walk route for a mobile entity.
     */
    public static void calculateWalkRoute(Mobile entity, int destX, int destY) {
        PathFinder.calculateWalkRoute(entity, destX, destY);
    }

    /**
     * Calculate combat route to a target.
     */
    public static void calculateCombatRoute(Mobile attacker, Mobile target) {
        PathFinder.calculateCombatRoute(attacker, target);
    }

    /**
     * Calculate entity route to a specific location.
     */
    public static void calculateEntityRoute(Mobile entity, int destX, int destY) {
        PathFinder.calculateEntityRoute(entity, destX, destY);
    }

    /**
     * Get tiles exactly a given distance away from a center point.
     */
    public static List<Location> getTilesForDistance(Location center, int distance) {
        return PathFinder.getTilesForDistance(center, distance);
    }

    /**
     * Find the closest attackable tile for ranged/magic combat.
     */
    public static Location getClosestAttackableTile(Mobile attacker, Mobile defender, int maxDistance) {
        return PathFinder.getClosestAttackableTile(attacker, defender, maxDistance);
    }

    /**
     * Check if a projectile can travel between two locations.
     * Uses RuneLite's collision data to verify line of sight.
     */
    public boolean canProjectileAttack(Location from, Location to) {
        // Convert Elvarg locations to RuneLite world points
        WorldPoint fromPoint = toWorldPoint(from);
        WorldPoint toPoint = toWorldPoint(to);

        // Check collision using RuneLite's collision system
        CollisionData[] collisionData = client.getCollisionMaps();
        if (collisionData == null) {
            // No collision data available, assume clear path
            return true;
        }

        int plane = from.getZ();
        if (plane >= collisionData.length || collisionData[plane] == null) {
            return true;
        }

        // Use RegionManager for consistency with Elvarg
        // Calculate scene-relative local coordinates
        // RuneLite uses scene coordinates (0-103) for collision maps
        LocalPoint fromLocal = LocalPoint.fromWorld(client, toWorldPoint(from));
        LocalPoint toLocal = LocalPoint.fromWorld(client, toWorldPoint(to));

        if (fromLocal == null || toLocal == null) {
            return false; // Points not in scene
        }

        int x0 = fromLocal.getSceneX();
        int y0 = fromLocal.getSceneY();
        int x1 = toLocal.getSceneX();
        int y1 = toLocal.getSceneY();

        // Check bounds
        if (x0 < 0 || x0 >= 104 || y0 < 0 || y0 >= 104 ||
            x1 < 0 || x1 >= 104 || y1 < 0 || y1 >= 104) {
            return false;
        }

        // Implement line-of-sight check using Bresenham's algorithm
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (x0 != x1 || y0 != y1) {
            // Check if current tile blocks projectiles
            int flags = collisionData[plane].getFlags()[x0][y0];
            if ((flags & CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL) != 0) {
                return false;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }

        return true;
    }

    /**
     * Check if a tile is blocked/unwalkable.
     * Uses RuneLite's collision flags.
     */
    public boolean isBlocked(Location location) {
        CollisionData[] collisionData = client.getCollisionMaps();
        if (collisionData == null) {
            return false;
        }

        int plane = location.getZ();
        if (plane >= collisionData.length || collisionData[plane] == null) {
            return false;
        }

        // Check collision flags at the location
        // Calculate scene-relative coordinates
        LocalPoint localPoint = LocalPoint.fromWorld(client, toWorldPoint(location));
        if (localPoint == null) {
            return true; // Location not in scene, consider blocked
        }
        
        int localX = localPoint.getSceneX();
        int localY = localPoint.getSceneY();

        if (localX < 0 || localX >= 104 || localY < 0 || localY >= 104) {
            return true; // Out of bounds
        }

        int flags = collisionData[plane].getFlags()[localX][localY];

        // Check if tile is blocked (solid object, wall, etc)
        return (flags & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
    }

    /**
     * Get collision flags at a specific location.
     */
    public int getCollisionFlags(Location location) {
        CollisionData[] collisionData = client.getCollisionMaps();
        if (collisionData == null) {
            return 0;
        }

        int plane = location.getZ();
        if (plane >= collisionData.length || collisionData[plane] == null) {
            return 0;
        }

        // Calculate scene-relative coordinates
        LocalPoint localPoint = LocalPoint.fromWorld(client, toWorldPoint(location));
        if (localPoint == null) {
            return CollisionDataFlag.BLOCK_MOVEMENT_FULL; // Not in scene
        }
        
        int localX = localPoint.getSceneX();
        int localY = localPoint.getSceneY();

        if (localX < 0 || localX >= 104 || localY < 0 || localY >= 104) {
            return CollisionDataFlag.BLOCK_MOVEMENT_FULL; // Out of bounds
        }

        return collisionData[plane].getFlags()[localX][localY];
    }

    /**
     * Check if movement is blocked in a specific direction.
     */
    public boolean isMovementBlocked(Location from, int direction) {
        int flags = getCollisionFlags(from);

        switch (direction) {
            case NORTH:
                return (flags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0;
            case SOUTH:
                return (flags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0;
            case EAST:
                return (flags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0;
            case WEST:
                return (flags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0;
            case NORTHEAST:
                return (flags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST)) != 0;
            case NORTHWEST:
                return (flags & (CollisionDataFlag.BLOCK_MOVEMENT_NORTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST)) != 0;
            case SOUTHEAST:
                return (flags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_EAST)) != 0;
            case SOUTHWEST:
                return (flags & (CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_WEST)) != 0;
            default:
                return false;
        }
    }

    /**
     * Find a path between two locations using A* pathfinding.
     * Returns a list of tiles to walk through, or empty if no path exists.
     */
    public List<Location> findPath(Location start, Location end, int maxDistance) {
        List<Location> path = new ArrayList<>();

        // For now, delegate to PathFinder's route calculation
        // In a full implementation, this would use A* with RuneLite's collision data
        log.debug("[PATH] Finding path from {} to {} (max distance: {})", start, end, maxDistance);

        // Simple straight-line path for now
        int dx = Integer.signum(end.getX() - start.getX());
        int dy = Integer.signum(end.getY() - start.getY());

        Location current = start.clone();
        int steps = 0;

        while (!current.equals(end) && steps < maxDistance) {
            if (current.getX() != end.getX()) {
                current = current.transform(dx, 0);
            }
            if (current.getY() != end.getY()) {
                current = current.transform(0, dy);
            }

            if (!isBlocked(current)) {
                path.add(current.clone());
                steps++;
            } else {
                // Path blocked, try to find alternative
                break;
            }
        }

        return path;
    }

    /**
     * Check if a location is within melee distance (1 tile).
     */
    public boolean isWithinMeleeDistance(Location attacker, Location target) {
        int dx = Math.abs(attacker.getX() - target.getX());
        int dy = Math.abs(attacker.getY() - target.getY());
        return dx <= 1 && dy <= 1;
    }

    /**
     * Get the distance between two locations (Chebyshev distance).
     */
    public int getDistance(Location from, Location to) {
        return from.getDistance(to);
    }

    /**
     * Convert Elvarg Location to RuneLite WorldPoint.
     */
    private WorldPoint toWorldPoint(Location location) {
        return new WorldPoint(location.getX(), location.getY(), location.getZ());
    }

    /**
     * Convert RuneLite WorldPoint to Elvarg Location.
     */
    private Location toLocation(WorldPoint point) {
        return new Location(point.getX(), point.getY(), point.getPlane());
    }

    /**
     * Check if we can walk to a specific tile.
     */
    public boolean canWalkTo(Location from, Location to) {
        // Check if destination is blocked
        if (isBlocked(to)) {
            return false;
        }

        // Check if there's a clear path
        return !findPath(from, to, 25).isEmpty();
    }

    /**
     * Get reachable tiles within a certain distance.
     */
    public Set<Location> getReachableTiles(Location center, int distance) {
        Set<Location> reachable = new HashSet<>();

        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                Location tile = center.transform(dx, dy);
                if (!isBlocked(tile) && canWalkTo(center, tile)) {
                    reachable.add(tile);
                }
            }
        }

        return reachable;
    }

    /**
     * Check if location is in multi-combat zone.
     */
    public boolean isMultiCombat(Location location) {
        // In RuneLite, check varbit for multi-combat
        // For now, return false as default (single combat)
        // This would need to be implemented with actual multi-combat area checks
        return false;
    }

    /**
     * Get the region ID for a location.
     */
    public int getRegionId(Location location) {
        return (location.getRegionX() << 8) | location.getRegionY();
    }

    /**
     * Check if two locations are in the same region.
     */
    public boolean isSameRegion(Location loc1, Location loc2) {
        return loc1.getRegionX() == loc2.getRegionX() &&
               loc1.getRegionY() == loc2.getRegionY();
    }
}