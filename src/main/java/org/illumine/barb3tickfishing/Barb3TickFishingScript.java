package org.illumine.barb3tickfishing;

import com.google.common.eventbus.Subscribe;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.event.TickEvent;
import org.powbot.api.rt4.Camera;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Item;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Npc;
import org.powbot.api.rt4.Npcs;
import org.powbot.api.rt4.Player;
import org.powbot.api.rt4.Players;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.ValueChanged;
import java.util.ArrayList;
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
@ScriptConfiguration(
        name = "Allow World Hopping (Anti-ban)",
        description = "Enable timed world hopping",
        optionType = OptionType.BOOLEAN,
        defaultValue = "false"
)
@ScriptConfiguration(
        name = "World Hop every X minutes",
        description = "Minutes between world hops",
        optionType = OptionType.INTEGER,
        defaultValue = "10",
        visible = false
)
@ScriptManifest(
        name = "illu 3Tick Barb Fishing",
        description = "3-tick barbarian fishing using herb + Swamp tar. Your Settings in game must be set to 100ms hold for menu pop-up.",
        author = "illumine",
        category = ScriptCategory.Fishing,
        version = "0.0.1"
)
public class Barb3TickFishingScript extends AbstractScript {
    static final String DEFAULT_HERB_NAME = "Guam leaf";

    private final Barb3TickConfig config = new Barb3TickConfig(this, DEFAULT_HERB_NAME);
    private final WorldHopController worldHopController = new WorldHopController(this);
    private final Barb3TickPaint paint = new Barb3TickPaint(this);

    private Tile targetSpotTile = null;
    private Npc currentFishSpot = null;

    private enum NextAction {CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE}

    private enum FishingMode {THREE_TICK, NORMAL}

    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1;
    private String lastSpotSource = "";
    private long tickCount = 0;
    private FishingMode fishingMode = FishingMode.THREE_TICK;
    private boolean tickFishing = true;
    private long modeExpiresAtMs = 0L;
    private boolean switchQueued = false;
    private long startTimeMs = 0L;
    private long currentModeEnteredAtMs = 0L;
    private long threeTickAccumulatedMs = 0L;
    private boolean suppliesFallbackTriggered = false;

    private String herbName = DEFAULT_HERB_NAME;

