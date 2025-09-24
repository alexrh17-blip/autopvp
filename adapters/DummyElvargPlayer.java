package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.content.combat.hit.PendingHit;
import com.elvarg.game.model.Location;

/**
 * Dummy Elvarg Player implementation to satisfy constructor requirements.
 * This is only used to satisfy Elvarg's constructor requirements for adapters.
 */
public class DummyElvargPlayer extends Player {

    private boolean swapPid = false;
    private int lastDamage = 0;

    public DummyElvargPlayer() {
        super(null);
    }

    @Override
    public void onAdd() {
        // No-op
    }

    @Override
    public void onRemove() {
        // No-op
    }

    @Override
    public PendingHit manipulateHit(PendingHit hit) {
        return hit;
    }

    @Override
    public void appendDeath() {
        // No-op
    }

    @Override
    public void heal(int damage) {
        // No-op
    }

    @Override
    public int getHitpoints() {
        // Return 0 HP to represent 'no opponent' state correctly
        // Prevents AI from thinking there's an opponent with 0% health
        return 0;
    }

    @Override
    public Player setHitpoints(int hitpoints) {
        return this;
    }

    /**
     * Sets the PID swap flag for this player.
     * PID swapping affects attack priority in PvP combat.
     * When true, this player gets priority in mutual combat situations.
     */
    public void setSwapPid(boolean swapPid) {
        this.swapPid = swapPid;
    }

    /**
     * Gets the PID swap flag for this player.
     * @return true if PID is swapped (player has priority), false otherwise
     */
    public boolean isSwapPid() {
        return swapPid;
    }

    public Mobile setPositionToFace(Location position) {
        // Store the position this player is facing for combat calculations
        // In a dummy player, we don't need to track this, but return properly
        return this;
    }

    public int getLastDamage() {
        return lastDamage;
    }

    public void setLastDamage(int damage) {
        this.lastDamage = damage;
    }

    public double getPid() {
        // Citation: NhEnvironment.java:1760 calls getTarget().getPid()
        // Citation: Mobile.java has getIndex() method that serves as PID
        return getIndex();
    }

    // Note: getCachedUpdateBlock and setCachedUpdateBlock are inherited from Player
    // They use ByteBuf (Netty) which may not be available at runtime.
    // The Player class handles these, we don't need to override them.
    // If they get called, it will return null from the parent class.

    /**
     * Get TimerRepository for dummy player.
     * Returns null since dummy player has no active timers.
     */
    public com.elvarg.util.timers.TimerRepository getTimers() {
        return null; // Dummy player has no timers
    }

    /**
     * Get SkillManager for dummy player.
     * Returns null since dummy player has no skills.
     */
    public com.elvarg.game.content.skill.SkillManager getSkillManager() {
        return null; // Dummy player has no skill manager
    }

    /**
     * Get prayer active array for dummy player.
     * Returns empty array since dummy player has no prayers active.
     */
    public boolean[] getPrayerActive() {
        return new boolean[29]; // Empty prayer array (29 prayers in OSRS)
    }
}