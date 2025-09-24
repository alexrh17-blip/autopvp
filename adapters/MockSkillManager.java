package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Skill;
import com.elvarg.game.content.skill.SkillManager;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mock SkillManager that doesn't use Netty for opponent tracking.
 * Provides read-only skill levels without network dependencies.
 *
 * This wrapper avoids extending SkillManager to prevent Netty dependency issues.
 */
public class MockSkillManager extends SkillManager {

    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> maxLevels = new EnumMap<>(Skill.class);

    public MockSkillManager(Player player) {
        super(player);

        // Initialize all skills to level 1
        for (Skill skill : Skill.values()) {
            levels.put(skill, 1);
            maxLevels.put(skill, 1);
        }
    }

    // setLevel returns void in parent class
    @Override
    public void setLevel(Skill skill, int level) {
        levels.put(skill, level);
        maxLevels.put(skill, level);
    }

    // setLevel with refresh also returns void
    public void setLevel(Skill skill, int level, boolean refresh) {
        setLevel(skill, level);
    }

    @Override
    public int getCurrentLevel(Skill skill) {
        return levels.getOrDefault(skill, 1);
    }

    @Override
    public int getMaxLevel(Skill skill) {
        return maxLevels.getOrDefault(skill, 1);
    }

    // Note: setMaxLevel returns SkillManager in parent for fluent interface
    public SkillManager setMaxLevel(Skill skill, int level) {
        maxLevels.put(skill, level);
        return this;
    }

    public SkillManager setMaxLevel(Skill skill, int level, boolean refresh) {
        return setMaxLevel(skill, level);
    }

    public SkillManager setExperience(Skill skill, int experience) {
        // No-op for mock
        return this;
    }

    public SkillManager setExperience(Skill skill, int experience, boolean refresh) {
        // No-op for mock
        return this;
    }

    @Override
    public int getExperience(Skill skill) {
        // Return approximate experience for the level
        int level = getCurrentLevel(skill);
        return experienceForLevel(level);
    }

    private int experienceForLevel(int level) {
        if (level <= 1) return 0;
        if (level >= 99) return 13034431;

        // Approximate XP calculation
        double total = 0;
        for (int i = 1; i < level; i++) {
            total += Math.floor(i + 300 * Math.pow(2, i / 7.0));
        }
        return (int) Math.floor(total / 4);
    }

    @Override
    public int getCombatLevel() {
        // Calculate combat level based on set levels
        int attack = getCurrentLevel(Skill.ATTACK);
        int defence = getCurrentLevel(Skill.DEFENCE);
        int strength = getCurrentLevel(Skill.STRENGTH);
        int hitpoints = getCurrentLevel(Skill.HITPOINTS);
        int ranged = getCurrentLevel(Skill.RANGED);
        int prayer = getCurrentLevel(Skill.PRAYER);
        int magic = getCurrentLevel(Skill.MAGIC);

        double base = 0.25 * (defence + hitpoints + Math.floor(prayer / 2));
        double melee = 0.325 * (attack + strength);
        double range = 0.325 * (Math.floor(ranged / 2) + ranged);
        double mage = 0.325 * (Math.floor(magic / 2) + magic);

        return (int) (base + Math.max(melee, Math.max(range, mage)));
    }

    @Override
    public int getTotalLevel() {
        int total = 0;
        for (Skill skill : Skill.values()) {
            if (skill != Skill.CONSTRUCTION) {  // Skip construction for now
                total += getCurrentLevel(skill);
            }
        }
        return total;
    }

    @Override
    public long getTotalExp() {
        long total = 0;
        for (Skill skill : Skill.values()) {
            total += getExperience(skill);
        }
        return total;
    }

    public void stopSkilling() {
        // No-op for mock
    }

    /**
     * Update current hitpoints to track real HP from health bar.
     * Used by OpponentElvargPlayer to provide real HP data to NhEnvironment.
     */
    public void updateCurrentHitpoints(int current) {
        levels.put(Skill.HITPOINTS, current);
    }

    @Override
    public String toString() {
        return "MockSkillManager{combatLevel=" + getCombatLevel() + "}";
    }
}