    public Barb3TickFishingScript() {
        config.applyInitialVisibility();
        worldHopController.applyInitialVisibility();
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

        config.initialize();
        worldHopController.initialize();

        herbName = config.herbName();
        fishingMode = config.frequencyMode().startsInThreeTick() ? FishingMode.THREE_TICK : FishingMode.NORMAL;
        tickFishing = fishingMode == FishingMode.THREE_TICK;

        suppliesFallbackTriggered = false;
        long now = System.currentTimeMillis();
        startTimeMs = now;
        currentModeEnteredAtMs = now;
        threeTickAccumulatedMs = 0L;
        if (config.switchingEnabled()) {
            scheduleNextWindow();
        } else {
            modeExpiresAtMs = 0L;
        }
        switchQueued = false;

        paint.apply();

        targetSpotTile = null;
        currentFishSpot = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        tickCount = 0;
        if (Camera.getZoom() > 1) {
            logOnce("Camera", "Current Zoom level is: " + Camera.getZoom());
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
        config.reset();
        herbName = DEFAULT_HERB_NAME;
        suppliesFallbackTriggered = false;
        worldHopController.reset();
        startTimeMs = 0L;
        currentModeEnteredAtMs = 0L;
        threeTickAccumulatedMs = 0L;
    }

    @Override
    public boolean canBreak() {
        return nextAction == NextAction.SELECT_TAR || nextAction == NextAction.COMBINE_HERB || !tickFishing;
    }

    @Override
    public void poll() {
        long now = System.currentTimeMillis();

        worldHopController.updateHopDue(now);

        if (config.switchingEnabled() && !switchQueued && modeExpiresAtMs > 0 && now >= modeExpiresAtMs) {
            switchQueued = true;
            dbgSched("mode", "Mode switch queued");
        }

        String coreMissing = missingCoreItem();
        if (!coreMissing.isBlank()) {
            logOnce("Stopping", "Missing item " + coreMissing);
            getController().stop();
            return;
        }

        if (worldHopController.canWorldHop(canBreak())) {
            worldHopController.performWorldHop();
            worldHopController.scheduleNextHop();
            return;
        }

        if (!tickFishing) {
            if (config.switchingEnabled() && !hasThreeTickSuppliesAvailable()) {
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

    public static void main(String[] args) {
        new Barb3TickFishingScript().startScript();
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

    private boolean hasItem(String name) {
        Item item = Inventory.stream().name(name).first();
        return item.valid();
    }

    private String lastLogKey = "";

    void logOnce(String category, String message) {
        if (getLog() == null || message == null) return;
        String key = category + "|t=" + tickCount + "|" + message;
        if (!key.equals(lastLogKey)) {
            lastLogKey = key;
            getLog().info("[SimpleBarb3T] t=" + tickCount + " " + category + ": " + message);
        }
    }

    void dbgSched(String category, String message) {
        if (getLog() == null || message == null) return;
        getLog().info("[SimpleBarb3T][SCHED] t=" + tickCount + " | " + category + " | " + message);
    }

    void dbgExec(String category, String message) {
        if (getLog() == null || message == null) return;
        getLog().info("[SimpleBarb3T][EXEC] t=" + tickCount + " | " + category + " | " + message);
    }

    private long rollThreeTickDurationMs() {
        switch (config.frequencyMode()) {
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
        switch (config.frequencyMode()) {
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
        if (!config.switchingEnabled()) {
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

    private boolean toggleFishingMode() {
        if (!config.switchingEnabled()) {
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
        if (!toggleFishingMode()) {
            return;
        }
        switchQueued = false;
        if (tickFishing) {
            nextAction = NextAction.SELECT_TAR;
        } else {
            nextAction = NextAction.CLICK_SPOT;
        }
    }

    String formatSwitchCountdown() {
        if (!config.switchingEnabled()) {
            return "N/A";
        }
        return Barb3TickPaint.formatMs(modeExpiresAtMs - System.currentTimeMillis());
    }

    String formatThreeTickShare() {
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
        Condition.sleep(Random.nextInt(200, 3000));
        Inventory.open();
        Inventory.enableShiftDropping();
        Inventory.drop(leapingFish);
        Inventory.disableShiftDropping();
        Condition.sleep(Random.nextInt(200, 3000));
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
            Condition.sleep(Random.nextInt(20, 35));
            handleDropOne();
        }
    }

    private void handleDropOne() {
        nextAction = NextAction.CLICK_SPOT;
        boolean success = dropOneLeapingFish();
        dbgExec("poll", "t=" + tickCount + " | drop one leaping=" + success);
    }

    private boolean stepToAdjacentTile() {
        Player local = Players.local();

        Tile me = local.tile();
        Tile[] neighbours;
        try {
            neighbours = new Tile[]{
                    me.derive(1, 0), me.derive(-1, 0), me.derive(0, 1), me.derive(0, -1),
                    me.derive(1, 1), me.derive(1, -1), me.derive(-1, 1), me.derive(-1, -1)
            };
        } catch (Throwable t) {
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
        if (!config.switchToNormalOnSuppliesOut()) {
            logOnce("Stopping", "Out of 3T supplies: " + missingItem);
            getController().stop();
            return;
        }
        logOnce("Fallback", "Out of 3T supplies (" + missingItem + "), switching to normal fishing");
        switchToPermanentNormalMode();
    }

    private void switchToPermanentNormalMode() {
        suppliesFallbackTriggered = true;
        config.setFrequencyMode(ThreeTickFrequencyMode.NEVER);
        switchQueued = false;
        setFishingMode(FishingMode.NORMAL);
        modeExpiresAtMs = 0L;
        nextAction = NextAction.CLICK_SPOT;
    }

    void onWorldHopSuccess() {
        targetSpotTile = null;
        currentFishSpot = null;
        lastSpotSource = "";
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = tickCount;
    }

    @ValueChanged(keyName = "3Tick Frequency Mode")
    public void onFrequencyModeOptionChanged(String newValue) {
        ThreeTickFrequencyMode mode = ThreeTickFrequencyMode.fromOptionString(newValue);
        config.updateVisibilityForMode(mode);
    }

    @ValueChanged(keyName = "Clean Herb Name")
    private void onHerbNameOptionChanged(String newValue) {
        config.setHerbName(newValue);
        herbName = config.herbName();
    }

    @ValueChanged(keyName = "Allow World Hopping")
    public void onAllowWorldHoppingOptionChanged(Boolean enabled) {
        worldHopController.setHopEnabled(Boolean.TRUE.equals(enabled));
    }

    @ValueChanged(keyName = "World Hop every X minutes")
    public void onWorldHopIntervalChanged(Integer minutes) {
        int parsed = ScriptOptionParser.asInt(minutes, worldHopController.hopIntervalMinutes(), 1);
        worldHopController.setHopIntervalMinutes(parsed);
    }

    Barb3TickConfig getConfig() {
        return config;
    }

    WorldHopController getWorldHopController() {
        return worldHopController;
    }

    boolean isTickFishing() {
        return tickFishing;
    }
}
