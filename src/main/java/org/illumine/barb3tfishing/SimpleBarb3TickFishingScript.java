package org.illumine.barb3tfishing;

import com.google.common.eventbus.Subscribe;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.event.TickEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.ValueChanged;
import org.powbot.api.script.paint.PaintBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@ScriptConfiguration(
        name = "3Tick Frequency Mode",
        description = "How often to 3-tick",
        optionType = OptionType.STRING,
        defaultValue = "Sometimes 3Tick",
        allowedValues = {
                "Always 3Tick (VERY DANGEROUS!)",
                "Mostly 3Tick",
                "Sometimes 3Tick",
                "Never 3Tick"
        }
)
@ScriptConfiguration(
        name = "Clean Herb Name",
        description = "Clean herb used for 3-tick method",
        optionType = OptionType.STRING,
        defaultValue = "Guam leaf",
        visible = true
)
@ScriptConfiguration(
        name = "Switch to normal fishing if out of 3Tick supplies",
        description = "Fallback to normal mode when 3-tick supplies run out",
        optionType = OptionType.BOOLEAN,
        defaultValue = "true",
        visible = true
)
@ScriptManifest(
        name = "Simple Barb 3T",
        description = "Barebones 3-tick barbarian fishing using Guam leaf + Swamp tar",
        author = "illumine",
        category = ScriptCategory.Fishing,
        version = "0.0.1"
)
public class SimpleBarb3TickFishingScript extends AbstractScript {
    private static final String DEFAULT_HERB_NAME = "Guam leaf";

    private Tile targetSpotTile = null;
    private Npc currentFishSpot = null;

    private enum NextAction {CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE}

    private enum FishingMode {THREE_TICK, NORMAL}

    private enum ThreeTickFrequencyMode {
        ALWAYS("Always 3Tick (VERY DANGEROUS!)"),
        MOSTLY("Mostly 3Tick"),
        SOMETIMES("Sometimes 3Tick"),
        NEVER("Never 3Tick");

        private final String label;

        ThreeTickFrequencyMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean startsInThreeTick() {
            return this != NEVER;
        }

        public boolean switchingEnabled() {
            return this == MOSTLY || this == SOMETIMES;
        }

        public static ThreeTickFrequencyMode fromOptionString(String value) {
            if (value != null) {
                for (ThreeTickFrequencyMode mode : values()) {
                    if (mode.label.equalsIgnoreCase(value.trim())) {
                        return mode;
                    }
                }
            }
            return SOMETIMES;
        }
    }

    // Simple poll-driven scheduling
    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1;
    private String lastSpotSource = "";
    private long tickCount = 0;
    private FishingMode fishingMode = FishingMode.THREE_TICK;
    private boolean tickFishing = true;
    private long modeExpiresAtMs = 0L;
    private boolean switchQueued = false;
    private ThreeTickFrequencyMode frequencyMode = ThreeTickFrequencyMode.SOMETIMES;
    private boolean switchingEnabled = true;
    private long startTimeMs = 0L;
    private long currentModeEnteredAtMs = 0L;
    private long threeTickAccumulatedMs = 0L;
    private String herbName = DEFAULT_HERB_NAME;
    private boolean switchToNormalOnSuppliesOut = true;
    private boolean suppliesFallbackTriggered = false;

