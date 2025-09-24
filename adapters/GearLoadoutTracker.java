package net.runelite.client.plugins.autopvp.adapters;

import com.elvarg.game.content.combat.CombatType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.autopvp.util.TargetEquipmentTranslator;

/**
 * Approximates gear/loadout observations using RuneLite data.
 * Mirrors naton1's NhEnvironment gear logic with RuneLite item metadata.
 */
@Slf4j
public class GearLoadoutTracker
{
    private static final double DEFAULT_RANGED_ATTACK_RANGE = 7.0;
    private static final int MIN_ATTACK_SPEED = 1;

    private final Client client;
    private final EventBus eventBus;
    private final ItemManager itemManager;
    private final TimerManagerAdapter timerManagerAdapter;

    private final Set<String> currentItemNames = new HashSet<>();

    private static final int TARGET_LOSS_RESET_TICKS = 12;

    private CombatAdapter combatAdapter;

    private Player lastObservedTarget;
    private int lastObservedTargetTick;
    private TargetEquipmentTranslator.Result lastTargetTranslation;
    private final double[] targetBaselineBonuses = new double[TargetEquipmentTranslator.BONUS_COUNT];
    private final double[] lastTargetBlendedBonuses = new double[TargetEquipmentTranslator.BONUS_COUNT];
    private int targetStaleTicks;

    private ItemContainer lastEquipment;
    private ItemContainer lastInventory;
    private String currentWeaponName;

    // Player gear booleans (obs 103-123)
    private boolean enchantedDragonBolts;
    private boolean enchantedOpalBolts;
    private boolean enchantedDiamondBolts;
    private boolean mageSpecWeapon;
    private boolean rangeSpecWeapon;
    private boolean nightmareStaff;
    private boolean zaryteCrossbow;
    private boolean ballista;
    private boolean morrigansJavelins;
    private boolean dragonKnives;
    private boolean darkBow;
    private boolean meleeSpecDclaws;
    private boolean meleeSpecDds;
    private boolean meleeSpecAgs;
    private boolean meleeSpecVls;
    private boolean meleeSpecStatHammer;
    private boolean meleeSpecAncientGodsword;
    private boolean meleeSpecGraniteMaul;
    private boolean bloodFury;
    private boolean dharoksSet;
    private boolean zurielStaff;

    // Player gear stats (obs 124-141)
    private double magicGearAccuracy;
    private double magicGearStrength;
    private double rangedGearAccuracy;
    private double rangedGearStrength;
    private int rangedGearAttackSpeed;
    private double rangedGearAttackRange;
    private double meleeGearAccuracy;
    private double meleeGearStrength;
    private int meleeGearAttackSpeed;
    private double magicGearRangedDefence;
    private double magicGearMageDefence;
    private double magicGearMeleeDefence;
    private double rangedGearRangedDefence;
    private double rangedGearMageDefence;
    private double rangedGearMeleeDefence;
    private double meleeGearRangedDefence;
    private double meleeGearMageDefence;
    private double meleeGearMeleeDefence;

    // Target gear stats (obs 142-159)
    private double targetCurrentGearRangedDefence;
    private double targetCurrentGearMageDefence;
    private double targetCurrentGearMeleeDefence;

    private double targetLastMagicGearAccuracy;
    private double targetLastMagicGearStrength;
    private double targetLastMagicGearRangedDefence;
    private double targetLastMagicGearMageDefence;
    private double targetLastMagicGearMeleeDefence;

    private double targetLastRangedGearAccuracy;
    private double targetLastRangedGearStrength;
    private double targetLastRangedGearRangedDefence;
    private double targetLastRangedGearMageDefence;
    private double targetLastRangedGearMeleeDefence;

    private double targetLastMeleeGearAccuracy;
    private double targetLastMeleeGearStrength;
    private double targetLastMeleeGearRangedDefence;
    private double targetLastMeleeGearMageDefence;
    private double targetLastMeleeGearMeleeDefence;

