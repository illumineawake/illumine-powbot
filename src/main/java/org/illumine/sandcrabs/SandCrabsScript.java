package org.illumine.sandcrabs;

import org.illumine.sandcrabs.tasks.AttackTask;
import org.illumine.sandcrabs.tasks.BankAndStopTask;
import org.illumine.sandcrabs.tasks.EatFoodTask;
import org.illumine.sandcrabs.tasks.ManageLevellingTask;
import org.illumine.sandcrabs.tasks.ResetAggroTask;
import org.illumine.sandcrabs.tasks.SandCrabsTask;
import org.illumine.sandcrabs.tasks.TravelToSpotTask;
import org.illumine.taskscript.Task;
import org.illumine.taskscript.TaskScript;
import org.powbot.api.Area;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Combat;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.ValueChanged;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;

import java.util.List;

@ScriptConfiguration(name = "Use Food", description = "Enable eating", optionType = OptionType.BOOLEAN)
@ScriptConfiguration(name = "Food Name", description = "Food to consume", optionType = OptionType.STRING, defaultValue = "Lobster", visible = false)
@ScriptConfiguration(name = "Eat Min %", description = "Minimum HP percent for eating threshold", optionType = OptionType.INTEGER, defaultValue = "40")
@ScriptConfiguration(name = "Eat Max %", description = "Maximum HP percent for eating threshold", optionType = OptionType.INTEGER, defaultValue = "75")
// Levelling controls
@ScriptConfiguration(name = "Configure Levelling", description = "Enable skill levelling goals and switching (Melee only)", optionType = OptionType.BOOLEAN, defaultValue = "false")
@ScriptConfiguration(name = "Levelling Mode", description = "Switch combat skill to train when criteria is met - remains obedient to configured limits", optionType = OptionType.STRING, defaultValue = "When Limit Reached", allowedValues = {"Keep Within Range", "When Limit Reached", "Optimal"}, visible = false)
@ScriptConfiguration(name = "Max Attack", description = "Max Attack level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Max Strength", description = "Max Strength level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Max Defence", description = "Max Defence level to train to", optionType = OptionType.INTEGER, defaultValue = "99", visible = false)
@ScriptConfiguration(name = "Keep Within Levels", description = "Keep combat skills within X levels of each other", optionType = OptionType.INTEGER, defaultValue = "5", visible = false)
@ScriptManifest(name = "illu Sand Crabs", description = "Kills Sand Crabs at Southern Hosidious beach, with bank restocking and combat style switching", author = "illumine", category = ScriptCategory.Combat, version = "0.2.0")
public class SandCrabsScript extends TaskScript {

    public static final List<Tile> HOSIDIUS_SPOT_TILES = List.of(
            new Tile(1790, 3468, 0),
            new Tile(1776, 3468, 0),
            new Tile(1773, 3461, 0),
            new Tile(1765, 3468, 0),
            new Tile(1749, 3469, 0),
            new Tile(1738, 3468, 0)
    );

    public static final Area RESET_AREA = new Area(new Tile(1741, 3501, 0), new Tile(1745, 3498, 0));
    public static final Tile SHORE_BANK_TILE = new Tile(1720, 3465, 0);

    public static final int DEFAULT_EAT_MIN_PERCENT = 40;
    public static final int DEFAULT_EAT_MAX_PERCENT = 75;
    public static final int CLAMP_MIN_EAT_PERCENT = 1;
    public static final int CLAMP_MAX_EAT_PERCENT = 100;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 99;
    public static final int MIN_NO_COMBAT_SECONDS = 8;
    public static final int MAX_NO_COMBAT_SECONDS = 12;
    public static final long WORLD_HOP_COOLDOWN_MS = 10000;
    public static final long DORMANT_WARNING_DELAY_MS = 5 * 60 * 1000;
    public static final long SPOT_CRASH_THRESHOLD_MS = 10000;

    public static final String MODE_WITHIN_RANGE = "Keep Within Range";
    public static final String MODE_ON_LIMIT = "When Limit Reached";
    public static final String MODE_OPTIMAL = "Optimal";