    public SimpleBarb3TickFishingScript() {
        try {
            ThreeTickFrequencyMode initialMode = ThreeTickFrequencyMode.fromOptionString(asString(getOption("3Tick Frequency Mode"), ThreeTickFrequencyMode.SOMETIMES.label()));
            applyOptionVisibility(initialMode);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStart() {
        if (!hasLevelRequirements()) {
            getLog().info("[SimpleBarb3T] Barbarian Fishing level requirements not met. You need all of: 48 Fishing, 15 Strength, 15 Agility.");
            getController().stop();
            return;
        }
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] starting");
        }
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        Object optionValue = getOption("3Tick Frequency Mode");
        String optionString = optionValue == null ? ThreeTickFrequencyMode.SOMETIMES.label() : optionValue.toString();
        frequencyMode = ThreeTickFrequencyMode.fromOptionString(optionString);
        switchingEnabled = frequencyMode.switchingEnabled();
        fishingMode = frequencyMode.startsInThreeTick() ? FishingMode.THREE_TICK : FishingMode.NORMAL;
        tickFishing = fishingMode == FishingMode.THREE_TICK;
        herbName = resolveHerbNameOption();
        Object fallbackOption = getOption("Switch to normal fishing if out of 3Tick supplies");
        if (fallbackOption instanceof Boolean) {
            switchToNormalOnSuppliesOut = (Boolean) fallbackOption;
        } else if (fallbackOption != null) {
            switchToNormalOnSuppliesOut = Boolean.parseBoolean(fallbackOption.toString());
        } else {
            switchToNormalOnSuppliesOut = true;
        }
        suppliesFallbackTriggered = false;
        applyOptionVisibility(frequencyMode);
        long now = System.currentTimeMillis();
        startTimeMs = now;
        currentModeEnteredAtMs = now;
        threeTickAccumulatedMs = 0L;
        if (switchingEnabled) {
            scheduleNextWindow();
        } else {
            modeExpiresAtMs = 0L;
        }
        switchQueued = false;
        addPaint(PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(Skill.Fishing)
                .trackSkill(Skill.Agility)
                .trackSkill(Skill.Strength)
                .addString("Mode: ", () -> tickFishing ? "3Tick Fishing" : "Normal Fishing")
                .addString("3T Frequency: ", () -> frequencyMode.label())
                .addString("Time(%) Spent 3Tick Fishing: ", this::formatThreeTickShare)
                .addString("Switching fishing mode in: ", this::formatSwitchCountdown)
                .build());
        // Initialize tick cycle
        targetSpotTile = null;
        currentFishSpot = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        tickCount = 0;
        if (Camera.getZoom() > 0) {
            Camera.moveZoomSlider(0);
            Condition.sleep(Random.nextInt(500, 2000));
        }
        Inventory.open();
        dbgSched("init", "initialized");
    }

