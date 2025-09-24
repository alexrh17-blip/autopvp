package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.CombatType;
import java.util.ArrayDeque;
import java.util.Deque;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks combat history metrics (attacks, prayers, damage ratios) to approximate
 * naton1's NhEnvironment counters using RuneLite observable events.
 */
@Slf4j
public class CombatHistoryTracker
{
    private static final int RECENT_WINDOW = 5;
    private static final int TARGET_MAX_HP_ESTIMATE = 99;

    private final Client client;
    private final EventBus eventBus;
    private final TimerManagerAdapter timerManagerAdapter;

    private double totalDamageDealt;
    private double totalDamageReceived;
    private double damageDealtScaleTick;
    private double damageReceivedScaleTick;

    private int totalTargetHitCount;
    private int targetHitMeleeCount;
    private int targetHitRangeCount;
    private int targetHitMagicCount;
    private int targetHitCorrectCount;

    private int totalTargetPrayCount;
    private int targetPrayMeleeCount;
    private int targetPrayRangeCount;
    private int targetPrayMagicCount;
    private int targetPrayCorrectCount;

    private int playerHitMeleeCount;
    private int playerHitRangeCount;
    private int playerHitMagicCount;

    private int playerPrayMeleeCount;
    private int playerPrayRangeCount;
    private int playerPrayMagicCount;

    private final Deque<CombatType> recentTargetAttackStyles = new ArrayDeque<>();
    private final Deque<CombatType> recentPlayerAttackStyles = new ArrayDeque<>();
    private final Deque<CombatType> recentTargetPrayerStyles = new ArrayDeque<>();
    private final Deque<CombatType> recentPlayerPrayerStyles = new ArrayDeque<>();
    private final Deque<Boolean> recentTargetHitCorrect = new ArrayDeque<>();
    private final Deque<Boolean> recentTargetPrayerCorrect = new ArrayDeque<>();

    public CombatHistoryTracker(Client client, EventBus eventBus, TimerManagerAdapter timerManagerAdapter)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.timerManagerAdapter = timerManagerAdapter;
        eventBus.register(this);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        Player local = client.getLocalPlayer();
        if (local == null)
        {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();
        if (hitsplat == null)
        {
            return;
        }

        Actor actor = event.getActor();
        if (actor == null)
        {
            return;
        }

        Actor currentTarget = local.getInteracting();
        if (actor == local)
        {
            handleDamageToPlayer(local, currentTarget instanceof Player ? (Player) currentTarget : null, hitsplat.getAmount());
        }
        else if (currentTarget != null && actor == currentTarget && actor instanceof Player)
        {
            handleDamageToTarget((Player) actor, hitsplat.getAmount());
        }
    }

    private void handleDamageToPlayer(Player localPlayer, Player targetPlayer, int damage)
    {
        totalTargetHitCount++;

        CombatType attackStyle = timerManagerAdapter.getTargetLastAttackType(targetPlayer);
        if (attackStyle == null)
        {
            attackStyle = CombatType.MELEE; // Fallback when style is unknown
        }

        pushStyle(recentTargetAttackStyles, attackStyle);
        switch (attackStyle)
        {
            case MAGIC:
                targetHitMagicCount++;
                break;
            case RANGED:
                targetHitRangeCount++;
                break;
            default:
                targetHitMeleeCount++;
        }

        // Track our overhead prayer usage (mirrors NhEnvironment onHitCalculated logic)
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
        {
            playerPrayMagicCount++;
            pushStyle(recentPlayerPrayerStyles, CombatType.MAGIC);
        }
        else if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
        {
            playerPrayRangeCount++;
            pushStyle(recentPlayerPrayerStyles, CombatType.RANGED);
        }
        else if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
        {
            playerPrayMeleeCount++;
            pushStyle(recentPlayerPrayerStyles, CombatType.MELEE);
        }

        boolean prayedCorrectly = isPrayerActiveForStyle(attackStyle);
        if (!prayedCorrectly)
        {
            targetHitCorrectCount++;
        }
        pushBoolean(recentTargetHitCorrect, !prayedCorrectly);

        // Normalised damage received for ratio calculations
        double normalisedDamage = normaliseDamageToPlayer(damage);
        damageReceivedScaleTick += normalisedDamage;
        totalDamageReceived += normalisedDamage;
    }

