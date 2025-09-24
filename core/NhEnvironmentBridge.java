package net.runelite.client.plugins.autopvp.core;

import com.github.naton1.rl.env.nh.NhEnvironment;
import com.github.naton1.rl.env.nh.NhEnvironmentParams;
import com.github.naton1.rl.env.nh.NhLoadout;
import com.github.naton1.rl.env.nh.NhLmsMedLoadout;
import com.github.naton1.rl.env.nh.NhPureLoadout;
import com.github.naton1.rl.env.nh.NhZerkLoadout;
import com.github.naton1.rl.env.nh.NhMedLoadout;
import com.github.naton1.rl.env.nh.NhMaxLoadout;
import com.github.naton1.rl.env.nh.NhLmsPureLoadout;
import com.github.naton1.rl.env.nh.NhLmsZerkLoadout;
import com.elvarg.game.content.PrayerHandler;
import com.elvarg.game.model.Item;
import com.elvarg.game.content.skill.SkillManager;
import com.elvarg.game.model.Skill;
import com.elvarg.game.model.MagicSpellbook;
import com.github.naton1.rl.env.Loadout;
import com.elvarg.game.entity.impl.player.Player;
import net.runelite.client.plugins.autopvp.LoadoutOverride;
import net.runelite.client.plugins.autopvp.adapters.OpponentElvargPlayer;
import java.util.Arrays;
import net.runelite.client.plugins.autopvp.adapters.PlayerAdapter;
import net.runelite.client.plugins.autopvp.adapters.DummyElvargPlayer;
import net.runelite.client.plugins.autopvp.adapters.DelegatingElvargPlayer;
import net.runelite.client.plugins.autopvp.adapters.DynamicTargetPlayer;
import net.runelite.client.plugins.autopvp.adapters.CombatAdapter;
import net.runelite.client.plugins.autopvp.adapters.CombatFactoryAdapter;
import net.runelite.client.plugins.autopvp.adapters.TimerManagerAdapter;
import net.runelite.client.plugins.autopvp.adapters.DamageTrackerAdapter;
import net.runelite.client.plugins.autopvp.adapters.GearLoadoutTracker;
import net.runelite.client.plugins.autopvp.adapters.CombatHistoryTracker;
import net.runelite.client.plugins.autopvp.util.TargetEquipmentTranslator;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

/**
 * Wrapper that allows us to override the fight type of any loadout.
 * This ensures observations 160-161 reflect the actual detected game mode.
 */
class DynamicFightTypeLoadout implements NhLoadout {
    private final NhLoadout delegate;
    private final NhEnvironmentParams.FightType overrideFightType;

    public DynamicFightTypeLoadout(NhLoadout delegate, NhEnvironmentParams.FightType fightType) {
        this.delegate = delegate;
        this.overrideFightType = fightType;
    }

    @Override
    public NhEnvironmentParams.FightType getFightType() {
        return overrideFightType;
    }

    // Delegate all other methods to the wrapped loadout
    @Override
    public Item[] getInventory() { return delegate.getInventory(); }

    @Override
    public Item[] getEquipment() { return delegate.getEquipment(); }

    @Override
    public int[] getMageGear() { return delegate.getMageGear(); }

    @Override
    public int[] getRangedGear() { return delegate.getRangedGear(); }

    @Override
    public int[] getMeleeGear() { return delegate.getMeleeGear(); }

    @Override
    public int[] getTankGear() { return delegate.getTankGear(); }

    @Override
    public int[] getMeleeSpecGear() { return delegate.getMeleeSpecGear(); }

    @Override
    public PrayerHandler.PrayerData[] getMagePrayers() { return delegate.getMagePrayers(); }

    @Override
    public PrayerHandler.PrayerData[] getRangedPrayers() { return delegate.getRangedPrayers(); }

    @Override
    public PrayerHandler.PrayerData[] getMeleePrayers() { return delegate.getMeleePrayers(); }

    @Override
    public NhLoadout randomize(long seed) {
        // Delegate randomization and wrap the result with the same fight type override
        NhLoadout randomized = delegate.randomize(seed);
        return new DynamicFightTypeLoadout(randomized, overrideFightType);
    }

    @Override
    public Loadout.CombatStats getCombatStats() { return delegate.getCombatStats(); }

    @Override
    public MagicSpellbook getMagicSpellbook() { return delegate.getMagicSpellbook(); }
}

/**
 * Bridge class that connects the original NhEnvironment from Naton1's source
 * to our RuneLite adapters. This allows us to use the exact observation and
 * action logic from the RSPS implementation.
 */
@Slf4j
public class NhEnvironmentBridge {

    private final NhEnvironment environment;
    private final PlayerAdapter playerAdapter;
    private final DynamicTargetPlayer targetPlayer;
    private final NhLoadout loadout;
    private final GearLoadoutTracker gearLoadoutTracker;
    private final CombatHistoryTracker combatHistoryTracker;
    private final NhEnvironmentParams params;
    private final Client client;
    private final CombatAdapter combatAdapter;
    private final TimerManagerAdapter timerManagerAdapter;
    private final DamageTrackerAdapter damageTrackerAdapter;
    private final net.runelite.client.game.ItemManager itemManager;
    private final LoadoutOverride loadoutOverride;
    private final double[] loadoutBaselineBonuses;

