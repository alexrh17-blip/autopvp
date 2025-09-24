package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.model.Location;
import com.elvarg.game.model.movement.MovementQueue;
import com.elvarg.game.entity.impl.Mobile;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that creates a MovementQueue instance for tracking player movement.
 * Since MovementQueue is final, we create an instance rather than extending.
 */
@Slf4j
public class MovementQueueAdapter {

    private final Client client;
    private final Mobile owner;
    private final MovementQueue movementQueue;

    public MovementQueueAdapter(Client client, Mobile owner) {
        this.client = client;
        this.owner = owner;
        this.movementQueue = new MovementQueue(owner);
    }

    /**
     * Get the underlying MovementQueue instance.
     */
    public MovementQueue getMovementQueue() {
        return movementQueue;
    }

    /**
     * Update movement state from RuneLite client.
     */
    public void updateMovementState() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        // Check if player is moving
        boolean isMoving = isPlayerMoving();

        // Update location if changed
        WorldPoint currentLocation = localPlayer.getWorldLocation();
        Location elvargLocation = new Location(currentLocation.getX(), currentLocation.getY(), currentLocation.getPlane());

        if (!owner.getLocation().equals(elvargLocation)) {
            owner.setLocation(elvargLocation);
            log.debug("[MOVEMENT] Player moved to: {}", elvargLocation);
        }
    }

    /**
     * Check if the player is currently moving.
     */
    public boolean isPlayerMoving() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }

        // Check idle vs current pose animation
        int idlePose = localPlayer.getIdlePoseAnimation();
        int currentPose = localPlayer.getPoseAnimation();

        // Player is moving if pose animation differs from idle
        return currentPose != idlePose && currentPose != -1;
    }

    /**
     * Check if player can move (not frozen/stunned).
     */
    public boolean canMove() {
        // Check if frozen (via timer adapter)
        if (owner.getTimers() != null && owner.getTimers().has(com.elvarg.util.timers.TimerKey.FREEZE)) {
            return false;
        }

        // Check if stunned
        if (owner.getTimers() != null && owner.getTimers().has(com.elvarg.util.timers.TimerKey.STUN)) {
            return false;
        }

        // Check if player is performing an action that prevents movement
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            int animation = localPlayer.getAnimation();
            // Check for animations that prevent movement (eating, spec, etc.)
            if (animation == 829 || // Eating
                animation == 7644 || // AGS spec
                animation == 7645 || // BGS spec
                animation == 7640 || // SGS spec
                animation == 7642) { // ZGS spec
                return false;
            }
        }

        return true;
    }

    /**
     * Get the destination the player is moving to.
     */
    public Location getDestination() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return owner.getLocation();
        }

        // If player is moving to interact with something
        if (localPlayer.getInteracting() != null) {
            WorldPoint targetWorld = localPlayer.getInteracting().getWorldLocation();
            return new Location(targetWorld.getX(), targetWorld.getY(), targetWorld.getPlane());
        }

        // Return current location if not moving
        return owner.getLocation();
    }

    /**
     * Reset the movement queue (not supported in RuneLite).
     */
    public void reset() {
        log.debug("[MOVEMENT] Cannot reset movement queue in RuneLite (read-only)");
    }
}