    private SandCrabsConfig config;
    private SandCrabsState state;
    private CombatMonitor combatMonitor;
    private SpotManager spotManager;
    private LevellingService levellingService;
    private SandCrabsContext context;

    @Override
    public void onStart() {
        config = loadConfiguration();
        state = new SandCrabsState(config.getEatMaxPercent(), config.getMinNoCombatSeconds() * 1000L, System.currentTimeMillis());
        combatMonitor = new CombatMonitor(getLog(), config, state);
        spotManager = new SpotManager(getLog(), config, state, combatMonitor);
        levellingService = new LevellingService(config, state);
        context = new SandCrabsContext(config, state, combatMonitor, spotManager, levellingService);

        combatMonitor.rollNextEatThreshold();
        combatMonitor.rollNextNoCombatThreshold();
        updateVisibility("Food Name", config.isUseFood());
        updateLevellingVisibility();

        try {
            Combat.Style style = Combat.style();
            levellingService.setInitialLockedSkill(levellingService.mapStyleToSkill(style));
        } catch (Exception ignored) {
            levellingService.setInitialLockedSkill(null);
        }

        super.onStart();
        initPaint();
    }

    @Override
    protected List<Task> createTasks() {
        return addAll(
                new BankAndStopTask(this, context),
                new EatFoodTask(this, context),
                new ResetAggroTask(this, context),
                new ManageLevellingTask(this, context),
                new TravelToSpotTask(this, context),
                new AttackTask(this, context)
        );
    }

    public static void main(String[] args) {
        new SandCrabsScript().startScript();
    }

    public SandCrabsContext getContext() {
        return context;
    }