    /**
     * Factory method to create appropriate loadout based on account build.
     * Citations: All loadout classes from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\
     */
    private static NhLoadout createLoadout(NhEnvironmentParams.AccountBuild build) {
        if (build == null) {
            build = NhEnvironmentParams.AccountBuild.MED;
        }
        switch (build) {
            case PURE: return new NhPureLoadout(); // NhPureLoadout.java
            case ZERKER: return new NhZerkLoadout(); // NhZerkLoadout.java
            case MED: return new NhMedLoadout(); // NhMedLoadout.java
            case MAXED: return new NhMaxLoadout(); // NhMaxLoadout.java
            case LMS_PURE: return new NhLmsPureLoadout(); // NhLmsPureLoadout.java
            case LMS_ZERKER: return new NhLmsZerkLoadout(); // NhLmsZerkLoadout.java
            case LMS_MED: return new NhLmsMedLoadout(); // NhLmsMedLoadout.java
            default: return new NhLmsMedLoadout(); // Default to LMS Med
        }
    }

    private LoadoutSelection selectLoadout(NhEnvironmentParams.AccountBuild defaultBuild)
    {
        Optional<NhEnvironmentParams.AccountBuild> overrideBuild =
            loadoutOverride == null ? Optional.empty() : loadoutOverride.getAccountBuild();

        if (overrideBuild.isPresent()) {
            NhEnvironmentParams.AccountBuild build = overrideBuild.get();
            NhLoadout loadout = createLoadout(build);
            String reason = String.format("Config override: %s", loadoutOverride);
            return new LoadoutSelection(build, loadout, reason);
        }

        Set<Integer> equipped = getCanonicalPlayerEquipment();
        if (equipped.isEmpty()) {
            NhEnvironmentParams.AccountBuild fallbackBuild = defaultBuild != null
                ? defaultBuild
                : NhEnvironmentParams.AccountBuild.MED;
            NhLoadout fallback = createLoadout(fallbackBuild);
            log.warn("[AUTOPVP] Unable to detect equipped items; defaulting to {} loadout.", fallbackBuild);
            return new LoadoutSelection(fallbackBuild, fallback, "Fallback: no equipment detected");
        }

        List<LoadoutScore> scores = new ArrayList<>();
        for (NhEnvironmentParams.AccountBuild build : NhEnvironmentParams.AccountBuild.values()) {
            NhLoadout candidate = createLoadout(build);
            LoadoutScore score = scoreLoadout(build, candidate, equipped);
            scores.add(score);
        }

        Comparator<LoadoutScore> comparator = Comparator
            .comparingInt(LoadoutScore::getScore)
            .thenComparingInt(LoadoutScore::getBaseMatches)
            .thenComparingInt(score -> score.getBuild() == defaultBuild ? 1 : 0)
            .thenComparingInt(LoadoutScore::getVariantMatches)
            .thenComparing(score -> score.getBuild().name());

        scores.sort(comparator.reversed());
        LoadoutScore best = scores.get(0);

        if (best.getScore() == 0 && best.getBaseMatches() == 0) {
            NhEnvironmentParams.AccountBuild fallbackBuild = defaultBuild != null
                ? defaultBuild
                : NhEnvironmentParams.AccountBuild.MED;
            NhLoadout fallback = createLoadout(fallbackBuild);
            log.warn("[AUTOPVP] Auto loadout matching found no overlap; defaulting to {}.", fallbackBuild);
            return new LoadoutSelection(fallbackBuild, fallback, "Fallback: no matching gear");
        }

        if (log.isDebugEnabled()) {
            log.debug("[AUTOPVP] Loadout match scores ({} equipped items):", equipped.size());
            for (LoadoutScore score : scores) {
                log.debug("[AUTOPVP]   {} -> score={} base {}/{} variant {}/{}",
                    score.getBuild(),
                    score.getScore(),
                    score.getBaseMatches(),
                    score.getBaseTotal(),
                    score.getVariantMatches(),
                    score.getVariantTotal());
            }
        }

        String reason = String.format("Auto match (%d/%d equipment, %d variant, score=%d)",
            best.getBaseMatches(), best.getBaseTotal(), best.getVariantMatches(), best.getScore());

        return new LoadoutSelection(best.getBuild(), best.getLoadout(), reason);
    }

    private LoadoutScore scoreLoadout(NhEnvironmentParams.AccountBuild build, NhLoadout loadout, Set<Integer> equipped)
    {
        Set<Integer> baseItems = canonicalizeEquipment(loadout.getEquipment());
        Set<Integer> variantItems = canonicalizeVariantGear(loadout, baseItems);
        int baseMatches = countMatches(equipped, baseItems);
        int variantMatches = countMatches(equipped, variantItems);
        int score = baseMatches * 100 + variantMatches * 10;
        return new LoadoutScore(build, loadout, baseMatches, baseItems.size(), variantMatches, variantItems.size(), score);
    }

