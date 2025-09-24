package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Skill;
import net.runelite.api.Client;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that extends SkillManager to bridge RuneLite's skill system.
 * Uses a dummy Player instance to satisfy the constructor requirement.
 */
@Slf4j
public class SkillManagerWrapperAdapter extends SkillManager {

    private final Client client;

    /**
     * Dummy Player implementation to satisfy SkillManager constructor.
     */
    private static class DummyPlayer extends Player {
        public DummyPlayer() {
            super(null);
        }

        @Override
        public void appendDeath() {
        }

        @Override
        public void heal(int amount) {
        }

        @Override
        public int getHitpoints() {
            return 99;
        }

        @Override
        public Player setHitpoints(int hitpoints) {
            return this;
        }
    }

    public SkillManagerWrapperAdapter(Client client) {
        super(new DummyPlayer());
        this.client = client;
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

    @Override
    public int getCurrentLevel(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        return client.getBoostedSkillLevel(rlSkill);
    }

    @Override
    public int getMaxLevel(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        return client.getRealSkillLevel(rlSkill);
    }

    @Override
    public int getExperience(Skill skill) {
        net.runelite.api.Skill rlSkill = toRuneLiteSkill(skill);
        return client.getSkillExperience(rlSkill);
    }

    @Override
    public int getTotalLevel() {
        return client.getTotalLevel();
    }

    @Override
    public long getTotalExp() {
        return client.getOverallExperience();
    }

    @Override
    public int getCombatLevel() {
        net.runelite.api.Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            return localPlayer.getCombatLevel();
        }
        return 3; // Default combat level
    }

    @Override
    public SkillManager addExperience(Skill skill, int exp) {
        log.debug("[ADAPTER] Cannot add experience in RuneLite (read-only): {} exp to {}", exp, skill);
        return this;
    }

    @Override
    public void setLevel(Skill skill, int level) {
        log.debug("[ADAPTER] Cannot set skill level in RuneLite (read-only): {} to level {}", skill, level);
    }

    @Override
    public SkillManager setCurrentLevel(Skill skill, int level) {
        log.debug("[ADAPTER] Cannot set current level in RuneLite (read-only): {} to level {}", skill, level);
        return this;
    }

    @Override
    public SkillManager setCurrentLevel(Skill skill, int level, boolean refresh) {
        log.debug("[ADAPTER] Cannot set current level in RuneLite (read-only): {} to level {}", skill, level);
        return this;
    }

    @Override
    public SkillManager setMaxLevel(Skill skill, int level) {
        log.debug("[ADAPTER] Cannot set max level in RuneLite (read-only): {} to level {}", skill, level);
        return this;
    }

    @Override
    public SkillManager setMaxLevel(Skill skill, int level, boolean refresh) {
        log.debug("[ADAPTER] Cannot set max level in RuneLite (read-only): {} to level {}", skill, level);
        return this;
    }

    @Override
    public SkillManager setExperience(Skill skill, int exp) {
        log.debug("[ADAPTER] Cannot set experience in RuneLite (read-only): {} to {} exp", skill, exp);
        return this;
    }

    @Override
    public SkillManager setExperience(Skill skill, int exp, boolean refresh) {
        log.debug("[ADAPTER] Cannot set experience in RuneLite (read-only): {} to {} exp", skill, exp);
        return this;
    }
}