    private void initPaint() {
        PaintBuilder builder = PaintBuilder.newBuilder()
                .x(15)
                .y(40)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Attack)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Strength)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Defence)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Ranged)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Magic)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Hitpoints)
                .addString("Status", this::getCurrentStatus)
                .addString("Training", () -> levellingService.trainingStatus());
        if (MODE_OPTIMAL.equals(levellingService.getLevellingMode())) {
            builder = builder.addString("Optimal Target", () -> levellingService.optimalTargetStatus());
        }
        Paint paint = builder.build();
        addPaint(paint);
    }

    private void refreshPaint() {
        if (levellingService == null) {
            return;
        }
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        initPaint();
    }

    private SandCrabsConfig loadConfiguration() {
        boolean useFood = Boolean.TRUE.equals(getOption("Use Food"));

        Object minValue = getOption("Eat Min %");
        Object maxValue = getOption("Eat Max %");
        int eatMinPercent = clampToBounds(asInt(minValue, DEFAULT_EAT_MIN_PERCENT));
        int eatMaxPercent = clampToBounds(asInt(maxValue, DEFAULT_EAT_MAX_PERCENT));
        if (eatMinPercent > eatMaxPercent) {
            int temp = eatMinPercent;
            eatMinPercent = eatMaxPercent;
            eatMaxPercent = temp;
        }

        String foodName = "";
        if (useFood) {
            Object foodValue = getOption("Food Name");
            String name = foodValue instanceof String ? ((String) foodValue).trim() : "Lobster";
            if (name.isEmpty()) {
                name = "Lobster";
            }
            foodName = name;
        }

        boolean levellingEnabled = Boolean.TRUE.equals(getOption("Configure Levelling"));
        Object modeVal = getOption("Levelling Mode");
        String levellingMode = (modeVal instanceof String) ? ((String) modeVal).trim() : MODE_ON_LIMIT;
        if (!MODE_WITHIN_RANGE.equalsIgnoreCase(levellingMode)
                && !MODE_ON_LIMIT.equalsIgnoreCase(levellingMode)
                && !MODE_OPTIMAL.equalsIgnoreCase(levellingMode)) {
            levellingMode = MODE_ON_LIMIT;
        }
        if (MODE_WITHIN_RANGE.equalsIgnoreCase(levellingMode)) {
            levellingMode = MODE_WITHIN_RANGE;
        } else if (MODE_OPTIMAL.equalsIgnoreCase(levellingMode)) {
            levellingMode = MODE_OPTIMAL;
        } else {
            levellingMode = MODE_ON_LIMIT;
        }

        int maxAttack = clampLevel(asInt(getOption("Max Attack"), MAX_LEVEL));
        int maxStrength = clampLevel(asInt(getOption("Max Strength"), MAX_LEVEL));
        int maxDefence = clampLevel(asInt(getOption("Max Defence"), MAX_LEVEL));
        int keepWithin = Math.max(1, Math.min(20, asInt(getOption("Keep Within Levels"), 5)));

        return SandCrabsConfig.builder()
                .spotTiles(HOSIDIUS_SPOT_TILES)
                .resetArea(RESET_AREA)
                .bankTile(SHORE_BANK_TILE)
                .useFood(useFood)
                .foodName(foodName)
                .eatMinPercent(eatMinPercent)
                .eatMaxPercent(eatMaxPercent)
                .levellingEnabled(levellingEnabled)
                .levellingMode(levellingMode)
                .maxAttackLevel(maxAttack)
                .maxStrengthLevel(maxStrength)
                .maxDefenceLevel(maxDefence)
                .keepWithinLevels(keepWithin)
                .minNoCombatSeconds(MIN_NO_COMBAT_SECONDS)
                .maxNoCombatSeconds(MAX_NO_COMBAT_SECONDS)
                .worldHopCooldownMillis(WORLD_HOP_COOLDOWN_MS)
                .dormantWarningDelayMillis(DORMANT_WARNING_DELAY_MS)
                .spotCrashThresholdMillis(SPOT_CRASH_THRESHOLD_MS)
                .build();
    }

    private void rebuildConfiguration() {
        SandCrabsConfig newConfig = loadConfiguration();
        this.config = newConfig;
        if (context != null) {
            context.updateConfig(newConfig);
            normalizeStateAgainstConfig();
        }
    }

    private void normalizeStateAgainstConfig() {
        if (state == null || config == null || combatMonitor == null || levellingService == null) {
            return;
        }
        int threshold = state.getEatThresholdPercent();
        if (threshold < config.getEatMinPercent() || threshold > config.getEatMaxPercent()) {
            combatMonitor.rollNextEatThreshold();
        }
        long millis = state.getNoCombatThresholdMillis();
        long min = config.getMinNoCombatSeconds() * 1000L;
        long max = config.getMaxNoCombatSeconds() * 1000L;
        if (millis < min || millis > max) {
            combatMonitor.rollNextNoCombatThreshold();
        }
        if (!config.isLevellingEnabled()) {
            levellingService.setCurrentTrainingSkill(null);
        }
    }

    private void updateLevellingVisibility() {
        boolean show = config != null && config.isLevellingEnabled();
        updateVisibility("Levelling Mode", show);
        updateVisibility("Max Attack", show);
        updateVisibility("Max Strength", show);
        updateVisibility("Max Defence", show);
        boolean showWithin = show && config != null && MODE_WITHIN_RANGE.equals(config.getLevellingMode());
        updateVisibility("Keep Within Levels", showWithin);
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
        if (value < MIN_LEVEL) {
            return MIN_LEVEL;
        }
        if (value > MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return value;
    }

    @ValueChanged(keyName = "Use Food")
    public void onUseFoodChanged(Boolean enabled) {
        updateVisibility("Food Name", Boolean.TRUE.equals(enabled));
        rebuildConfiguration();
    }

    @ValueChanged(keyName = "Configure Levelling")
    public void onConfigureLevellingChanged(Boolean enabled) {
        rebuildConfiguration();
        updateLevellingVisibility();
    }

    @ValueChanged(keyName = "Levelling Mode")
    public void onLevellingModeChanged(String newMode) {
        rebuildConfiguration();
        updateLevellingVisibility();
        refreshPaint();
    }
}