    private Set<Integer> getCanonicalPlayerEquipment()
    {
        Set<Integer> equipped = new HashSet<>();
        if (playerAdapter == null || playerAdapter.getEquipment() == null) {
            return equipped;
        }

        Item[] items = playerAdapter.getEquipment().getItems();
        if (items == null) {
            return equipped;
        }

        for (Item item : items) {
            if (item == null) {
                continue;
            }
            int canonical = canonicalizeItemId(item.getId());
            if (canonical > 0) {
                equipped.add(canonical);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("[AUTOPVP] Canonical equipped items: {}", equipped);
        }

        return equipped;
    }

    private Set<Integer> canonicalizeEquipment(Item[] items)
    {
        Set<Integer> result = new HashSet<>();
        if (items == null) {
            return result;
        }

        for (Item item : items) {
            if (item == null) {
                continue;
            }
            int canonical = canonicalizeItemId(item.getId());
            if (canonical > 0) {
                result.add(canonical);
            }
        }

        return result;
    }

    private Set<Integer> canonicalizeVariantGear(NhLoadout loadout, Set<Integer> baseItems)
    {
        Set<Integer> variant = new HashSet<>();
        addItems(loadout.getMageGear(), variant);
        addItems(loadout.getRangedGear(), variant);
        addItems(loadout.getMeleeGear(), variant);
        addItems(loadout.getMeleeSpecGear(), variant);
        addItems(loadout.getTankGear(), variant);
        if (baseItems != null && !baseItems.isEmpty()) {
            variant.removeAll(baseItems);
        }
        return variant;
    }

    private void addItems(int[] itemIds, Set<Integer> sink)
    {
        if (itemIds == null || sink == null) {
            return;
        }

        for (int id : itemIds) {
            int canonical = canonicalizeItemId(id);
            if (canonical > 0) {
                sink.add(canonical);
            }
        }
    }

    private int canonicalizeItemId(int itemId)
    {
        if (itemId <= 0) {
            return -1;
        }

        try {
            return itemManager != null ? itemManager.canonicalize(itemId) : itemId;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[AUTOPVP] Failed to canonicalize item {}: {}", itemId, e.getMessage());
            }
            return itemId;
        }
    }

    private static int countMatches(Set<Integer> playerItems, Set<Integer> loadoutItems)
    {
        if (playerItems == null || playerItems.isEmpty() || loadoutItems == null) {
            return 0;
        }

        int matches = 0;
        for (int id : loadoutItems) {
            if (playerItems.contains(id)) {
                matches++;
            }
        }
        return matches;
    }

    private static final class LoadoutScore
    {
        private final NhEnvironmentParams.AccountBuild build;
        private final NhLoadout loadout;
        private final int baseMatches;
        private final int baseTotal;
        private final int variantMatches;
        private final int variantTotal;
        private final int score;

        LoadoutScore(NhEnvironmentParams.AccountBuild build, NhLoadout loadout,
                     int baseMatches, int baseTotal, int variantMatches, int variantTotal, int score)
        {
            this.build = build;
            this.loadout = loadout;
            this.baseMatches = baseMatches;
            this.baseTotal = baseTotal;
            this.variantMatches = variantMatches;
            this.variantTotal = variantTotal;
            this.score = score;
        }

        NhEnvironmentParams.AccountBuild getBuild()
        {
            return build;
        }

        NhLoadout getLoadout()
        {
            return loadout;
        }

        int getBaseMatches()
        {
            return baseMatches;
        }

        int getBaseTotal()
        {
            return baseTotal;
        }

        int getVariantMatches()
        {
            return variantMatches;
        }

        int getVariantTotal()
        {
            return variantTotal;
        }

        int getScore()
        {
            return score;
        }
    }

    private static final class LoadoutSelection
    {
        private final NhEnvironmentParams.AccountBuild build;
        private final NhLoadout loadout;
        private final String reason;

        LoadoutSelection(NhEnvironmentParams.AccountBuild build, NhLoadout loadout, String reason)
        {
            this.build = build;
            this.loadout = loadout;
            this.reason = reason;
        }

        NhEnvironmentParams.AccountBuild getBuild()
        {
            return build;
        }

        NhLoadout getLoadout()
        {
            return loadout;
        }

        String getReason()
        {
            return reason;
        }
    }

    /**
     * Detect the current game mode from RuneLite client.
     * @return The appropriate FightType for the current game mode
     */
    private NhEnvironmentParams.FightType detectGameMode() {
        if (client == null) {
            return NhEnvironmentParams.FightType.NORMAL;
        }

        java.util.EnumSet<net.runelite.api.WorldType> worldTypes = client.getWorldType();
        if (worldTypes.contains(net.runelite.api.WorldType.LAST_MAN_STANDING)) {
            log.debug("[AUTOPVP] Detected Last Man Standing world");
            return NhEnvironmentParams.FightType.LMS;
        }

        if (worldTypes.contains(net.runelite.api.WorldType.PVP_ARENA)) {
            log.debug("[AUTOPVP] Detected PvP Arena world");
            return NhEnvironmentParams.FightType.PVP_ARENA;
        }

        if (worldTypes.contains(net.runelite.api.WorldType.PVP) || isInWilderness()) {
            log.debug("[AUTOPVP] Detected PvP/Wilderness context");
            return NhEnvironmentParams.FightType.NORMAL;
        }

        log.debug("[AUTOPVP] Defaulting to NORMAL fight type");
        return NhEnvironmentParams.FightType.NORMAL;
    }

