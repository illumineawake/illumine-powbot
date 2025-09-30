package org.illumine.sandcrabs;

import org.illumine.sandcrabs.tasks.AttackTask;
import org.illumine.sandcrabs.tasks.BankAndStopTask;
import org.illumine.sandcrabs.tasks.EatFoodTask;
import org.illumine.sandcrabs.tasks.ResetAggroTask;
import org.illumine.sandcrabs.tasks.TravelToCampTask;
import org.illumine.taskscript.Task;
import org.illumine.taskscript.TaskScript;
import org.powbot.api.Area;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Game;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Item;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Npc;
import org.powbot.api.rt4.Npcs;
import org.powbot.api.rt4.Player;
import org.powbot.api.rt4.Players;
import org.powbot.api.rt4.World;
import org.powbot.api.rt4.Worlds;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.rt4.Skills;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.ValueChanged;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

@ScriptConfiguration(name = "Use Food", description = "Enable eating logic", optionType = OptionType.BOOLEAN)
@ScriptConfiguration(name = "Food Name", description = "Food to consume", optionType = OptionType.STRING, visible = false)
@ScriptConfiguration(name = "Eat Min %", description = "Minimum HP percent for eating threshold", optionType = OptionType.INTEGER)
@ScriptConfiguration(name = "Eat Max %", description = "Maximum HP percent for eating threshold", optionType = OptionType.INTEGER)
@ScriptManifest(name = "Sand Crabs Task", description = "Task-based Sand Crabs script", author = "illumine", category = ScriptCategory.Combat, version = "0.1.0")
public class SandCrabsScript extends TaskScript {

    public static final Tile[] CAMP_TILES = {
            new Tile(1776, 3468, 0),
            new Tile(1773, 3461, 0),
            new Tile(1765, 3468, 0)
    };

    public static final Area RESET_AREA = new Area(new Tile(1741, 3501, 0), new Tile(1745, 3498, 0));
    public static final Tile SHORE_BANK_TILE = new Tile(1720, 3465, 0);

    private static final int MIN_ALLOWED_EAT_PERCENT = 40;
    private static final int MAX_ALLOWED_EAT_PERCENT = 60;
    private static final int MIN_NO_COMBAT_SECONDS = 8;
    private static final int MAX_NO_COMBAT_SECONDS = 12;
    private static final int MIN_RUN_THRESHOLD = 5;
    private static final int MAX_RUN_THRESHOLD = 15;
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
    private int eatMinPercent = MIN_ALLOWED_EAT_PERCENT;
    private int eatMaxPercent = MAX_ALLOWED_EAT_PERCENT;
    private int currentEatThresholdPercent = MAX_ALLOWED_EAT_PERCENT;
    private long currentNoCombatThresholdMillis = MIN_NO_COMBAT_SECONDS * 1000L;
    private int runEnergyThreshold = MAX_RUN_THRESHOLD;
    private Tile currentCampTile;
    private long lastWorldHopMillis = 0L;
    private long lastDormantSeenTime = System.currentTimeMillis();
    private boolean dormantWarningShown = false;

    @Override
    public void onStart() {
        readAndValidateConfiguration();
        rollNextEatThreshold();
        rollNextNoCombatThreshold();
        rollNextRunThreshold();
        updateVisibility("Food Name", useFood);
        super.onStart();
        initPaint();
    }

    @Override
    protected List<Task> createTasks() {
        return addAll(
                new BankAndStopTask(this),
                new EatFoodTask(this),
                new ResetAggroTask(this),
                new TravelToCampTask(this),
                new AttackTask(this)
        );
    }

    public Tile[] getCampTiles() {
        return Arrays.copyOf(CAMP_TILES, CAMP_TILES.length);
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

    public void maybeEnableRun() {
        if (!Movement.running() && Movement.energyLevel() >= runEnergyThreshold) {
            if (Movement.running(true)) {
                rollNextRunThreshold();
            }
        }
    }

    public boolean isDormantCrabNearby() {
        Player local = Players.local();
        if (local == null || !local.valid()) {
            return false;
        }
        Npc dormant = Npcs.stream()
                .name("Sandy rocks")
                .within(local, 2.0)
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
                .within(camp, 2.0)
                .filtered(player -> !player.equals(local))
                .first()
                .valid();
    }

    public List<Tile> eligibleCampTiles() {
        List<Tile> eligible = new ArrayList<>();
        for (Tile camp : CAMP_TILES) {
            if (!isCampTileOccupied(camp)) {
                eligible.add(camp);
            }
        }
        return eligible;
    }

    public boolean hopToNextWorld() {
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
                .build();
        addPaint(paint);
    }

    private void readAndValidateConfiguration() {
        useFood = Boolean.TRUE.equals(getOption("Use Food"));

        Object minValue = getOption("Eat Min %");
        Object maxValue = getOption("Eat Max %");
        eatMinPercent = clampToBounds(asInt(minValue, MIN_ALLOWED_EAT_PERCENT));
        eatMaxPercent = clampToBounds(asInt(maxValue, MAX_ALLOWED_EAT_PERCENT));
        if (eatMinPercent > eatMaxPercent) {
            int temp = eatMinPercent;
            eatMinPercent = eatMaxPercent;
            eatMaxPercent = temp;
        }

        if (useFood) {
            Object foodValue = getOption("Food Name");
            String name = foodValue instanceof String ? (String) foodValue : "Lobster";
            name = name == null ? "Lobster" : name.trim();
            if (name.isEmpty()) {
                name = "Lobster";
            }
            configuredFoodName = name;
        } else {
            configuredFoodName = "";
        }
    }

    private int clampToBounds(int value) {
        if (value < MIN_ALLOWED_EAT_PERCENT) {
            return MIN_ALLOWED_EAT_PERCENT;
        }
        if (value > MAX_ALLOWED_EAT_PERCENT) {
            return MAX_ALLOWED_EAT_PERCENT;
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

    private void rollNextRunThreshold() {
        runEnergyThreshold = Random.nextInt(MIN_RUN_THRESHOLD, MAX_RUN_THRESHOLD + 1);
    }

    @ValueChanged(keyName = "Use Food")
    public void onUseFoodChanged(Boolean enabled) {
        updateVisibility("Food Name", Boolean.TRUE.equals(enabled));
    }
}
