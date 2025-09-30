package org.illumine.sandcrabs;

import org.illumine.sandcrabs.tasks.*;
import org.illumine.taskscript.Task;
import org.illumine.taskscript.TaskScript;
import org.powbot.api.Area;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.*;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@ScriptConfiguration(name = "Use Food", description = "Enable eating logic", optionType = OptionType.BOOLEAN)
@ScriptConfiguration(name = "Food Name", description = "Food to consume", optionType = OptionType.STRING, defaultValue = "Lobster", visible = false)
@ScriptConfiguration(name = "Eat Min %", description = "Minimum HP percent for eating threshold", optionType = OptionType.INTEGER, defaultValue = "40")
@ScriptConfiguration(name = "Eat Max %", description = "Maximum HP percent for eating threshold", optionType = OptionType.INTEGER, defaultValue = "75")
// Levelling controls
@ScriptConfiguration(name = "Configure Levelling", description = "Enable skill levelling goals and switching", optionType = OptionType.BOOLEAN, defaultValue = "false")
@ScriptConfiguration(name = "Levelling Mode", description = "Select levelling mode", optionType = OptionType.STRING, defaultValue = "On Limit", allowedValues = {"Within Range", "On Limit"}, visible = false)
@ScriptConfiguration(name = "Max Attack", description = "Max Attack level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Max Strength", description = "Max Strength level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Max Defence", description = "Max Defence level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Keep Within Levels", description = "Keep skills within X levels of each other", optionType = OptionType.INTEGER, defaultValue = "5", visible = false)
@ScriptManifest(name = "Sand Crabs Task", description = "Task-based Sand Crabs script", author = "illumine", category = ScriptCategory.Combat, version = "0.2.0")
public class SandCrabsScript extends TaskScript {

    public static final List<Tile> SPOT_TILES = java.util.List.of(
            new Tile(1790, 3468, 0),
            new Tile(1776, 3468, 0),
            new Tile(1773, 3461, 0),
            new Tile(1765, 3468, 0),
            new Tile(1749, 3469, 0),
            new Tile(1738, 3468, 0)
    );

    public static final Area RESET_AREA = new Area(new Tile(1741, 3501, 0), new Tile(1745, 3498, 0));
    public static final Tile SHORE_BANK_TILE = new Tile(1720, 3465, 0);

    private static final int DEFAULT_EAT_MIN_PERCENT = 40;
    private static final int DEFAULT_EAT_MAX_PERCENT = 75;
    private static final int CLAMP_MIN_EAT_PERCENT = 1;
    private static final int CLAMP_MAX_EAT_PERCENT = 100;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 99;
    private static final int MIN_NO_COMBAT_SECONDS = 8;
    private static final int MAX_NO_COMBAT_SECONDS = 12;
    private static final long WORLD_HOP_COOLDOWN_MS = 10_000L;
    private static final long DORMANT_WARNING_DELAY_MS = 5 * 60 * 1000L;

    private static final Skill[] TRACKED_COMBAT_SKILLS = new Skill[]{
            Skill.Attack,
            Skill.Strength,
            Skill.Defence,
            Skill.Ranged,
            Skill.Magic,
            Skill.Hitpoints
    };

    private boolean useFood;
    private String configuredFoodName = "Lobster";
    private int eatMinPercent = DEFAULT_EAT_MIN_PERCENT;
    private int eatMaxPercent = DEFAULT_EAT_MAX_PERCENT;
    private int currentEatThresholdPercent = DEFAULT_EAT_MAX_PERCENT;
    private long currentNoCombatThresholdMillis = MIN_NO_COMBAT_SECONDS * 1000;
    private Tile currentCampTile;
    private long lastWorldHopMillis = 0;
    private long lastDormantSeenTime = System.currentTimeMillis();
    private boolean dormantWarningShown = false;
    // Camp crash detection (avoid reacting to passersby)
    private static final long CAMP_CRASH_THRESHOLD_MS = 10_000L;
    private final java.util.Map<String, Long> campCrashFirstSeen = new java.util.HashMap<>();