    /**
     * Detect the appropriate account build based on combat levels.
     * @return The appropriate AccountBuild for the player's stats
     */
    private boolean isInWilderness() {
        if (client == null) {
            return false;
        }

        try {
            return client.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
        } catch (Exception e) {
            log.debug("[AUTOPVP] Failed to read IN_WILDERNESS varbit", e);
            return false;
        }
    }

    private NhEnvironmentParams.AccountBuild detectAccountBuild(NhEnvironmentParams.FightType fightType) {
        if (playerAdapter == null || playerAdapter.getSkillManager() == null) {
            NhEnvironmentParams.AccountBuild fallback = fightType == NhEnvironmentParams.FightType.LMS
                ? NhEnvironmentParams.AccountBuild.LMS_MED
                : NhEnvironmentParams.AccountBuild.MED;
            log.debug("[AUTOPVP] Skill data unavailable; defaulting account build to {}", fallback);
            return fallback;
        }

        SkillManager skills = playerAdapter.getSkillManager();
        int defence = skills.getMaxLevel(Skill.DEFENCE);
        int attack = skills.getMaxLevel(Skill.ATTACK);
        int prayer = skills.getMaxLevel(Skill.PRAYER);

        boolean isLms = fightType == NhEnvironmentParams.FightType.LMS;
        NhEnvironmentParams.AccountBuild build;

        if (defence == 1) {
            build = isLms ? NhEnvironmentParams.AccountBuild.LMS_PURE : NhEnvironmentParams.AccountBuild.PURE;
        } else if (defence <= 45) {
            build = isLms ? NhEnvironmentParams.AccountBuild.LMS_ZERKER : NhEnvironmentParams.AccountBuild.ZERKER;
        } else if (defence <= 70) {
            build = isLms ? NhEnvironmentParams.AccountBuild.LMS_MED : NhEnvironmentParams.AccountBuild.MED;
        } else {
            build = NhEnvironmentParams.AccountBuild.MAXED;
        }

        if (log.isDebugEnabled()) {
            log.debug("[AUTOPVP] Account build detection -> def={}, att={}, pray={}, lms={}, build={}",
                defence, attack, prayer, isLms, build);
        }

        return build;
    }

    /**
     * Create a bridge with our PlayerAdapter as the agent.
     * Uses a dynamic target that can switch between dummy and real opponent.
     */
    public NhEnvironmentBridge(Client client, PlayerAdapter playerAdapter, CombatAdapter combatAdapter,
            TimerManagerAdapter timerManagerAdapter, DamageTrackerAdapter damageTrackerAdapter,
            net.runelite.client.game.ItemManager itemManager, GearLoadoutTracker gearLoadoutTracker,
            CombatHistoryTracker combatHistoryTracker, LoadoutOverride loadoutOverride) {
        this.client = client;
        this.playerAdapter = playerAdapter;
        this.combatAdapter = combatAdapter;
        this.timerManagerAdapter = Objects.requireNonNull(timerManagerAdapter, "timerManagerAdapter");
        this.damageTrackerAdapter = Objects.requireNonNull(damageTrackerAdapter, "damageTrackerAdapter");
        this.gearLoadoutTracker = gearLoadoutTracker;
        this.combatHistoryTracker = combatHistoryTracker;
        this.itemManager = itemManager;
        this.loadoutOverride = loadoutOverride != null ? loadoutOverride : LoadoutOverride.AUTO;

        DelegatingElvargPlayer agentPlayer = new DelegatingElvargPlayer(playerAdapter);

        this.targetPlayer = new DynamicTargetPlayer(client, itemManager, combatAdapter);

        NhEnvironmentParams.FightType gameMode = detectGameMode();
        NhEnvironmentParams.AccountBuild defaultBuild = detectAccountBuild(gameMode);

        LoadoutSelection selection = selectLoadout(defaultBuild);

        this.params = new NhEnvironmentParams()
            .setAccountBuild(selection.getBuild())
            .setFightType(gameMode)
            .setAllowSmite(true)
            .setAllowRedemption(true)
            .setOnlySwitchPrayerWhenAboutToAttack(true)
            .setOnlySwitchGearWhenAttackSoon(true);

        NhLoadout baseLoadout = selection.getLoadout();
        this.loadout = new DynamicFightTypeLoadout(baseLoadout, gameMode);

        this.environment = new NhEnvironment(
            agentPlayer,
            targetPlayer,
            null,
            loadout,
            params
        );

        combatAdapter.setEnvironmentBridge(this);
        double[] baseline = TargetEquipmentTranslator.computeBaseline(itemManager,
            loadout.getMeleeGear(),
            loadout.getRangedGear(),
            loadout.getMageGear(),
            loadout.getTankGear(),
            loadout.getMeleeSpecGear());
        this.loadoutBaselineBonuses = baseline != null ? baseline.clone() : new double[TargetEquipmentTranslator.BONUS_COUNT];
        combatAdapter.setTargetGearBaseline(this.loadoutBaselineBonuses);
        combatAdapter.setOpponentTargetSupplier(this::getDynamicTargetPlayer);

        log.info("[AUTOPVP] NhEnvironmentBridge initialized with {} build in {} mode ({})",
                 params.getAccountBuild(), params.getFightType(), selection.getReason());
    }