    public GearLoadoutTracker(Client client,
                              EventBus eventBus,
                              ItemManager itemManager,
                              TimerManagerAdapter timerManagerAdapter)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.itemManager = itemManager;
        this.timerManagerAdapter = timerManagerAdapter;
        eventBus.register(this);
    }

    public void setCombatAdapter(CombatAdapter combatAdapter)
    {
        this.combatAdapter = combatAdapter;
    }

    public void setTargetBaselineBonuses(double[] baseline)
    {
        if (baseline == null)
        {
            Arrays.fill(this.targetBaselineBonuses, 0.0);
            return;
        }

        Arrays.fill(this.targetBaselineBonuses, 0.0);
        System.arraycopy(baseline, 0, this.targetBaselineBonuses, 0, Math.min(baseline.length, this.targetBaselineBonuses.length));
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        updateLocalGearState();
        updateTargetGearState();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return;
        }

        Actor actor = event.getActor();
        if (actor == null)
        {
            return;
        }

        Actor interacting = localPlayer.getInteracting();
        if (!(interacting instanceof Player))
        {
            return;
        }

        Player targetPlayer = (Player) interacting;

        if (actor == localPlayer)
        {
            TargetEquipmentTranslator.Result translation = translateTarget(targetPlayer);
            if (translation != null)
            {
                updateTargetCurrentStats(translation);
                CombatType targetStyle = timerManagerAdapter.getTargetLastAttackType(targetPlayer);
                updateTargetLastStats(targetStyle, translation);
            }
        }
        else if (actor == targetPlayer)
        {
            TargetEquipmentTranslator.Result translation = translateTarget(targetPlayer);
            if (translation != null)
            {
                updateTargetCurrentStats(translation);
            }
        }
    }

    public void shutdown()
    {
        eventBus.unregister(this);
    }

    public void onTickEnd()
    {
        // no per-tick decay required yet; present for API symmetry
    }

    private void updateLocalGearState()
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        lastEquipment = equipment;
        lastInventory = inventory;

        collectCurrentItemNames(equipment, inventory);

        enchantedDragonBolts = hasNameEqual("dragon bolts (e)")
            || hasNameContaining("dragonstone dragon bolts (e)");
        enchantedOpalBolts = hasNameContaining("opal bolts (e)", "opal dragon bolts (e)");
        enchantedDiamondBolts = hasNameContaining("diamond bolts (e)", "diamond dragon bolts (e)");

        nightmareStaff = hasNameContaining("nightmare staff");
        zurielStaff = hasNameContaining("zuriel's staff");
        mageSpecWeapon = nightmareStaff;

        zaryteCrossbow = hasNameContaining("zaryte crossbow");
        ballista = hasNameContaining("light ballista", "heavy ballista");
        morrigansJavelins = hasNameContaining("morrigan's javelin");
        dragonKnives = hasNameContaining("dragon knife");
        darkBow = hasNameContaining("dark bow");

        rangeSpecWeapon = zaryteCrossbow || ballista || morrigansJavelins || darkBow || dragonKnives;

        meleeSpecDclaws = hasNameContaining("dragon claw");
        meleeSpecDds = hasNameContaining("dragon dagger");
        meleeSpecAgs = hasNameContaining("armadyl godsword");
        meleeSpecVls = hasNameContaining("vesta's longsword");
        meleeSpecStatHammer = hasNameContaining("statius's warhammer");
        meleeSpecAncientGodsword = hasNameContaining("ancient godsword");
        meleeSpecGraniteMaul = hasNameContaining("granite maul");

        bloodFury = hasNameContaining("blood fury");
        dharoksSet = hasNameContaining("dharok's greataxe");

        recomputeLocalBonuses(equipment);
    }

    private void collectCurrentItemNames(ItemContainer... containers)
    {
        currentItemNames.clear();
        for (ItemContainer container : containers)
        {
            if (container == null)
            {
                continue;
            }

            Item[] items = container.getItems();
            if (items == null)
            {
                continue;
            }

            for (Item item : items)
            {
                if (item == null || item.getId() <= 0)
                {
                    continue;
                }

                ItemComposition comp = itemManager.getItemComposition(item.getId());
                if (comp != null)
                {
                    currentItemNames.add(comp.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private void recomputeLocalBonuses(ItemContainer equipment)
    {
        magicGearAccuracy = 0;
        magicGearStrength = 0;
        rangedGearAccuracy = 0;
        rangedGearStrength = 0;
        meleeGearAccuracy = 0;
        meleeGearStrength = 0;
        magicGearRangedDefence = 0;
        magicGearMageDefence = 0;
        magicGearMeleeDefence = 0;
        rangedGearRangedDefence = 0;
        rangedGearMageDefence = 0;
        rangedGearMeleeDefence = 0;
        meleeGearRangedDefence = 0;
        meleeGearMageDefence = 0;
        meleeGearMeleeDefence = 0;
        rangedGearAttackSpeed = MIN_ATTACK_SPEED;
        meleeGearAttackSpeed = MIN_ATTACK_SPEED;
        rangedGearAttackRange = DEFAULT_RANGED_ATTACK_RANGE;
        currentWeaponName = null;

        if (equipment == null)
        {
            return;
        }

        Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        ItemEquipmentStats weaponStats = null;
        if (weapon != null && weapon.getId() > 0)
        {
            ItemStats stats = itemManager.getItemStats(weapon.getId());
            if (stats != null)
            {
                weaponStats = stats.getEquipment();
            }

            ItemComposition comp = itemManager.getItemComposition(weapon.getId());
            if (comp != null)
            {
                currentWeaponName = comp.getName().toLowerCase(Locale.ROOT);
            }
        }

        if (weaponStats != null)
        {
            int rawSpeed = Math.max(MIN_ATTACK_SPEED, weaponStats.getAspeed());
            CombatType weaponType = determineWeaponType(weaponStats, currentWeaponName);
            if (weaponType == CombatType.RANGED)
            {
                rangedGearAttackSpeed = Math.max(MIN_ATTACK_SPEED, rawSpeed - 1);
                meleeGearAttackSpeed = rawSpeed;
                rangedGearAttackRange = lookupRangedAttackRange(currentWeaponName);
            }
            else
            {
                meleeGearAttackSpeed = rawSpeed;
                rangedGearAttackSpeed = Math.max(MIN_ATTACK_SPEED, rawSpeed - 1);
                rangedGearAttackRange = lookupRangedAttackRange(currentWeaponName);
            }
        }

        Item[] items = equipment.getItems();
        if (items == null)
        {
            return;
        }

        for (Item item : items)
        {
            if (item == null || item.getId() <= 0)
            {
                continue;
            }

            ItemStats stats = itemManager.getItemStats(item.getId());
            if (stats == null)
            {
                continue;
            }

            ItemEquipmentStats equip = stats.getEquipment();
            if (equip == null)
            {
                continue;
            }

            magicGearAccuracy += equip.getAmagic();
            magicGearStrength += equip.getMdmg();
            rangedGearAccuracy += equip.getArange();
            rangedGearStrength += equip.getRstr();
            meleeGearAccuracy += equip.getAslash();
            meleeGearStrength += equip.getStr();

            magicGearRangedDefence += equip.getDrange();
            magicGearMageDefence += equip.getDmagic();
            magicGearMeleeDefence += equip.getDslash();

            rangedGearRangedDefence += equip.getDrange();
            rangedGearMageDefence += equip.getDmagic();
            rangedGearMeleeDefence += equip.getDslash();

            meleeGearRangedDefence += equip.getDrange();
            meleeGearMageDefence += equip.getDmagic();
            meleeGearMeleeDefence += equip.getDslash();
        }
    }

    private TargetEquipmentTranslator.Result translateTarget(Player target)
    {
        PlayerComposition composition = target.getPlayerComposition();
        if (composition == null)
        {
            return null;
        }
        return TargetEquipmentTranslator.translate(composition, itemManager, target.getId());
    }

    private void updateTargetCurrentStats(TargetEquipmentTranslator.Result translation)
    {
        double[] bonuses = translation.getBonuses();
        targetCurrentGearRangedDefence = bonuses[9];
        targetCurrentGearMageDefence = bonuses[8];
        targetCurrentGearMeleeDefence = bonuses[6];
    }

    private void updateTargetLastStats(CombatType style, TargetEquipmentTranslator.Result translation)
    {
        if (style == null)
        {
            return;
        }

        double[] bonuses = translation.getBonuses();
        switch (style)
        {
            case MAGIC:
                targetLastMagicGearAccuracy = bonuses[3];
                targetLastMagicGearStrength = bonuses[12];
                targetLastMagicGearRangedDefence = bonuses[9];
                targetLastMagicGearMageDefence = bonuses[8];
                targetLastMagicGearMeleeDefence = bonuses[6];
                break;
            case RANGED:
                targetLastRangedGearAccuracy = bonuses[4];
                targetLastRangedGearStrength = bonuses[11];
                targetLastRangedGearRangedDefence = bonuses[9];
                targetLastRangedGearMageDefence = bonuses[8];
                targetLastRangedGearMeleeDefence = bonuses[6];
                break;
            case MELEE:
            default:
                targetLastMeleeGearAccuracy = bonuses[1];
                targetLastMeleeGearStrength = bonuses[10];
                targetLastMeleeGearRangedDefence = bonuses[9];
                targetLastMeleeGearMageDefence = bonuses[8];
                targetLastMeleeGearMeleeDefence = bonuses[6];
                break;
        }
    }

    private CombatType determineWeaponType(ItemEquipmentStats weaponStats, String weaponName)
    {
        if (weaponStats == null)
        {
            return CombatType.MELEE;
        }

        if (weaponStats.getAmagic() > 0 || weaponStats.getMdmg() > 0)
        {
            return CombatType.MAGIC;
        }

        if (weaponStats.getArange() > weaponStats.getAslash())
        {
            return CombatType.RANGED;
        }

        if (weaponName != null && weaponName.contains("crossbow"))
        {
            return CombatType.RANGED;
        }

        return CombatType.MELEE;
    }

    private double lookupRangedAttackRange(String weaponName)
    {
        if (weaponName == null)
        {
            return DEFAULT_RANGED_ATTACK_RANGE;
        }

        if (weaponName.contains("ballista"))
        {
            return 10.0;
        }
        if (weaponName.contains("crossbow"))
        {
            return 8.0;
        }
        if (weaponName.contains("bow"))
        {
            return 8.0;
        }
        if (weaponName.contains("blowpipe"))
        {
            return 7.0;
        }
        if (weaponName.contains("javelin"))
        {
            return 6.0;
        }
        if (weaponName.contains("knife") || weaponName.contains("throwing"))
        {
            return 5.0;
        }
        return DEFAULT_RANGED_ATTACK_RANGE;
    }

    private boolean hasNameContaining(String... patterns)
    {
        if (currentItemNames.isEmpty())
        {
            return false;
        }

        for (String name : currentItemNames)
        {
            for (String pattern : patterns)
            {
                if (name.contains(pattern))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNameEqual(String target)
    {
        String needle = target.toLowerCase(Locale.ROOT);
        return currentItemNames.contains(needle);
    }



    // ==== Player observation getters ====

    public double getIsEnchantedDragonBolts()
    {
        return enchantedDragonBolts ? 1.0 : 0.0;
    }

    public double getIsEnchantedOpalBolts()
    {
        return enchantedOpalBolts ? 1.0 : 0.0;
    }

    public double getIsEnchantedDiamondBolts()
    {
        return enchantedDiamondBolts ? 1.0 : 0.0;
    }

    public double getIsMageSpecWeapon()
    {
        return mageSpecWeapon ? 1.0 : 0.0;
    }

    public double getIsRangeSpecWeapon()
    {
        return rangeSpecWeapon ? 1.0 : 0.0;
    }

    public double getIsNightmareStaff()
    {
        return nightmareStaff ? 1.0 : 0.0;
    }

    public double getIsZaryteCrossbow()
    {
        return zaryteCrossbow ? 1.0 : 0.0;
    }

    public double getIsBallista()
    {
        return ballista ? 1.0 : 0.0;
    }

    public double getIsMorrigansJavelins()
    {
        return morrigansJavelins ? 1.0 : 0.0;
    }

    public double getIsDragonKnives()
    {
        return dragonKnives ? 1.0 : 0.0;
    }

    public double getIsDarkBow()
    {
        return darkBow ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecDclaws()
    {
        return meleeSpecDclaws ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecDds()
    {
        return meleeSpecDds ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecAgs()
    {
        return meleeSpecAgs ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecVls()
    {
        return meleeSpecVls ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecStatHammer()
    {
        return meleeSpecStatHammer ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecAncientGodsword()
    {
        return meleeSpecAncientGodsword ? 1.0 : 0.0;
    }

    public double getIsMeleeSpecGraniteMaul()
    {
        return meleeSpecGraniteMaul ? 1.0 : 0.0;
    }

    public double getIsBloodFury()
    {
        return bloodFury ? 1.0 : 0.0;
    }

    public double getIsDharoksSet()
    {
        return dharoksSet ? 1.0 : 0.0;
    }

    public double getIsZurielStaff()
    {
        return zurielStaff ? 1.0 : 0.0;
    }

    public double getMagicGearAccuracy()
    {
        return magicGearAccuracy;
    }

    public double getMagicGearStrength()
    {
        return magicGearStrength;
    }

    public double getRangedGearAccuracy()
    {
        return rangedGearAccuracy;
    }

    public double getRangedGearStrength()
    {
        return rangedGearStrength;
    }

    public double getRangedGearAttackSpeed()
    {
        return rangedGearAttackSpeed;
    }

    public double getRangedGearAttackRange()
    {
        return rangedGearAttackRange;
    }

    public double getMeleeGearAccuracy()
    {
        return meleeGearAccuracy;
    }

    public double getMeleeGearStrength()
    {
        return meleeGearStrength;
    }

    public double getMeleeGearAttackSpeed()
    {
        return meleeGearAttackSpeed;
    }

    public double getMagicGearRangedDefence()
    {
        return magicGearRangedDefence;
    }

    public double getMagicGearMageDefence()
    {
        return magicGearMageDefence;
    }

    public double getMagicGearMeleeDefence()
    {
        return magicGearMeleeDefence;
    }

    public double getRangedGearRangedDefence()
    {
        return rangedGearRangedDefence;
    }

    public double getRangedGearMageDefence()
    {
        return rangedGearMageDefence;
    }

    public double getRangedGearMeleeDefence()
    {
        return rangedGearMeleeDefence;
    }

    public double getMeleeGearRangedDefence()
    {
        return meleeGearRangedDefence;
    }

    public double getMeleeGearMageDefence()
    {
        return meleeGearMageDefence;
    }

    public double getMeleeGearMeleeDefence()
    {
        return meleeGearMeleeDefence;
    }

    public double getTargetCurrentGearRangedDefence()
    {
        return targetCurrentGearRangedDefence;
    }

    public double getTargetCurrentGearMageDefence()
    {
        return targetCurrentGearMageDefence;
    }

    public double getTargetCurrentGearMeleeDefence()
    {
        return targetCurrentGearMeleeDefence;
    }

    public double getTargetLastMagicGearAccuracy()
    {
        return targetLastMagicGearAccuracy;
    }

    public double getTargetLastMagicGearStrength()
    {
        return targetLastMagicGearStrength;
    }

    public double getTargetLastRangedGearAccuracy()
    {
        return targetLastRangedGearAccuracy;
    }

    public double getTargetLastRangedGearStrength()
    {
        return targetLastRangedGearStrength;
    }

    public double getTargetLastMeleeGearAccuracy()
    {
        return targetLastMeleeGearAccuracy;
    }

    public double getTargetLastMeleeGearStrength()
    {
        return targetLastMeleeGearStrength;
    }

    public double getTargetLastMagicGearRangedDefence()
    {
        return targetLastMagicGearRangedDefence;
    }

    public double getTargetLastMagicGearMageDefence()
    {
        return targetLastMagicGearMageDefence;
    }

    public double getTargetLastMagicGearMeleeDefence()
    {
        return targetLastMagicGearMeleeDefence;
    }

    public double getTargetLastRangedGearRangedDefence()
    {
        return targetLastRangedGearRangedDefence;
    }

    public double getTargetLastRangedGearMageDefence()
    {
        return targetLastRangedGearMageDefence;
    }

    public double getTargetLastRangedGearMeleeDefence()
    {
        return targetLastRangedGearMeleeDefence;
    }

    public double getTargetLastMeleeGearRangedDefence()
    {
        return targetLastMeleeGearRangedDefence;
    }

    public double getTargetLastMeleeGearMageDefence()
    {
        return targetLastMeleeGearMageDefence;
    }

    public double getTargetLastMeleeGearMeleeDefence()
    {
        return targetLastMeleeGearMeleeDefence;
    }
}