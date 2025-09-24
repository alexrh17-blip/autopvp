package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.Combat;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.entity.impl.Mobile;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.container.impl.Equipment;
import com.elvarg.game.model.container.impl.Inventory;
import com.elvarg.game.model.movement.MovementQueue;
import com.elvarg.util.timers.TimerRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegating Player implementation that wraps a PlayerAdapter.
 * This allows NhEnvironment to interact with our adapter through the Elvarg Player interface.
 */
@Slf4j
public class DelegatingElvargPlayer extends DummyElvargPlayer {

    private final PlayerAdapter playerAdapter;

    /**
     * Create a delegating player that wraps a PlayerAdapter.
     * @param playerAdapter The adapter to delegate to
     */
    public DelegatingElvargPlayer(PlayerAdapter playerAdapter) {
        super();
        this.playerAdapter = playerAdapter;
        log.debug("[DELEGATE] Created DelegatingElvargPlayer wrapping PlayerAdapter");
    }

    @Override
    public Equipment getEquipment() {
        return playerAdapter.getEquipment();
    }

    @Override
    public Inventory getInventory() {
        return playerAdapter.getInventory();
    }

    @Override
    public SkillManager getSkillManager() {
        return playerAdapter.getSkillManager();
    }

    @Override
    public Combat getCombat() {
        return playerAdapter.getCombat();
    }

    @Override
    public TimerRepository getTimers() {
        return playerAdapter.getTimers();
    }

    @Override
    public MovementQueue getMovementQueue() {
        return playerAdapter.getMovementQueue();
    }

    @Override
    public Location getLocation() {
        return playerAdapter.getLocation();
    }

    @Override
    public boolean[] getPrayerActive() {
        return playerAdapter.getPrayerActive();
    }

    @Override
    public boolean isSpecialActivated() {
        return playerAdapter.isSpecialActivated();
    }

    @Override
    public void setSpecialActivated(boolean specialActivated) {
        playerAdapter.setSpecialActivated(specialActivated);
    }

    @Override
    public int getSpecialPercentage() {
        return playerAdapter.getSpecialPercentage();
    }

    @Override
    public void setSpecialPercentage(int specialPercentage) {
        playerAdapter.setSpecialPercentage(specialPercentage);
    }

    @Override
    public boolean hasVengeance() {
        return playerAdapter.hasVengeance();
    }

    @Override
    public void setHasVengeance(boolean hasVengeance) {
        playerAdapter.setHasVengeance(hasVengeance);
    }

    @Override
    public Mobile getInteractingMobile() {
        return playerAdapter.getInteractingMobile();
    }

    // Note: setInteractingMobile is not in Player interface, only in PlayerAdapter
    public void setInteractingMobile(Mobile interactingMobile) {
        playerAdapter.setInteractingMobile(interactingMobile);
    }

    @Override
    public Mobile getCombatFollowing() {
        return playerAdapter.getCombatFollowing();
    }

    @Override
    public void setCombatFollowing(Mobile combatFollowing) {
        playerAdapter.setCombatFollowing(combatFollowing);
    }

    @Override
    public int getHitpoints() {
        return playerAdapter.getHitpoints();
    }

    @Override
    public Player setHitpoints(int hitpoints) {
        playerAdapter.setHitpoints(hitpoints);
        return this;
    }

    @Override
    public int getLastDamage() {
        return playerAdapter.getLastDamage();
    }

    @Override
    public void setLastDamage(int damage) {
        playerAdapter.setLastDamage(damage);
    }

    @Override
    public Mobile setPositionToFace(Location position) {
        playerAdapter.setPositionToFace(position);
        return this;
    }

    /**
     * Get the underlying PlayerAdapter.
     * @return The wrapped PlayerAdapter
     */
    public PlayerAdapter getPlayerAdapter() {
        return playerAdapter;
    }
}