    /**
     * Get observations from the environment.
     * Returns a List<Number> with exactly 176 observations.
     */
    public List<Number> getObservations() {
        try {
            float[] encoded = NhObservationEncoder.encode(client, timerManagerAdapter, damageTrackerAdapter);
            List<Number> observations = new ArrayList<>(encoded.length);
            for (float value : encoded) {
                observations.add(value);
            }

            // Add client-specific observations
            applyCombatHistoryObservations(observations);
            applyGearObservations(observations);

            // Log detailed observations when attacking a real target
            if (targetPlayer.getHitpoints() > 0) {
                logKeyObservations(observations);
            }

            return observations;
        } catch (RuntimeException e) {
            // Let exceptions bubble up - no dummy data
            log.error("[AUTOPVP] Error getting observations", e);
            throw e;
        }
    }

    /**
     * Get action masks showing which actions are valid.
     * Returns a List<List<Boolean>> for 12 action heads.
     */
    public List<List<Boolean>> getActionMasks() {
        try {
            return environment.getActionMasks();
        } catch (RuntimeException e) {
            // Let exceptions bubble up - no dummy data
            log.error("[AUTOPVP] Error getting action masks", e);
            throw e;
        }
    }


    /**
     * Execute an action in the environment.
     * The action array should have 12 elements, one for each action head.
     */
    public void processAction(int[] action) {
        if (action == null || action.length != 12) {
            log.error("[AUTOPVP] Invalid action array: expected 12 elements, got {}",
                     action == null ? "null" : action.length);
            return;
        }

        // NO-OP: We don't want to execute Elvarg actions
        // The Elvarg environment expects to modify RSPS game state which doesn't exist in RuneLite
        // All actions are executed directly through RuneLite API in ActionExecutor
        log.debug("[AUTOPVP] processAction called but skipped (no-op) - actions execute via RuneLite API");
    }

    /**
     * Call onTickStart on the environment.
     */
    public void onTickStart() {
        // Update DynamicTargetPlayer delegate to use real opponent if available
        targetPlayer.updateDelegate();
        environment.onTickStart();
    }

    /**
     * Call onTickProcessed on the environment.
     */
    public void onTickProcessed() {
        environment.onTickProcessed();
    }

    /**
     * Call onTickEnd on the environment.
     */
    public void onTickEnd() {
        environment.onTickEnd();
    }

    /**
     * Update the target player delegate based on current combat state.
     * Called when combat state changes.
     */
    public void updateTarget() {
        // Update DynamicTargetPlayer's delegate to reflect the actual target
        if (targetPlayer instanceof DynamicTargetPlayer) {
            DynamicTargetPlayer dynamicTarget = (DynamicTargetPlayer) targetPlayer;
            dynamicTarget.updateDelegate();

            // Trigger environment refresh by calling onAdd() on the target
            // Citation: C:/dev/naton/osrs-pvp-reinforcement-learning/src/main/java/com/github/naton1/rl/env/nh/NhEnvironment.java:68
            if (environment != null && environment.getTarget() != null) {
                environment.getTarget().onAdd();

                // Initialize lastTargetHealthPercent when switching to real opponent
                // Citation: C:/dev/naton/osrs-pvp-reinforcement-learning/simulation-rsps/ElvargServer/src/main/java/com/github/naton1/rl/env/nh/NhEnvironment.java:109,921,1818-1819
                // lastTargetHealthPercent is only updated on hitsplat, defaults to 0
                // We must initialize it to actual health when combat starts
                if (combatAdapter.getTarget() instanceof OpponentElvargPlayer) {
                    try {
                        java.lang.reflect.Field healthField = environment.getClass().getDeclaredField("lastTargetHealthPercent");
                        healthField.setAccessible(true);

                        // Get actual HP percentage from the real opponent
                        OpponentElvargPlayer opponent = (OpponentElvargPlayer) combatAdapter.getTarget();
                        double healthPercent = opponent.getHitpoints() / 99.0; // 99 is max HP in PvP
                        healthField.set(environment, healthPercent);

                        log.debug("[AUTOPVP] Initialized lastTargetHealthPercent to {} for opponent", healthPercent);
                    } catch (Exception e) {
                        log.warn("[AUTOPVP] Could not initialize lastTargetHealthPercent", e);
                    }
                }
            }

            log.info("[AUTOPVP] Target handoff completed");
        } else {
            log.warn("[AUTOPVP] Target is not DynamicTargetPlayer, cannot update");
        }
    }