    // Levelling config/state
    public static final String MODE_WITHIN_RANGE = "Within Range";
    public static final String MODE_ON_LIMIT = "On Limit";
    private boolean levellingEnabled = false;
    private String levellingMode = MODE_ON_LIMIT;
    private int maxAttack = MAX_LEVEL;
    private int maxStrength = MAX_LEVEL;
    private int maxDefence = MAX_LEVEL;
    private int keepWithin = 5;
    private Skill initialLockedSkill = null; // Applies in Mode: On Limit only
    private Skill currentTrainingSkill = null; // For overlay display

    @Override
    public void onStart() {
        readAndValidateConfiguration();
        readAndValidateLevellingConfiguration();
        rollNextEatThreshold();
        rollNextNoCombatThreshold();
        updateVisibility("Food Name", useFood);
        updateLevellingVisibility();
        // Capture initial style to lock starting skill for Mode: On Limit
        try {
            Combat.Style style = Combat.style();
            initialLockedSkill = mapStyleToSkill(style);
        } catch (Exception ignored) {
            initialLockedSkill = null;
        }
        super.onStart();
        initPaint();
    }

    @Override
    protected List<Task> createTasks() {
        return addAll(
                new BankAndStopTask(this),
                new EatFoodTask(this),
                new ResetAggroTask(this),
                new ManageLevellingTask(this),
                new TravelToCampTask(this),
                new AttackTask(this)
        );
    }

    public static void main(String[] args) {
        // Ensure exactly one device is connected via ADB
        new SandCrabsScript().startScript();
    }

    public List<Tile> getCrabTiles() {
        return SPOT_TILES;
    }

    public Area getResetArea() {
        return RESET_AREA;
    }

    public Tile getShoreBankTile() {
        return SHORE_BANK_TILE;
    }

    public boolean isUseFoodEnabled() {
        return useFood;
    }

    public String getConfiguredFoodName() {
        return configuredFoodName;
    }

    public boolean isConfiguredFood(String itemName) {
        return useFood && !configuredFoodName.isEmpty() && itemName != null
                && itemName.equalsIgnoreCase(configuredFoodName);
    }

    public int getEatMinPercent() {
        return eatMinPercent;
    }

    public int getEatMaxPercent() {
        return eatMaxPercent;
    }

    public int getCurrentEatThresholdPercent() {
        return currentEatThresholdPercent;
    }

    public Tile getCurrentCampTile() {
        return currentCampTile;
    }

    public void setCurrentCampTile(Tile currentCampTile) {
        this.currentCampTile = currentCampTile;
    }

    public void resetCampSelection() {
        this.currentCampTile = null;
    }

    public void rollNextEatThreshold() {
        currentEatThresholdPercent = Random.nextInt(eatMinPercent, eatMaxPercent + 1);
    }

    public long getCurrentNoCombatThresholdMillis() {
        return currentNoCombatThresholdMillis;
    }

    public void rollNextNoCombatThreshold() {
        int seconds = Random.nextInt(MIN_NO_COMBAT_SECONDS, MAX_NO_COMBAT_SECONDS + 1);
        currentNoCombatThresholdMillis = seconds * 1000L;
    }

