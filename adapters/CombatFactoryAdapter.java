package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.CombatFactory;
import com.elvarg.game.content.combat.CombatType;
import com.elvarg.game.content.combat.method.CombatMethod;
import com.elvarg.game.content.combat.method.impl.MagicCombatMethod;
import com.elvarg.game.content.combat.method.impl.MeleeCombatMethod;
import com.elvarg.game.content.combat.method.impl.RangedCombatMethod;
import com.elvarg.game.entity.impl.Mobile;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that bridges RuneLite to Elvarg's CombatFactory static utility methods.
 * Delegates all combat logic directly to the original CombatFactory implementation.
 * Uses the same static method pattern as the original CombatFactory.
 */
@Slf4j
public class CombatFactoryAdapter {

    // Static combat method instances matching CombatFactory
    public static final CombatMethod MELEE_COMBAT = new MeleeCombatMethod();
    public static final CombatMethod RANGED_COMBAT = new RangedCombatMethod();
    public static final CombatMethod MAGIC_COMBAT = new MagicCombatMethod();

    /**
     * Gets the combat method for the attacker based on their equipment and state.
     * Delegates directly to CombatFactory.getMethod() for exact behavior.
     */
    public static CombatMethod getMethod(Mobile attacker) {
        // Direct delegation to Elvarg's CombatFactory
        // Source: C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\combat\CombatFactory.java
        return CombatFactory.getMethod(attacker);
    }

    /**
     * Checks if the attacker can reach the target based on combat method and distance.
     * Delegates directly to CombatFactory.canReach() for exact behavior.
     */
    public static boolean canReach(Mobile attacker, CombatMethod method, Mobile target) {
        // Direct delegation to Elvarg's CombatFactory
        // Source: C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\combat\CombatFactory.java
        return CombatFactory.canReach(attacker, method, target);
    }

    /**
     * Validates if the target is valid for combat.
     * Delegates directly to CombatFactory.validTarget() for exact behavior.
     */
    public static boolean validTarget(Mobile attacker, Mobile target) {
        // Direct delegation to Elvarg's CombatFactory
        // Source: C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\combat\CombatFactory.java
        return CombatFactory.validTarget(attacker, target);
    }

    /**
     * Gets the combat type based on the attacker's current combat method.
     * Helper method for determining attack style.
     */
    public static CombatType getCombatType(Mobile attacker) {
        CombatMethod method = getMethod(attacker);
        return method.type();
    }

    /**
     * Checks if the attacker is within attack distance of the target.
     * Helper method that combines distance and method checks.
     */
    public static boolean inAttackDistance(Mobile attacker, Mobile target) {
        CombatMethod method = getMethod(attacker);
        int requiredDistance = method.attackDistance(attacker);
        int distance = attacker.calculateDistance(target);
        return distance <= requiredDistance;
    }
}