    /**
     * Get the tank gear loadout from the environment.
     * Delegates to loadout.getTankGear() from the NhLoadout implementation.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     *
     * @return Array of item IDs for tank gear
     */
    public DynamicTargetPlayer getDynamicTargetPlayer() {
        return targetPlayer;
    }

    public double[] getLoadoutBaselineBonuses() {
        return loadoutBaselineBonuses.clone();
    }

    public int[] getTankGear() {
        return loadout.getTankGear();
    }

    /**
     * Get the mage gear loadout from the environment.
     * Delegates to loadout.getMageGear() from the NhLoadout implementation.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     *
     * @return Array of item IDs for mage gear
     */
    public int[] getMageGear() {
        return loadout.getMageGear();
    }

    /**
     * Get the ranged gear loadout from the environment.
     * Delegates to loadout.getRangedGear() from the NhLoadout implementation.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     *
     * @return Array of item IDs for ranged gear
     */
    public int[] getRangedGear() {
        return loadout.getRangedGear();
    }

    /**
     * Get the melee gear loadout from the environment.
     * Delegates to loadout.getMeleeGear() from the NhLoadout implementation.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     *
     * @return Array of item IDs for melee gear
     */
    public int[] getMeleeGear() {
        return loadout.getMeleeGear();
    }

    /**
     * Get the mage prayers from the environment.
     * Delegates to loadout.getMagePrayers() and converts PrayerData to ordinals.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     * Citation: PrayerData enum from C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\PrayerHandler.java
     *
     * @return Array of prayer ordinals for mage combat
     */
    public int[] getMagePrayers() {
        PrayerHandler.PrayerData[] prayers = loadout.getMagePrayers();
        return Arrays.stream(prayers).mapToInt(PrayerHandler.PrayerData::ordinal).toArray();
    }

    /**
     * Get the ranged prayers from the environment.
     * Delegates to loadout.getRangedPrayers() and converts PrayerData to ordinals.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     * Citation: PrayerData enum from C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\PrayerHandler.java
     *
     * @return Array of prayer ordinals for ranged combat
     */
    public int[] getRangedPrayers() {
        PrayerHandler.PrayerData[] prayers = loadout.getRangedPrayers();
        return Arrays.stream(prayers).mapToInt(PrayerHandler.PrayerData::ordinal).toArray();
    }

    /**
     * Get the melee prayers from the environment.
     * Delegates to loadout.getMeleePrayers() and converts PrayerData to ordinals.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     * Citation: PrayerData enum from C:\dev\elvarg-rsps-master\ElvargServer\src\main\java\com\elvarg\game\content\PrayerHandler.java
     *
     * @return Array of prayer ordinals for melee combat
     */
    public int[] getMeleePrayers() {
        PrayerHandler.PrayerData[] prayers = loadout.getMeleePrayers();
        return Arrays.stream(prayers).mapToInt(PrayerHandler.PrayerData::ordinal).toArray();
    }

    /**
     * Get the melee special attack gear loadout from the environment.
     * Delegates to loadout.getMeleeSpecGear() from the NhLoadout implementation.
     * Citation: NhLoadout interface method from C:\dev\naton\osrs-pvp-reinforcement-learning\src\main\java\com\github\naton1\rl\env\nh\NhLoadout.java
     *
     * @return Array of item IDs for melee spec gear
     */
    public int[] getMeleeSpecGear() {
        return loadout.getMeleeSpecGear();
    }

    /**
     * Update the target player (for when we detect a real opponent).
     */
    public void updateTarget(Player newTarget) {
        // This would require recreating the environment with the new target
        // For now, log that we would update the target
        log.info("[AUTOPVP] Target update requested (not yet implemented)");
    }