    public long minTrackedSkillExpDelta() {
        long min = Long.MAX_VALUE;
        for (Skill skill : TRACKED_COMBAT_SKILLS) {
            long delta = Skills.timeSinceExpGained(skill);
            if (delta < min) {
                min = delta;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    public boolean hasRequiredFoodInInventory() {
        if (!useFood || configuredFoodName.isEmpty()) {
            return false;
        }
        Item food = Inventory.stream()
                .filtered(item -> item != null && item.valid() && item.name() != null
                        && item.name().equalsIgnoreCase(configuredFoodName))
                .first();
        return food != null && food.valid();
    }

    public boolean isDormantCrabNearby() {
        Player local = Players.local();
        if (local == null || !local.valid()) {
            return false;
        }
        Npc dormant = Npcs.stream()
                .name("Sandy rocks")
                .within(local, 2)
                .first();
        if (dormant != null && dormant.valid()) {
            lastDormantSeenTime = System.currentTimeMillis();
            return true;
        }

        long sinceSeen = System.currentTimeMillis() - lastDormantSeenTime;
        if (!dormantWarningShown && sinceSeen >= DORMANT_WARNING_DELAY_MS) {
            dormantWarningShown = true;
            if (getLog() != null) {
                getLog().warning("No 'Sandy rocks' NPCs detected nearby for an extended period. Check naming or aggro state.");
            }
        }
        return false;
    }

    public boolean isCampTileOccupied(Tile camp) {
        Player local = Players.local();
        if (camp == null) {
            return true;
        }
        return Players.stream()
                .within(camp, 2)
                .filtered(player -> !player.equals(local))
                .first()
                .valid();
    }

    /**
     * Returns true only if a non-local player has been continuously within the
     * camp radius for at least CAMP_CRASH_THRESHOLD_MS.
     */
    public boolean isCampTileCrashed(Tile camp) {
        if (camp == null) return true;
        Player local = Players.local();
        boolean someoneNearby = Players.stream()
                .within(camp, 2)
                .filtered(p -> !p.equals(local))
                .first()
                .valid();

        String key = String.valueOf(camp);
        long now = System.currentTimeMillis();

        if (!someoneNearby) {
            campCrashFirstSeen.remove(key);
            return false;
        }

        Long firstSeen = campCrashFirstSeen.get(key);
        if (firstSeen == null) {
            campCrashFirstSeen.put(key, now);
            return false;
        }
        return now - firstSeen >= CAMP_CRASH_THRESHOLD_MS;
    }

    public List<Tile> eligibleCampTiles() {
        List<Tile> eligible = new ArrayList<>();
        for (Tile camp : SPOT_TILES) {
            if (!isCampTileOccupied(camp)) {
                eligible.add(camp);
            }
        }
        return eligible;
    }

    public boolean hopToNextWorld() {
        // Do not hop while in combat; wait until out of combat first
        if (isInCombat()) {
            if (getLog() != null) {
                getLog().info("In combat; waiting to hop worlds...");
            }
            // Wait up to ~30 seconds for combat to end
            boolean out = Condition.wait(() -> !isInCombat(), 200, 150);
            if (!out) {
                return false;
            }
        }

        // Pair with sandcrabs no-combat timing: ensure we've been out of combat long enough
        long requiredNoCombatMs = getCurrentNoCombatThresholdMillis();
        long cap = Math.max(4000, Math.min(20000, requiredNoCombatMs));
        if (minTrackedSkillExpDelta() < cap) {
            if (getLog() != null) {
                getLog().info("Waiting " + cap + "ms of no combat before hopping...");
            }
            int attempts = (int) Math.ceil(cap / 200.0) + 5;
            boolean waited = Condition.wait(() -> minTrackedSkillExpDelta() >= cap, 200, attempts);
            if (!waited) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastWorldHopMillis < WORLD_HOP_COOLDOWN_MS) {
            return false;
        }
        lastWorldHopMillis = now;

        List<World> candidates = new ArrayList<>(Worlds.stream()
                .filtered(World::valid)
                .filtered(world -> {
                    World.Type type = world.type();
                    return type == World.Type.MEMBERS;
                })
                .filtered(world -> {
                    World.Specialty specialty = world.specialty();
                    return specialty != World.Specialty.PVP && specialty != World.Specialty.HIGH_RISK;
                })
                .list());

        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort(Comparator.comparingInt(World::getPopulation));
        int poolSize = Math.min(10, candidates.size());
        int index = Random.nextInt(0, poolSize);
        World target = candidates.get(index);
        if (target == null || !target.valid()) {
            return false;
        }

        boolean hopped = target.hop();
        if (!hopped) {
            return false;
        }

        Condition.wait(() -> !Game.loggedIn(), 200, 25);
        Condition.wait(Game::loggedIn, 200, 50);
        resetCampSelection();
        return true;
    }

    public boolean isInCombat() {
        Player p = Players.local();
        if (p == null || !p.valid()) {
            return false;
        }
        try {
            return (p.interacting().valid()) || p.healthBarVisible();
        } catch (Exception ignored) {
            return (p.interacting().valid());
        }
    }

    public boolean canAttemptWorldHop() {
        return System.currentTimeMillis() - lastWorldHopMillis >= WORLD_HOP_COOLDOWN_MS;
    }

    public long getWorldHopCooldownMillis() {
        return WORLD_HOP_COOLDOWN_MS;
    }

    public long getLastWorldHopMillis() {
        return lastWorldHopMillis;
    }

    private void initPaint() {
        Paint paint = PaintBuilder.newBuilder()
                .x(15)
                .y(40)
                .trackSkill(Skill.Attack)
                .trackSkill(Skill.Strength)
                .trackSkill(Skill.Defence)
                .trackSkill(Skill.Ranged)
                .trackSkill(Skill.Magic)
                .trackSkill(Skill.Hitpoints)
                .addString("Status", this::getCurrentStatus)
                .addString("Training", this::getTrainingStatus)
                .build();
        addPaint(paint);
    }

    private void readAndValidateConfiguration() {
        useFood = Boolean.TRUE.equals(getOption("Use Food"));

        Object minValue = getOption("Eat Min %");
        Object maxValue = getOption("Eat Max %");
        eatMinPercent = clampToBounds(asInt(minValue, DEFAULT_EAT_MIN_PERCENT));
        eatMaxPercent = clampToBounds(asInt(maxValue, DEFAULT_EAT_MAX_PERCENT));
        if (eatMinPercent > eatMaxPercent) {
            int temp = eatMinPercent;
            eatMinPercent = eatMaxPercent;
            eatMaxPercent = temp;
        }

        if (useFood) {
            Object foodValue = getOption("Food Name");
            String name = foodValue instanceof String ? (String) foodValue : "Lobster";
            name = name.trim();
            if (name.isEmpty()) {
                // Pre-populate default if the field is blank
                name = "Lobster";
            }
            configuredFoodName = name;
        } else {
            configuredFoodName = "";
        }
    }

    private void readAndValidateLevellingConfiguration() {
        levellingEnabled = Boolean.TRUE.equals(getOption("Configure Levelling"));
        Object modeVal = getOption("Levelling Mode");
        String mode = (modeVal instanceof String) ? ((String) modeVal).trim() : MODE_ON_LIMIT;
        if (!MODE_WITHIN_RANGE.equalsIgnoreCase(mode) && !MODE_ON_LIMIT.equalsIgnoreCase(mode)) {
            mode = MODE_ON_LIMIT;
        }
        // Normalize to canonical labels
        levellingMode = MODE_WITHIN_RANGE.equalsIgnoreCase(mode) ? MODE_WITHIN_RANGE : MODE_ON_LIMIT;

        maxAttack = clampLevel(asInt(getOption("Max Attack"), MAX_LEVEL));
        maxStrength = clampLevel(asInt(getOption("Max Strength"), MAX_LEVEL));
        maxDefence = clampLevel(asInt(getOption("Max Defence"), MAX_LEVEL));
        keepWithin = Math.max(1, Math.min(20, asInt(getOption("Keep Within Levels"), 5)));
    }

    private int clampToBounds(int value) {
        if (value < CLAMP_MIN_EAT_PERCENT) {
            return CLAMP_MIN_EAT_PERCENT;
        }
        if (value > CLAMP_MAX_EAT_PERCENT) {
            return CLAMP_MAX_EAT_PERCENT;
        }
        return value;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int clampLevel(int value) {
        if (value < MIN_LEVEL) return MIN_LEVEL;
        if (value > MAX_LEVEL) return MAX_LEVEL;
        return value;
    }

    @ValueChanged(keyName = "Use Food")
    public void onUseFoodChanged(Boolean enabled) {
        updateVisibility("Food Name", Boolean.TRUE.equals(enabled));
    }

    @ValueChanged(keyName = "Configure Levelling")
    public void onConfigureLevellingChanged(Boolean enabled) {
        levellingEnabled = Boolean.TRUE.equals(enabled);
        updateLevellingVisibility();
    }

    @ValueChanged(keyName = "Levelling Mode")
    public void onLevellingModeChanged(String newMode) {
        // Normalize and update visibility
        String mode = newMode == null ? MODE_ON_LIMIT : newMode.trim();
        levellingMode = MODE_WITHIN_RANGE.equalsIgnoreCase(mode) ? MODE_WITHIN_RANGE : MODE_ON_LIMIT;
        updateLevellingVisibility();
    }

    private void updateLevellingVisibility() {
        boolean show = levellingEnabled;
        updateVisibility("Levelling Mode", show);
        updateVisibility("Max Attack", show);
        updateVisibility("Max Strength", show);
        updateVisibility("Max Defence", show);
        boolean showWithin = show && MODE_WITHIN_RANGE.equals(levellingMode);
        updateVisibility("Keep Within Levels", showWithin);
    }

    public boolean isLevellingEnabled() {
        return levellingEnabled;
    }

    public String getLevellingMode() {
        return levellingMode;
    }

    public int getMaxFor(Skill skill) {
        if (skill == Skill.Attack) return maxAttack;
        if (skill == Skill.Strength) return maxStrength;
        if (skill == Skill.Defence) return maxDefence;
        return MAX_LEVEL;
    }

    public int realLevel(Skill skill) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, Skills.realLevel(skill)));
    }

    public boolean reachedLimit(Skill skill) {
        return realLevel(skill) >= getMaxFor(skill);
    }

    public boolean allGoalsReached() {
        return reachedLimit(Skill.Attack) && reachedLimit(Skill.Strength) && reachedLimit(Skill.Defence);
    }

    public Skill getInitialLockedSkill() {
        return initialLockedSkill;
    }

    public int getKeepWithin() {
        return keepWithin;
    }

    public void setCurrentTrainingSkill(Skill skill) {
        currentTrainingSkill = skill;
    }

    public Skill getCurrentTrainingSkill() {
        return currentTrainingSkill;
    }

    public String getTrainingStatus() {
        if (!levellingEnabled) return "Off";
        if (allGoalsReached()) return "All goals reached";
        Skill s = currentTrainingSkill;
        if (s == null) return "Training Pending";
        String name = (s == Skill.Attack ? "Attack" : s == Skill.Strength ? "Strength" : s == Skill.Defence ? "Defence" : s.name());
        int targetLevel = nextTargetLevel(s);
        if (targetLevel <= 0) return "Training " + name;
        return "Training " + name + " to " + targetLevel;
    }

    public Combat.Style styleFor(Skill skill) {
        if (skill == Skill.Attack) return Combat.Style.ACCURATE;
        if (skill == Skill.Strength) return Combat.Style.AGGRESSIVE;
        if (skill == Skill.Defence) return Combat.Style.DEFENSIVE;
        return Combat.Style.CONTROLLED;
    }

    public Skill mapStyleToSkill(Combat.Style style) {
        if (style == null) return null;
        switch (style) {
            case ACCURATE: return Skill.Attack;
            case AGGRESSIVE: return Skill.Strength;
            case DEFENSIVE: return Skill.Defence;
            default: return null; // CONTROLLED or unknown -> no lock
        }
    }

    public int nextTargetLevel(Skill skill) {
        if (!levellingEnabled || skill == null) return 0;
        if (allGoalsReached()) return realLevel(skill);
        if (MODE_ON_LIMIT.equals(levellingMode)) {
            return getMaxFor(skill);
        }

        // MODE_WITHIN_RANGE
        int current = realLevel(skill);
        int ownLimit = getMaxFor(skill);

        // Collect other eligible skills (not at limit)
        java.util.List<Skill> others = new java.util.ArrayList<>();
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (s != skill && !reachedLimit(s)) {
                others.add(s);
            }
        }

        if (others.isEmpty()) {
            return ownLimit; // No other skills to balance against
        }

        int highestOther = 0;
        int minThreshold = Integer.MAX_VALUE;
        for (Skill s : others) {
            int lv = realLevel(s);
            if (lv > highestOther) highestOther = lv;
            int threshold = lv + getKeepWithin();
            if (threshold < minThreshold) minThreshold = threshold;
        }

        boolean isTop = current >= highestOther;
        if (isTop) {
            // Switch away when we reach the earliest threshold among others
            return Math.min(ownLimit, minThreshold);
        }
        // Catch-up target equals the highest other level (but not above our own max)
        return Math.min(ownLimit, highestOther);
    }

    private int highestEligibleLevel() {
        int max = 0;
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (!reachedLimit(s)) {
                int lv = realLevel(s);
                if (lv > max) max = lv;
            }
        }
        return max;
    }
}
