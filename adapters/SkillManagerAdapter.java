package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.model.Skill;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.entity.impl.player.Player;
import net.runelite.api.Client;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that wraps a SkillManager instance and bridges RuneLite's skill system.
 * Since SkillManager requires a Player, we wrap it rather than extend.
 */
@Slf4j
public class SkillManagerAdapter {

    private final Client client;
    private final SkillManager skillManager;

    public SkillManagerAdapter(Client client, Player elvargPlayer) {
        this.client = client;
        this.skillManager = new SkillManager(elvargPlayer);
    }

    /**
     * Get the underlying SkillManager instance.
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * Maps Elvarg Skill enum to RuneLite Skill enum.
     */
    private net.runelite.api.Skill toRuneLiteSkill(Skill elvargSkill) {
        switch (elvargSkill) {
            case ATTACK: return net.runelite.api.Skill.ATTACK;
            case DEFENCE: return net.runelite.api.Skill.DEFENCE;
            case STRENGTH: return net.runelite.api.Skill.STRENGTH;
            case HITPOINTS: return net.runelite.api.Skill.HITPOINTS;
            case RANGED: return net.runelite.api.Skill.RANGED;
            case PRAYER: return net.runelite.api.Skill.PRAYER;
            case MAGIC: return net.runelite.api.Skill.MAGIC;
            case COOKING: return net.runelite.api.Skill.COOKING;
            case WOODCUTTING: return net.runelite.api.Skill.WOODCUTTING;
            case FLETCHING: return net.runelite.api.Skill.FLETCHING;
            case FISHING: return net.runelite.api.Skill.FISHING;
            case FIREMAKING: return net.runelite.api.Skill.FIREMAKING;
            case CRAFTING: return net.runelite.api.Skill.CRAFTING;
            case SMITHING: return net.runelite.api.Skill.SMITHING;
            case MINING: return net.runelite.api.Skill.MINING;
            case HERBLORE: return net.runelite.api.Skill.HERBLORE;
            case AGILITY: return net.runelite.api.Skill.AGILITY;
            case THIEVING: return net.runelite.api.Skill.THIEVING;
            case SLAYER: return net.runelite.api.Skill.SLAYER;
            case FARMING: return net.runelite.api.Skill.FARMING;
            case RUNECRAFTING: return net.runelite.api.Skill.RUNECRAFT;
            case HUNTER: return net.runelite.api.Skill.HUNTER;
            case CONSTRUCTION: return net.runelite.api.Skill.CONSTRUCTION;
            default:
                log.warn("[ADAPTER] Unknown Elvarg skill: {}", elvargSkill);
                return net.runelite.api.Skill.ATTACK; // Default fallback
        }
    }

    /**
     * Get current level for a skill from RuneLite client.
     */
    public int getCurrentLevel(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        return client.getBoostedSkillLevel(rlSkill);
    }

    /**
     * Get maximum level for a skill from RuneLite client.
     */
    public int getMaxLevel(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        int level = client.getRealSkillLevel(rlSkill);
        // Ensure we never return 0 to prevent division by zero in NhEnvironment calculations
        return Math.max(1, level);
    }

    /**
     * Get experience for a skill from RuneLite client.
     */
    public int getExperience(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        return client.getSkillExperience(rlSkill);
    }

    /**
     * Get total level from RuneLite client.
     */
    public int getTotalLevel() {
        return client.getTotalLevel();
    }

    /**
     * Get total experience from RuneLite client.
     */
    public long getTotalExp() {
        return client.getOverallExperience();
    }

    /**
     * Get combat level from RuneLite client.
     */
    public int getCombatLevel() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            return localPlayer.getCombatLevel();
        }
        return 3; // Default combat level
    }

    /**
     * Check if a skill requirement is met.
     */
    public boolean hasRequirement(Skill skill, int level) {
        return getCurrentLevel(skill) >= level;
    }

    /**
     * Methods that modify skills - not supported in RuneLite context.
     */
    public void addExperience(Skill skill, int exp) {
        log.debug("[ADAPTER] Cannot add experience in RuneLite (read-only): {} exp to {}", exp, skill);
    }

    public void setLevel(Skill skill, int level) {
        log.debug("[ADAPTER] Cannot set skill level in RuneLite (read-only): {} to level {}", skill, level);
    }
}