    /**
     * Get the underlying NhEnvironment for direct access if needed.
     */
    private void applyGearObservations(List<Number> observations) {
        if (gearLoadoutTracker == null || observations == null || observations.size() < NhContract.OBS_SIZE) {
            return;
        }

        setObservation(observations, 103, gearLoadoutTracker.getIsEnchantedDragonBolts());
        setObservation(observations, 104, gearLoadoutTracker.getIsEnchantedOpalBolts());
        setObservation(observations, 105, gearLoadoutTracker.getIsEnchantedDiamondBolts());
        setObservation(observations, 106, gearLoadoutTracker.getIsMageSpecWeapon());
        setObservation(observations, 107, gearLoadoutTracker.getIsRangeSpecWeapon());
        setObservation(observations, 108, gearLoadoutTracker.getIsNightmareStaff());
        setObservation(observations, 109, gearLoadoutTracker.getIsZaryteCrossbow());
        setObservation(observations, 110, gearLoadoutTracker.getIsBallista());
        setObservation(observations, 111, gearLoadoutTracker.getIsMorrigansJavelins());
        setObservation(observations, 112, gearLoadoutTracker.getIsDragonKnives());
        setObservation(observations, 113, gearLoadoutTracker.getIsDarkBow());
        setObservation(observations, 114, gearLoadoutTracker.getIsMeleeSpecDclaws());
        setObservation(observations, 115, gearLoadoutTracker.getIsMeleeSpecDds());
        setObservation(observations, 116, gearLoadoutTracker.getIsMeleeSpecAgs());
        setObservation(observations, 117, gearLoadoutTracker.getIsMeleeSpecVls());
        setObservation(observations, 118, gearLoadoutTracker.getIsMeleeSpecStatHammer());
        setObservation(observations, 119, gearLoadoutTracker.getIsMeleeSpecAncientGodsword());
        setObservation(observations, 120, gearLoadoutTracker.getIsMeleeSpecGraniteMaul());
        setObservation(observations, 121, gearLoadoutTracker.getIsBloodFury());
        setObservation(observations, 122, gearLoadoutTracker.getIsDharoksSet());
        setObservation(observations, 123, gearLoadoutTracker.getIsZurielStaff());

        setObservation(observations, 124, gearLoadoutTracker.getMagicGearAccuracy());
        setObservation(observations, 125, gearLoadoutTracker.getMagicGearStrength());
        setObservation(observations, 126, gearLoadoutTracker.getRangedGearAccuracy());
        setObservation(observations, 127, gearLoadoutTracker.getRangedGearStrength());
        setObservation(observations, 128, gearLoadoutTracker.getRangedGearAttackSpeed());
        setObservation(observations, 129, gearLoadoutTracker.getRangedGearAttackRange());
        setObservation(observations, 130, gearLoadoutTracker.getMeleeGearAccuracy());
        setObservation(observations, 131, gearLoadoutTracker.getMeleeGearStrength());
        setObservation(observations, 132, gearLoadoutTracker.getMeleeGearAttackSpeed());
        setObservation(observations, 133, gearLoadoutTracker.getMagicGearRangedDefence());
        setObservation(observations, 134, gearLoadoutTracker.getMagicGearMageDefence());
        setObservation(observations, 135, gearLoadoutTracker.getMagicGearMeleeDefence());
        setObservation(observations, 136, gearLoadoutTracker.getRangedGearRangedDefence());
        setObservation(observations, 137, gearLoadoutTracker.getRangedGearMageDefence());
        setObservation(observations, 138, gearLoadoutTracker.getRangedGearMeleeDefence());
        setObservation(observations, 139, gearLoadoutTracker.getMeleeGearRangedDefence());
        setObservation(observations, 140, gearLoadoutTracker.getMeleeGearMageDefence());
        setObservation(observations, 141, gearLoadoutTracker.getMeleeGearMeleeDefence());

        setObservation(observations, 142, gearLoadoutTracker.getTargetCurrentGearRangedDefence());
        setObservation(observations, 143, gearLoadoutTracker.getTargetCurrentGearMageDefence());
        setObservation(observations, 144, gearLoadoutTracker.getTargetCurrentGearMeleeDefence());
        setObservation(observations, 145, gearLoadoutTracker.getTargetLastMagicGearAccuracy());
        setObservation(observations, 146, gearLoadoutTracker.getTargetLastMagicGearStrength());
        setObservation(observations, 147, gearLoadoutTracker.getTargetLastRangedGearAccuracy());
        setObservation(observations, 148, gearLoadoutTracker.getTargetLastRangedGearStrength());
        setObservation(observations, 149, gearLoadoutTracker.getTargetLastMeleeGearAccuracy());
        setObservation(observations, 150, gearLoadoutTracker.getTargetLastMeleeGearStrength());
        setObservation(observations, 151, gearLoadoutTracker.getTargetLastMagicGearRangedDefence());
        setObservation(observations, 152, gearLoadoutTracker.getTargetLastMagicGearMageDefence());
        setObservation(observations, 153, gearLoadoutTracker.getTargetLastMagicGearMeleeDefence());
        setObservation(observations, 154, gearLoadoutTracker.getTargetLastRangedGearRangedDefence());
        setObservation(observations, 155, gearLoadoutTracker.getTargetLastRangedGearMageDefence());
        setObservation(observations, 156, gearLoadoutTracker.getTargetLastRangedGearMeleeDefence());
        setObservation(observations, 157, gearLoadoutTracker.getTargetLastMeleeGearRangedDefence());
        setObservation(observations, 158, gearLoadoutTracker.getTargetLastMeleeGearMageDefence());
        setObservation(observations, 159, gearLoadoutTracker.getTargetLastMeleeGearMeleeDefence());
    }
    private void applyCombatHistoryObservations(List<Number> observations) {
        if (combatHistoryTracker == null || observations == null || observations.size() < NhContract.OBS_SIZE) {
            return;
        }

        setObservation(observations, 49, combatHistoryTracker.didPlayerJustAttack() ? 1.0 : 0.0);
        setObservation(observations, 52, combatHistoryTracker.getHitsplatsOnAgentScale());
        setObservation(observations, 53, combatHistoryTracker.getHitsplatsOnTargetScale());
        setObservation(observations, 65, combatHistoryTracker.getDamageDealtScale());
        setObservation(observations, 66, combatHistoryTracker.getTargetHitConfidence());
        setObservation(observations, 67, combatHistoryTracker.getTargetHitMeleeRatio());
        setObservation(observations, 68, combatHistoryTracker.getTargetHitMageRatio());
        setObservation(observations, 69, combatHistoryTracker.getTargetHitRangeRatio());
        setObservation(observations, 70, combatHistoryTracker.getPlayerHitMeleeRatio());
        setObservation(observations, 71, combatHistoryTracker.getPlayerHitMageRatio());
        setObservation(observations, 72, combatHistoryTracker.getPlayerHitRangeRatio());
        setObservation(observations, 73, combatHistoryTracker.getTargetHitCorrectRatio());
        setObservation(observations, 74, combatHistoryTracker.getTargetPrayConfidence());
        setObservation(observations, 75, combatHistoryTracker.getTargetPrayMageRatio());
        setObservation(observations, 76, combatHistoryTracker.getTargetPrayRangeRatio());
        setObservation(observations, 77, combatHistoryTracker.getTargetPrayMeleeRatio());
        setObservation(observations, 78, combatHistoryTracker.getPlayerPrayMageRatio());
        setObservation(observations, 79, combatHistoryTracker.getPlayerPrayRangeRatio());
        setObservation(observations, 80, combatHistoryTracker.getPlayerPrayMeleeRatio());
        setObservation(observations, 81, combatHistoryTracker.getTargetPrayCorrectRatio());
        setObservation(observations, 82, combatHistoryTracker.getRecentTargetHitMeleeRatio());
        setObservation(observations, 83, combatHistoryTracker.getRecentTargetHitMageRatio());
        setObservation(observations, 84, combatHistoryTracker.getRecentTargetHitRangeRatio());
        setObservation(observations, 85, combatHistoryTracker.getRecentPlayerHitMeleeRatio());
        setObservation(observations, 86, combatHistoryTracker.getRecentPlayerHitMageRatio());
        setObservation(observations, 87, combatHistoryTracker.getRecentPlayerHitRangeRatio());
        setObservation(observations, 88, combatHistoryTracker.getRecentTargetHitCorrectRatio());
        setObservation(observations, 89, combatHistoryTracker.getRecentTargetPrayMageRatio());
        setObservation(observations, 90, combatHistoryTracker.getRecentTargetPrayRangeRatio());
        setObservation(observations, 91, combatHistoryTracker.getRecentTargetPrayMeleeRatio());
        setObservation(observations, 92, combatHistoryTracker.getRecentPlayerPrayMageRatio());
        setObservation(observations, 93, combatHistoryTracker.getRecentPlayerPrayRangeRatio());
        setObservation(observations, 94, combatHistoryTracker.getRecentPlayerPrayMeleeRatio());
        setObservation(observations, 95, combatHistoryTracker.getRecentTargetPrayCorrectRatio());
    }