    @Override
    public void onStop() {
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] stopped");
        }
        targetSpotTile = null;
        currentFishSpot = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        if (tickFishing && currentModeEnteredAtMs > 0) {
            threeTickAccumulatedMs += Math.max(0L, System.currentTimeMillis() - currentModeEnteredAtMs);
        }
        fishingMode = FishingMode.THREE_TICK;
        tickFishing = true;
        switchQueued = false;
        modeExpiresAtMs = 0L;
        frequencyMode = ThreeTickFrequencyMode.SOMETIMES;
        switchingEnabled = true;
        startTimeMs = 0L;
        currentModeEnteredAtMs = 0L;
        threeTickAccumulatedMs = 0L;
        herbName = DEFAULT_HERB_NAME;
        switchToNormalOnSuppliesOut = true;
        suppliesFallbackTriggered = false;
    }

    @Override
    public boolean canBreak() {
        return nextAction == NextAction.SELECT_TAR || nextAction == NextAction.COMBINE_HERB || !tickFishing;
    }

    @Override
    public void poll() {
        long now = System.currentTimeMillis();
        if (switchingEnabled && !switchQueued && modeExpiresAtMs > 0 && now >= modeExpiresAtMs) {
            switchQueued = true;
            dbgSched("mode", "Mode switch queued");
        }

        String coreMissing = missingCoreItem();
        if (!coreMissing.isBlank()) {
            logOnce("Stopping", "Missing item " + coreMissing);
            getController().stop();
            return;
        }

        if (!tickFishing) {
            if (switchingEnabled && !hasThreeTickSuppliesAvailable()) {
                handleOutOfThreeTickSupplies(determineMissingThreeTickSupply());
            }
            handleNormalMode();
            return;
        }

        Inventory.disableShiftDropping();

        if (!ensureThreeTickSuppliesForActiveMode()) {
            return;
        }

        switch (nextAction) {
            case CLICK_SPOT: {
                handleClickSpot();
                return;
            }
            case SELECT_TAR: {
                handleSelectTar();
                return;
            }
            case COMBINE_HERB: {
                handleCombineHerb();
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent tick) {
        tickCount++;
    }

    private boolean cleanHerb() {
        Item cleanHerb = Inventory.stream().nameContains("Grimy").action("Clean").first();
        if (cleanHerb.valid()) {
            cleanHerb.interact("Clean");
            return true;
        }
        return false;
    }

    private boolean clickFishingSpot() {
        currentFishSpot = findSpotAtTargetOrNearest();
        if (!currentFishSpot.valid()) {
            logOnce("spot", "No fishing spot found");
            return false;
        }
        // Lock/refresh the target to this spot's tile
        targetSpotTile = currentFishSpot.tile();

        if (targetSpotTile.distanceTo(Players.local()) >= 5) {
            logOnce("spot", "Moved far away, moving to it.");
            return false;
        }

        dbgExec("clicking_spot", "id=" + currentFishSpot.id() + ", source=" + lastSpotSource + ", tile=" + targetSpotTile);
        boolean ok = currentFishSpot.interact("Use-rod", false);
        if (!ok) {
            dbgExec("click", "interact returned false");
        } else if (Players.local().distanceTo(targetSpotTile) > 1) {
            Condition.wait(() -> Players.local().distanceTo(targetSpotTile) <= 1, 150, 20);
        }
        return ok;
    }

    private Npc findSpotAtTargetOrNearest() {
        if (targetSpotTile != null) {
            Npc locked = Npcs.stream()
                    .name("Fishing spot")
                    .action("Use-rod")
                    .at(targetSpotTile)
                    .first();
            if (locked.valid()) {
                lastSpotSource = "locked";
                return locked;
            }
        }

        Npc nearest = Npcs.stream()
                .name("Fishing spot")
                .action("Use-rod")
                .nearest()
                .first();
        if (nearest.valid()) {
            // Update target only when locked one is gone
            targetSpotTile = nearest.tile();
            lastSpotSource = "nearest";
        } else {
            lastSpotSource = "none";
        }
        return nearest;
    }

    private String missingCoreItem() {
        if (!hasItem("Feather")) {
            return "Feather";
        }
        if (!hasItem("Barbarian rod")) {
            return "Barbarian rod";
        }
        return "";
    }

    private String asString(Object value, String fallback) {
        if (value instanceof String) {
            String s = ((String) value).trim();
            return s.isEmpty() ? fallback : s;
        }
        if (value != null) {
            String s = value.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return fallback;
    }

    private String resolveHerbNameOption() {
        return asString(getOption("Clean Herb Name"), DEFAULT_HERB_NAME);
    }

    private boolean hasThreeTickSuppliesAvailable() {
        return hasItem("Swamp tar") && canObtainCleanHerb();
    }

    private boolean canObtainCleanHerb() {
        if (hasItem(herbName)) {
            return true;
        }
        Item cleanable = Inventory.stream().nameContains("Grimy").action("Clean").first();
        return cleanable.valid();
    }

    private String determineMissingThreeTickSupply() {
        if (!hasItem("Swamp tar")) {
            return "Swamp tar";
        }
        if (!canObtainCleanHerb()) {
            return herbName;
        }
        return "";
    }

    private boolean ensureThreeTickSuppliesForActiveMode() {
        return ensureThreeTickSupplies(true);
    }

    private boolean ensureThreeTickSuppliesForUpcomingMode() {
        return ensureThreeTickSupplies(true);
    }

    private boolean ensureThreeTickSupplies(boolean attemptClean) {
        if (!hasItem("Swamp tar")) {
            handleOutOfThreeTickSupplies("Swamp tar");
            return false;
        }
        if (!hasItem(herbName)) {
            if (attemptClean && cleanHerb()) {
                Condition.sleep(Random.nextInt(200, 3000));
                return false;
            }
            handleOutOfThreeTickSupplies(herbName);
            return false;
        }
        return true;
    }

    private void handleOutOfThreeTickSupplies(String missingItem) {
        if (suppliesFallbackTriggered) {
            return;
        }
        if (missingItem == null || missingItem.isBlank()) {
            missingItem = determineMissingThreeTickSupply();
        }
        if (missingItem.isBlank()) {
            missingItem = "3T supplies";
        }
        if (!switchToNormalOnSuppliesOut) {
            logOnce("Stopping", "Out of 3T supplies: " + missingItem);
            getController().stop();
            return;
        }
        logOnce("Fallback", "Out of 3T supplies (" + missingItem + "), switching to normal fishing");
        switchToPermanentNormalMode();
    }

    private void switchToPermanentNormalMode() {
        suppliesFallbackTriggered = true;
        frequencyMode = ThreeTickFrequencyMode.NEVER;
        switchingEnabled = false;
        switchQueued = false;
        applyOptionVisibility(frequencyMode);
        setFishingMode(FishingMode.NORMAL);
        modeExpiresAtMs = 0L;
        nextAction = NextAction.CLICK_SPOT;
    }

    private boolean hasItem(String name) {
        Item item = Inventory.stream().name(name).first();
        return item.valid();
    }

    private String lastLogKey = "";

    private void logOnce(String category, String message) {
        if (getLog() == null || message == null) return;
        String key = category + "|t=" + tickCount + "|" + message;
        if (!key.equals(lastLogKey)) {
            lastLogKey = key;
            getLog().info("[SimpleBarb3T] t=" + tickCount + " " + category + ": " + message);
        }
    }

    private void dbgSched(String category, String message) {
        if (getLog() == null || message == null) return;
        getLog().info("[SimpleBarb3T][SCHED] t=" + tickCount + " | " + category + " | " + message);
    }

    private void dbgExec(String category, String message) {
        if (getLog() == null || message == null) return;
        getLog().info("[SimpleBarb3T][EXEC] t=" + tickCount + " | " + category + " | " + message);
    }

    private void applyOptionVisibility(ThreeTickFrequencyMode mode) {
        boolean showThreeTickOptions = mode != ThreeTickFrequencyMode.NEVER;
        updateVisibility("Switch to normal fishing if out of 3Tick supplies", showThreeTickOptions);
        updateVisibility("Clean Herb Name", showThreeTickOptions);
    }

    private long rollThreeTickDurationMs() {
        switch (frequencyMode) {
            case ALWAYS:
            case MOSTLY:
                return Random.nextInt(90_000, 180_000);
            case SOMETIMES:
                return Random.nextInt(30_000, 90_000);
            case NEVER:
            default:
                return Random.nextInt(30_000, 90_000);
        }
    }

    private long rollNormalDurationMs() {
        switch (frequencyMode) {
            case MOSTLY:
                return Random.nextInt(30_000, 90_000);
            case SOMETIMES:
                return Random.nextInt(180_000, 300_000);
            case NEVER:
                return Random.nextInt(180_000, 300_000);
            case ALWAYS:
            default:
                return Random.nextInt(30_000, 90_000);
        }
    }

    private void scheduleNextWindow() {
        if (!switchingEnabled) {
            modeExpiresAtMs = 0L;
            return;
        }
        long duration = tickFishing ? rollThreeTickDurationMs() : rollNormalDurationMs();
        modeExpiresAtMs = System.currentTimeMillis() + duration;
    }

    private void setFishingMode(FishingMode mode) {
        long now = System.currentTimeMillis();
        boolean wasThreeTick = tickFishing;
        if (wasThreeTick && currentModeEnteredAtMs > 0) {
            threeTickAccumulatedMs += Math.max(0L, now - currentModeEnteredAtMs);
        }
        fishingMode = mode;
        tickFishing = mode == FishingMode.THREE_TICK;
        currentModeEnteredAtMs = now;
        scheduleNextWindow();
        dbgSched("mode", "Switched to " + (tickFishing ? "3Tick Fishing" : "Normal Fishing"));
    }

    private boolean toggleMode() {
        if (!switchingEnabled) {
            return false;
        }
        boolean switchingToThreeTick = !tickFishing;
        if (switchingToThreeTick && !ensureThreeTickSuppliesForUpcomingMode()) {
            return false;
        }
        setFishingMode(tickFishing ? FishingMode.NORMAL : FishingMode.THREE_TICK);
        return true;
    }

    private void consumeSwitchQueueAfterClick() {
        if (!switchQueued) {
            return;
        }
        if (!toggleMode()) {
            return;
        }
        switchQueued = false;
        if (tickFishing) {
            nextAction = NextAction.SELECT_TAR;
        } else {
            nextAction = NextAction.CLICK_SPOT;
        }
    }

    private String formatMs(long msRemaining) {
        if (msRemaining <= 0) {
            return "00:00";
        }
        long totalSeconds = msRemaining / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatSwitchCountdown() {
        if (!switchingEnabled) {
            return "N/A";
        }
        return formatMs(modeExpiresAtMs - System.currentTimeMillis());
    }

    private String formatThreeTickShare() {
        if (startTimeMs <= 0) {
            return "0.0%";
        }
        long now = System.currentTimeMillis();
        long total = Math.max(1L, now - startTimeMs);
        long threeTickTime = threeTickAccumulatedMs;
        if (tickFishing && currentModeEnteredAtMs > 0) {
            threeTickTime += Math.max(0L, now - currentModeEnteredAtMs);
        }
        double share = (double) threeTickTime / (double) total * 100.0;
        if (share < 0.0) {
            share = 0.0;
        }
        return String.format(Locale.ENGLISH, "%.1f%%", share);
    }

    private void handleClickSpotFailure() {
        Player local = Players.local();
        if (local == null) {
            return;
        }
        if (local.animation() != -1 && (currentFishSpot == null || !currentFishSpot.valid())) {
            stepToAdjacentTile();
            Condition.sleep(Random.nextInt(1000, 5000));
            return;
        }
        if (currentFishSpot != null && currentFishSpot.valid()) {
            Movement.builder(currentFishSpot)
                    .setWalkUntil(() -> Players.local().distanceTo(currentFishSpot) < 5)
                    .move();
        }
    }

    private boolean dropOneLeapingFish() {
        if (Inventory.stream().nameContains("Leaping").count() <= 1) {
            return false;
        }

        Item fish = Inventory.stream().nameContains("Leaping").first();
        if (fish.valid()) {
            fish.interact("Drop");
            return true;
        }
        return false;
    }

    private void handleNormalMode() {
        if (tickCount <= actionGateGT) {
            return;
        }

        if (Inventory.isFull()) {
            randomizedDropAllLeapingFish();
            actionGateGT = tickCount;
            return;
        }

        Player local = Players.local();
        boolean currentlyAnimating = local != null && local.animation() != -1;
        if (currentlyAnimating && currentFishSpot != null && currentFishSpot.valid()) {
            return;
        }

        dbgExec("normal_mode", "t=" + tickCount + " | click spot: attempt");
        boolean success = clickFishingSpot();
        dbgExec("normal_mode", "t=" + tickCount + " | click spot: " + success);
        if (success) {
            consumeSwitchQueueAfterClick();
            actionGateGT = tickCount;
        } else {
            handleClickSpotFailure();
            actionGateGT = tickCount;
        }
    }

    private void randomizedDropAllLeapingFish() {
        List<Item> rawLeaping = Inventory.stream().nameContains("Leaping").list();
        if (rawLeaping.isEmpty()) {
            return;
        }
        List<Item> leapingFish = new ArrayList<>(rawLeaping);
        leapingFish.removeIf(item -> item == null || !item.valid());
        if (leapingFish.isEmpty()) {
            return;
        }

        Inventory.open();

        int roll = Random.nextInt(0, 100);
        if (roll < 34) {
            Inventory.enableShiftDropping();
            Inventory.drop(leapingFish);
        } else if (roll < 67) {
            Collections.shuffle(leapingFish);
            for (Item fish : leapingFish) {
                if (!fish.valid()) {
                    continue;
                }
                Inventory.drop(fish, false);
                Condition.sleep(Random.nextInt(50, 180));
            }
        } else {
            Collections.shuffle(leapingFish);
            boolean shiftEnabled = Inventory.shiftDroppingEnabled() || Inventory.enableShiftDropping();
            for (Item fish : leapingFish) {
                if (!fish.valid()) {
                    continue;
                }
                boolean useShift = shiftEnabled && Random.nextBoolean();
                Inventory.drop(fish, useShift);
                Condition.sleep(Random.nextInt(60, 200));
            }
        }
        Inventory.disableShiftDropping();
        Condition.sleep(Random.nextInt(200, 600));
    }

    public static void main(String[] args) {
        new SimpleBarb3TickFishingScript().startScript();
    }

    private void handleClickSpot() {
        if (tickCount <= actionGateGT) {
            return;
        }

        dbgExec("poll", "t=" + tickCount + " | click spot: attempt");
        boolean success = clickFishingSpot();
        dbgExec("poll", "t=" + tickCount + " | click spot: " + (success ? "success" : "failed"));
        if (success) {
            consumeSwitchQueueAfterClick();
            actionGateGT = tickCount;
            if (tickFishing) {
                nextAction = NextAction.SELECT_TAR;
            } else {
                nextAction = NextAction.CLICK_SPOT;
            }
        } else {
            handleClickSpotFailure();
        }
    }

    private void handleSelectTar() {
        if (tickCount <= actionGateGT) {
            return;
        }
        Inventory.open();
        dbgExec("poll", "t=" + tickCount + " | select tar: attempt");
        Item tar = Inventory.stream().name("Swamp tar").first();
        boolean success = tar.valid() && tar.click();
        dbgExec("poll", "t=" + tickCount + " | select tar: " + success);
        actionGateGT = tickCount;
        if (success) {
            nextAction = NextAction.COMBINE_HERB;
        }
    }

    private void handleCombineHerb() {
        if (tickCount <= actionGateGT) {
            return;
        }
        dbgExec("poll", "t=" + tickCount + " | combine herb: attempt");
        Item herb = Inventory.stream().name(herbName).first();
        boolean success = herb.valid() && herb.click();
        dbgExec("poll", "t=" + tickCount + " | combine herb: " + success);
        actionGateGT = tickCount;
        if (success) {
            nextAction = NextAction.DROP_ONE;
            Condition.sleep(25);
            handleDropOne();
        }
    }

    private void handleDropOne() {
        nextAction = NextAction.CLICK_SPOT;
        boolean success = dropOneLeapingFish();
        dbgExec("poll", "t=" + tickCount + " | drop one leaping=" + success);
    }

    private boolean pickupFish() {
        if (Inventory.isFull()) {
            return false;
        }

        GroundItem floorFish = GroundItems.stream().nameContains("Leaping").within(1).first();

        if (floorFish.valid()) {
            return floorFish.interact("Take");
        }

        return false;
    }

    // Attempts to move to a neighbouring tile (not current tile) to cancel item mixing.
    // Returns true if a movement action was initiated and resulted in a tile change or motion.
    private boolean stepToAdjacentTile() {
        Player local = Players.local();

        Tile me = local.tile();
        // Prefer cardinal directions first, then diagonals
        Tile[] neighbours;
        try {
            neighbours = new Tile[]{
                    me.derive(1, 0), me.derive(-1, 0), me.derive(0, 1), me.derive(0, -1),
                    me.derive(1, 1), me.derive(1, -1), me.derive(-1, 1), me.derive(-1, -1)
            };
        } catch (Throwable t) {
            // Fallback: if derive is unavailable, do nothing
            dbgExec("adjacent_gen", "failed to derive neighbours: " + t.getMessage());
            return false;
        }

        for (Tile n : neighbours) {
            if (n == null || n.equals(me)) {
                continue;
            }

            if (!n.reachable()) {
                continue;
            }
            logOnce("Cancelling", "Stepping to nearby tile: " + n);
            return n.matrix().click();
        }
        return false;
    }

    private boolean hasLevelRequirements() {
        return Skill.Fishing.realLevel() >= 48 &&
                Skill.Strength.realLevel() >= 15 &&
                Skill.Agility.realLevel() >= 15;
    }

    @ValueChanged(keyName = "3Tick Frequency Mode")
    public void onFrequencyModeOptionChanged(String newValue) {
        ThreeTickFrequencyMode mode = ThreeTickFrequencyMode.fromOptionString(newValue);
        applyOptionVisibility(mode);
    }

    @ValueChanged(keyName = "Clean Herb Name")
    private void onHerbNameOptionChanged(String newValue) {
        if (newValue == null) {
            return;
        }
        String trimmed = newValue.trim();
        herbName = trimmed.isEmpty() ? DEFAULT_HERB_NAME : trimmed;
    }

}