    private void handleDamageToTarget(Player targetPlayer, int damage)
    {
        CombatType attackStyle = timerManagerAdapter.getPlayerCombatType();
        if (attackStyle == null)
        {
            attackStyle = CombatType.MELEE;
        }

        pushStyle(recentPlayerAttackStyles, attackStyle);
        switch (attackStyle)
        {
            case MAGIC:
                playerHitMagicCount++;
                break;
            case RANGED:
                playerHitRangeCount++;
                break;
            default:
                playerHitMeleeCount++;
        }

        double normalisedDamage = normaliseDamageToTarget(damage);
        damageDealtScaleTick += normalisedDamage;
        totalDamageDealt += normalisedDamage;

        totalTargetPrayCount++;

        CombatType prayedStyle = mapHeadIconToCombatStyle(targetPlayer.getOverheadIcon());
        if (prayedStyle != null)
        {
            switch (prayedStyle)
            {
                case MAGIC:
                    targetPrayMagicCount++;
                    break;
                case RANGED:
                    targetPrayRangeCount++;
                    break;
                default:
                    targetPrayMeleeCount++;
            }
            pushStyle(recentTargetPrayerStyles, prayedStyle);
        }

        boolean prayedCorrectly = prayedStyle != null && prayedStyle == attackStyle;
        if (prayedCorrectly)
        {
            targetPrayCorrectCount++;
        }
        pushBoolean(recentTargetPrayerCorrect, prayedCorrectly);
    }

    public void onTickEnd()
    {
        damageDealtScaleTick = 0;
        damageReceivedScaleTick = 0;
    }

    public void shutdown()
    {
        eventBus.unregister(this);
    }

    public boolean didPlayerJustAttack()
    {
        return timerManagerAdapter.getPlayerLastAttackTick() == client.getTickCount();
    }

    public double getHitsplatsOnAgentScale()
    {
        return damageReceivedScaleTick;
    }

    public double getHitsplatsOnTargetScale()
    {
        return damageDealtScaleTick;
    }

    public double getDamageDealtScale()
    {
        double ratio = (totalDamageDealt + 1.0) / (totalDamageReceived + 1.0);
        return Math.max(0.5, Math.min(ratio, 2.0));
    }

    public double getTargetHitConfidence()
    {
        return Math.min(totalTargetHitCount / 20.0, 1.0);
    }

    public double getTargetHitMeleeRatio()
    {
        return ratio(targetHitMeleeCount, totalTargetHitCount);
    }

    public double getTargetHitMageRatio()
    {
        return ratio(targetHitMagicCount, totalTargetHitCount);
    }

    public double getTargetHitRangeRatio()
    {
        return ratio(targetHitRangeCount, totalTargetHitCount);
    }

    public double getPlayerHitMeleeRatio()
    {
        return ratio(playerHitMeleeCount, totalTargetPrayCount);
    }

    public double getPlayerHitMageRatio()
    {
        return ratio(playerHitMagicCount, totalTargetPrayCount);
    }

    public double getPlayerHitRangeRatio()
    {
        return ratio(playerHitRangeCount, totalTargetPrayCount);
    }

    public double getTargetHitCorrectRatio()
    {
        return ratio(targetHitCorrectCount, totalTargetHitCount);
    }

    public double getTargetPrayConfidence()
    {
        return Math.min(totalTargetPrayCount / 20.0, 1.0);
    }

    public double getTargetPrayMageRatio()
    {
        return ratio(targetPrayMagicCount, totalTargetPrayCount);
    }

    public double getTargetPrayRangeRatio()
    {
        return ratio(targetPrayRangeCount, totalTargetPrayCount);
    }

    public double getTargetPrayMeleeRatio()
    {
        return ratio(targetPrayMeleeCount, totalTargetPrayCount);
    }

    public double getPlayerPrayMageRatio()
    {
        return ratio(playerPrayMagicCount, totalTargetHitCount);
    }

    public double getPlayerPrayRangeRatio()
    {
        return ratio(playerPrayRangeCount, totalTargetHitCount);
    }

    public double getPlayerPrayMeleeRatio()
    {
        return ratio(playerPrayMeleeCount, totalTargetHitCount);
    }

    public double getTargetPrayCorrectRatio()
    {
        return ratio(targetPrayCorrectCount, totalTargetPrayCount);
    }

