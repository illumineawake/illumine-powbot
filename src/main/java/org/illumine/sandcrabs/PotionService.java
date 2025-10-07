package org.illumine.sandcrabs;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.rt4.Bank;
import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Item;
import org.powbot.api.rt4.Skills;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Handles detection, usage, and restocking of combat potions for Sand Crabs.
 */
public class PotionService {

    public enum PotionType {
        ATTACK,
        STRENGTH,
        RANGING,
        SUPER_COMBAT
    }

    public static final class PotionSpec {
        final PotionType type;
        final String baseName; // e.g. "Super attack", "Super combat potion", "Ranging potion"
        final int startingCount; // ignoring doses
        int targetBoost; // 1..4, dynamic per type
        boolean bankOutOfStock;

        PotionSpec(PotionType type, String baseName, int startingCount, int targetBoost) {
            this.type = type;
            this.baseName = baseName;
            this.startingCount = startingCount;
            this.targetBoost = targetBoost;
            this.bankOutOfStock = false;
        }
    }

    private final Logger logger;
    private final LevellingService levelling;
    private final Map<PotionType, PotionSpec> profile = new EnumMap<>(PotionType.class);

    public PotionService(Logger logger, LevellingService levelling) {
        this.logger = logger;
        this.levelling = levelling;
    }

    public Map<PotionType, PotionSpec> getProfile() {
        return profile;
    }

    public void initFromInventory() {
        profile.clear();

        // Helper to record a potion spec if found in inventory
        recordIfPresent(PotionType.SUPER_COMBAT, "super combat");
        recordIfPresent(PotionType.RANGING, "ranging");

        // Only record Attack/Strength if Super combat was not detected
        boolean hasSuperCombat = profile.containsKey(PotionType.SUPER_COMBAT);
        if (!hasSuperCombat) {
            recordIfPresent(PotionType.ATTACK, "attack");
            recordIfPresent(PotionType.STRENGTH, "strength");
        }

        if (logger != null) {
            logger.info("Potion profile detected: " + profile.keySet());
        }
    }

    private void recordIfPresent(PotionType type, String key) {
        Item any = Inventory.stream().nameContains(key).action("Drink").first();
        if (!any.valid()) {
            return;
        }
        String base = stripDose(any.name());
        int count = (int) Inventory.stream().nameContains(base).action("Drink").count();
        int boost = Random.nextInt(1, 5);
        profile.put(type, new PotionSpec(type, base, count, boost));
    }

    public boolean shouldUsePotions() {
        return !profile.isEmpty();
    }

    /**
     * Return true only when a drink is currently needed (by threshold logic)
     * and the matching potion is not present in inventory. This supports
     * on-demand banking to avoid wasting active boosts.
     */
    public boolean shouldRestockNow() {
        if (profile.isEmpty()) {
            return false;
        }

        boolean meleeStyle = isMeleeStyle();
        if (meleeStyle) {
            PotionSpec superCombat = profile.get(PotionType.SUPER_COMBAT);
            if (superCombat != null) {
                boolean needDrink = needsDrink(Skill.Attack, superCombat.targetBoost)
                        || needsDrink(Skill.Strength, superCombat.targetBoost);
                return needDrink && inventoryCount(superCombat.baseName) == 0;
            }

            PotionSpec attack = profile.get(PotionType.ATTACK);
            PotionSpec strength = profile.get(PotionType.STRENGTH);
            boolean needAttack = attack != null
                    && needsDrink(Skill.Attack, attack.targetBoost)
                    && inventoryCount(attack.baseName) == 0;
            boolean needStrength = strength != null
                    && needsDrink(Skill.Strength, strength.targetBoost)
                    && inventoryCount(strength.baseName) == 0;
            return needAttack || needStrength;
        }

        PotionSpec ranging = profile.get(PotionType.RANGING);
        if (ranging != null) {
            boolean needDrink = needsDrink(Skill.Ranged, ranging.targetBoost);
            return needDrink && inventoryCount(ranging.baseName) == 0;
        }
        return false;
    }

    public boolean isOutOfStock() {
        for (PotionSpec spec : profile.values()) {
            if (inventoryCount(spec.baseName) == 0 && spec.bankOutOfStock) {
                return true;
            }
        }
        return false;
    }