    private void setObservation(List<Number> observations, int index, double value) {
        observations.set(index, value);
    }

    public NhEnvironment getEnvironment() {
        return environment;
    }

    private void logKeyObservations(List<Number> observations) {
        // Log key values using INFO level so they're always visible
        log.info("[OBSERVATIONS] ========== AI Sees Real Opponent ==========");

        // Player state
        log.info("[OBSERVATIONS] Player Health: {}%, Target Health: {}%",
            getObs(observations, 10), getObs(observations, 11));
        log.info("[OBSERVATIONS] Special Attack: {}%, Prayer Points: {}",
            getObs(observations, 4), getObs(observations, 28));

        // Equipment
        log.info("[OBSERVATIONS] Player Equipment: Melee={}, Ranged={}, Mage={}",
            getObs(observations, 0), getObs(observations, 1), getObs(observations, 2));
        log.info("[OBSERVATIONS] Target Equipment: Melee={}, Ranged={}, Mage={}",
            getObs(observations, 12), getObs(observations, 13), getObs(observations, 14));

        // Prayers
        log.info("[OBSERVATIONS] Player Prayers: ProtMelee={}, ProtRange={}, ProtMage={}",
            getObs(observations, 5), getObs(observations, 6), getObs(observations, 7));
        log.info("[OBSERVATIONS] Target Prayers: ProtMelee={}, ProtRange={}, ProtMage={}",
            getObs(observations, 16), getObs(observations, 17), getObs(observations, 18));

        // Consumables
        log.info("[OBSERVATIONS] Food: {}, Karambwan: {}",
            getObs(observations, 26), getObs(observations, 27));
        log.info("[OBSERVATIONS] Potions: Range={}, Combat={}, Restore={}, Brew={}",
            getObs(observations, 22), getObs(observations, 23),
            getObs(observations, 24), getObs(observations, 25));

        // Combat state
        log.info("[OBSERVATIONS] Distance: {}, In Melee Range: {}, Attacking: {}",
            getObs(observations, 62), getObs(observations, 33), getObs(observations, 54));

        log.info("[OBSERVATIONS] ==========================================");
    }

    private Number getObs(List<Number> observations, int index) {
        if (index < observations.size()) {
            return observations.get(index);
        }
        return 0;
    }
}

