    public double getRecentTargetHitMeleeRatio()
    {
        return recentStyleRatio(recentTargetAttackStyles, CombatType.MELEE);
    }

    public double getRecentTargetHitMageRatio()
    {
        return recentStyleRatio(recentTargetAttackStyles, CombatType.MAGIC);
    }

    public double getRecentTargetHitRangeRatio()
    {
        return recentStyleRatio(recentTargetAttackStyles, CombatType.RANGED);
    }

    public double getRecentPlayerHitMeleeRatio()
    {
        return recentStyleRatio(recentPlayerAttackStyles, CombatType.MELEE);
    }

    public double getRecentPlayerHitMageRatio()
    {
        return recentStyleRatio(recentPlayerAttackStyles, CombatType.MAGIC);
    }

    public double getRecentPlayerHitRangeRatio()
    {
        return recentStyleRatio(recentPlayerAttackStyles, CombatType.RANGED);
    }

    public double getRecentTargetHitCorrectRatio()
    {
        return recentBooleanRatio(recentTargetHitCorrect);
    }

    public double getRecentTargetPrayMageRatio()
    {
        return recentStyleRatio(recentTargetPrayerStyles, CombatType.MAGIC);
    }

    public double getRecentTargetPrayRangeRatio()
    {
        return recentStyleRatio(recentTargetPrayerStyles, CombatType.RANGED);
    }

    public double getRecentTargetPrayMeleeRatio()
    {
        return recentStyleRatio(recentTargetPrayerStyles, CombatType.MELEE);
    }

    public double getRecentPlayerPrayMageRatio()
    {
        return recentStyleRatio(recentPlayerPrayerStyles, CombatType.MAGIC);
    }

    public double getRecentPlayerPrayRangeRatio()
    {
        return recentStyleRatio(recentPlayerPrayerStyles, CombatType.RANGED);
    }

    public double getRecentPlayerPrayMeleeRatio()
    {
        return recentStyleRatio(recentPlayerPrayerStyles, CombatType.MELEE);
    }

    public double getRecentTargetPrayCorrectRatio()
    {
        return recentBooleanRatio(recentTargetPrayerCorrect);
    }

    private double ratio(int numerator, int denominator)
    {
        if (denominator <= 0)
        {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private double recentStyleRatio(Deque<CombatType> queue, CombatType style)
    {
        if (queue.isEmpty())
        {
            return 0.0;
        }
        long count = queue.stream().filter(style::equals).count();
        return count / (double) RECENT_WINDOW;
    }

    private double recentBooleanRatio(Deque<Boolean> queue)
    {
        if (queue.isEmpty())
        {
            return 0.0;
        }
        long count = queue.stream().filter(Boolean::booleanValue).count();
        return count / (double) RECENT_WINDOW;
    }

    private void pushStyle(Deque<CombatType> queue, CombatType style)
    {
        if (style == null)
        {
            return;
        }
        if (queue.size() == RECENT_WINDOW)
        {
            queue.removeFirst();
        }
        queue.addLast(style);
    }

    private void pushBoolean(Deque<Boolean> queue, boolean value)
    {
        if (queue.size() == RECENT_WINDOW)
        {
            queue.removeFirst();
        }
        queue.addLast(value);
    }

    private double normaliseDamageToPlayer(int damage)
    {
        if (damage <= 0)
        {
            return 0.0;
        }
        int maxHp = Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS));
        return damage / (double) maxHp;
    }

    private double normaliseDamageToTarget(int damage)
    {
        if (damage <= 0)
        {
            return 0.0;
        }
        return damage / (double) TARGET_MAX_HP_ESTIMATE;
    }

    private boolean isPrayerActiveForStyle(CombatType style)
    {
        switch (style)
        {
            case MAGIC:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);
            case RANGED:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES);
            case MELEE:
            default:
                return client.isPrayerActive(Prayer.PROTECT_FROM_MELEE);
        }
    }

    private CombatType mapHeadIconToCombatStyle(HeadIcon icon)
    {
        if (icon == null)
        {
            return null;
        }
        switch (icon)
        {
            case MAGIC:
                return CombatType.MAGIC;
            case RANGED:
                return CombatType.RANGED;
            case MELEE:
                return CombatType.MELEE;
            default:
                return null;
        }
    }
}