    public void restockAtBank() {
        for (PotionSpec spec : profile.values()) {
            int current = inventoryCount(spec.baseName);
            if (current >= spec.startingCount) {
                continue;
            }
            if (current == 0 && spec.bankOutOfStock) {
                continue;
            }

            int needed = Math.max(0, spec.startingCount - current);
            if (needed == 0) continue;

            int before = current;
            needed = withdrawByDosePreference(spec.baseName, needed);

            // If we still need items and none are found, mark as out-of-stock in bank
            if (inventoryCount(spec.baseName) == before) {
                boolean anyInBank = false;
                for (int d = 4; d >= 1; d--) {
                    String n = spec.baseName + "(" + d + ")";
                    if (Bank.stream().name(n).first().valid()) {
                        anyInBank = true;
                        break;
                    }
                }
                if (!anyInBank) {
                    spec.bankOutOfStock = true;
                    if (logger != null) {
                        logger.info("Out of stock in bank for: " + spec.baseName);
                    }
                }
            }
        }
    }

    public void drinkIfNeeded() {
        if (!shouldUsePotions()) {
            return;
        }
        if (Bank.opened()) {
            return;
        }

        boolean meleeStyle = isMeleeStyle();

        if (meleeStyle) {
            // Prefer super combat if present
            PotionSpec superCombat = profile.get(PotionType.SUPER_COMBAT);
            if (superCombat != null) {
                // Check either Attack or Strength threshold to justify a drink
                if (needsDrink(Skill.Attack, superCombat.targetBoost) || needsDrink(Skill.Strength, superCombat.targetBoost)) {
                    drinkByBaseName(superCombat);
                }
                return;
            }

            PotionSpec attack = profile.get(PotionType.ATTACK);
            if (attack != null && needsDrink(Skill.Attack, attack.targetBoost)) {
                drinkByBaseName(attack);
                return;
            }
            PotionSpec strength = profile.get(PotionType.STRENGTH);
            if (strength != null && needsDrink(Skill.Strength, strength.targetBoost)) {
                drinkByBaseName(strength);
            }
            return;
        }

        // Non-melee: use ranging if configured
        PotionSpec ranging = profile.get(PotionType.RANGING);
        if (ranging != null && Skills.level(Skill.Hitpoints) >= 15) {
            if (needsDrink(Skill.Ranged, ranging.targetBoost)) {
                drinkByBaseName(ranging);
            }
        }
    }

    private boolean isMeleeStyle() {
        try {
            Skill mapped = levelling.mapStyleToSkill(Combat.style());
            return mapped == Skill.Attack || mapped == Skill.Strength || mapped == Skill.Defence;
        } catch (Exception ignored) {
            return true; // default to melee to be conservative
        }
    }

    private boolean needsDrink(Skill skill, int targetBoost) {
        try {
            int base = Skills.realLevel(skill);
            int current = Skills.level(skill);
            return current <= base + targetBoost;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drinkByBaseName(PotionSpec spec) {
        Item potion = Inventory.stream().nameContains(spec.baseName).action("Drink").first();
        if (!potion.valid()) {
            return;
        }
        int before = inventoryCount(spec.baseName);
        if (potion.interact("Drink")) {
            Condition.wait(() -> inventoryCount(spec.baseName) < before, 100, 10);
            spec.targetBoost = Random.nextInt(1, 5);
        }
    }

    private int withdrawByDosePreference(String baseName, int needed) {
        int remaining = needed;
        for (int d = 4; d >= 1 && remaining > 0; d--) {
            String exact = baseName + "(" + d + ")";
            int inBank = (int) Bank.stream().name(exact).count();
            if (inBank <= 0) continue;
            int toWithdraw = Math.min(remaining, inBank);
            if (toWithdraw <= 0) continue;
            int before = (int) Inventory.stream().name(exact).count();
            Bank.withdraw(exact, toWithdraw);
            Condition.wait(() -> (int) Inventory.stream().name(exact).count() > before, 200, 20);
            remaining = Math.max(0, needed - totalInventoryCountForBase(baseName));
        }
        return remaining;
    }

    private int inventoryCount(String baseName) {
        return totalInventoryCountForBase(baseName);
    }

    private int totalInventoryCountForBase(String baseName) {
        return (int) Inventory.stream().nameContains(baseName).action("Drink").count();
    }

    private String stripDose(String name) {
        if (name == null) return "";
        return name.replaceAll("\\(\\d+\\)$", "").trim();
    }
}
