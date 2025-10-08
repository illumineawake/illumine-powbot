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
        name = "Allow World Hopping",
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
    private final ModeScheduler modeScheduler = new ModeScheduler(this, config);
    private final WorldHopController worldHopController = new WorldHopController(this);
    private final SuppliesManager suppliesManager = new SuppliesManager(this);
    private final Barb3TickPaint paint = new Barb3TickPaint(this);

    private Tile targetSpotTile = null;
    private Npc currentFishSpot = null;

    private enum NextAction {CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE}

    enum FishingMode {THREE_TICK, NORMAL}

    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1;
    private String lastSpotSource = "";
    private long tickCount = 0;
    private long startTimeMs = 0L;
    private long currentModeEnteredAtMs = 0L;
    private long threeTickAccumulatedMs = 0L;

    public Barb3TickFishingScript() {
        config.applyInitialVisibility();
        worldHopController.applyInitialVisibility();
    }

    private void resetState() {
        targetSpotTile = null;
        currentFishSpot = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        lastSpotSource = "";
        tickCount = 0;
        startTimeMs = 0L;
        currentModeEnteredAtMs = 0L;
        threeTickAccumulatedMs = 0L;
        modeScheduler.reset();
        suppliesManager.reset();
    }

    @Override
    public void onStart() {
        if (!hasLevelRequirements()) {
            log("t=" + tickCount + " stopping script: Barbarian Fishing level requirements not met. You need all of: 48 Fishing, 15 Strength, 15 Agility.");
            getController().stop();
            return;
        }
        log("t=" + tickCount + " starting script");
        try {
            clearPaints();
        } catch (Exception ignored) {
        }

        resetState();
        config.initialize();
        suppliesManager.setHerbName(config.herbName());
        worldHopController.initialize();

        modeScheduler.initialiseMode();
        long now = System.currentTimeMillis();
        startTimeMs = now;
        currentModeEnteredAtMs = now;
        threeTickAccumulatedMs = 0L;

        paint.apply();

        if (Camera.getZoom() > 1) {
            Camera.moveZoomSlider(0);
            Condition.sleep(Random.nextInt(500, 2000));
        }
        Inventory.open();
    }

    @Override
    public void onStop() {
        log("t=" + tickCount + " stopped script");
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        if (modeScheduler.tickFishing() && currentModeEnteredAtMs > 0) {
            threeTickAccumulatedMs += Math.max(0L, System.currentTimeMillis() - currentModeEnteredAtMs);
        }
        worldHopController.reset();
        config.reset();
        resetState();
    }

    @Override
    public boolean canBreak() {
        return nextAction == NextAction.SELECT_TAR || nextAction == NextAction.COMBINE_HERB || !modeScheduler.tickFishing();
    }

    @Override
    public void poll() {
        long now = System.currentTimeMillis();

        worldHopController.updateHopDue(now);

        if (config.switchingEnabled() && !modeScheduler.switchQueued() && modeScheduler.modeExpiresAtMs() > 0 && now >= modeScheduler.modeExpiresAtMs()) {
            modeScheduler.queueSwitch();
        }

        String coreMissing = missingCoreItem();
        if (!coreMissing.isBlank()) {
            log("t=" + tickCount + " stopping script: Missing item " + coreMissing);
            getController().stop();
            return;
        }

        if (worldHopController.canWorldHop(canBreak())) {
            worldHopController.performWorldHop();
            worldHopController.scheduleNextHop();
            return;
        }

        boolean tickFishing = modeScheduler.tickFishing();
        if (!tickFishing) {
            if (config.switchingEnabled() && !suppliesManager.hasThreeTickSuppliesAvailable()) {
                suppliesManager.handleOutOfSupplies(suppliesManager.determineMissingSupply(), config.switchToNormalOnSuppliesOut());
            }
            handleNormalMode();
            return;
        }

        Inventory.disableShiftDropping();

        if (!suppliesManager.ensureSuppliesForActiveMode()) {
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

    private boolean clickFishingSpot() {
        currentFishSpot = findSpotAtTargetOrNearest();
        if (!currentFishSpot.valid()) {
            log("No fishing spot found");
            return false;
        }
        targetSpotTile = currentFishSpot.tile();

        if (targetSpotTile.distanceTo(Players.local()) >= 5) {
            log("Moved far away from target spot, repositioning");
            return false;
        }

        boolean ok = currentFishSpot.interact("Use-rod", false);
        if (ok) {
            log("t=" + tickCount + " fishing: clicked spot");
            if (Players.local().distanceTo(targetSpotTile) > 1) {
                Condition.wait(() -> Players.local().distanceTo(targetSpotTile) <= 1, 150, 20);
            }
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

    private String lastLoggedMessage = "";

    void log(String message) {
        if (getLog() == null || message == null) {
            return;
        }
        String formatted = "[illu3TBarb] " + message;
        if (formatted.equals(lastLoggedMessage)) {
            return;
        }
        lastLoggedMessage = formatted;
        getLog().info(formatted);
    }

    String formatSwitchCountdown() {
        if (!config.switchingEnabled()) {
            return "N/A";
        }
        return Barb3TickPaint.formatMs(modeScheduler.modeExpiresAtMs() - System.currentTimeMillis());
    }

    String formatThreeTickShare() {
        if (startTimeMs <= 0) {
            return "0.0%";
        }
        long now = System.currentTimeMillis();
        long total = Math.max(1L, now - startTimeMs);
        long threeTickTime = threeTickAccumulatedMs;
        if (modeScheduler.tickFishing() && currentModeEnteredAtMs > 0) {
            threeTickTime += Math.max(0L, now - currentModeEnteredAtMs);
        }
        double share = (double) threeTickTime / (double) total * 100.0;
        if (share < 0.0) {
            share = 0.0;
        }
        return String.format(Locale.ENGLISH, "%.1f%%", share);
    }

    private void applyMode(FishingMode mode) {
        long now = System.currentTimeMillis();
        boolean wasThreeTick = modeScheduler.tickFishing();
        if (wasThreeTick && currentModeEnteredAtMs > 0) {
            threeTickAccumulatedMs += Math.max(0L, now - currentModeEnteredAtMs);
        }
        modeScheduler.setFishingMode(mode);
        log("t=" + tickCount + " mode: switched to " + mode.name().toLowerCase(Locale.ENGLISH));
        currentModeEnteredAtMs = now;
    }

    private boolean toggleMode() {
        if (!config.switchingEnabled()) {
            return false;
        }
        boolean switchingToThreeTick = !modeScheduler.tickFishing();
        if (switchingToThreeTick && !suppliesManager.ensureSuppliesForUpcomingMode()) {
            return false;
        }
        applyMode(modeScheduler.tickFishing() ? FishingMode.NORMAL : FishingMode.THREE_TICK);
        return true;
    }

    private void consumeSwitchQueueAfterClick() {
        if (!modeScheduler.switchQueued()) {
            return;
        }
        if (!toggleMode()) {
            return;
        }
        modeScheduler.clearQueue();
        if (modeScheduler.tickFishing()) {
            nextAction = NextAction.SELECT_TAR;
        } else {
            nextAction = NextAction.CLICK_SPOT;
        }
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
        boolean currentlyAnimating = local.animation() != -1;
        if (currentlyAnimating && currentFishSpot != null && currentFishSpot.valid()) {
            return;
        }

        boolean success = clickFishingSpot();
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
        log("t=" + tickCount + " dropping: dropped all leaping fish");
    }

    private void handleClickSpot() {
        if (tickCount <= actionGateGT) {
            return;
        }

        boolean success = clickFishingSpot();
        if (success) {
            consumeSwitchQueueAfterClick();
            actionGateGT = tickCount;
            if (modeScheduler.tickFishing()) {
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
        Item tar = Inventory.stream().name("Swamp tar").first();
        boolean success = tar.valid() && tar.click();
        actionGateGT = tickCount;
        if (success) {
            log("t=" + tickCount + " combining: selected swamp tar");
            nextAction = NextAction.COMBINE_HERB;
        }
    }

    private void handleCombineHerb() {
        if (tickCount <= actionGateGT) {
            return;
        }
        Item herb = Inventory.stream().name(suppliesManager.herbName()).first();
        boolean success = herb.valid() && herb.click();
        actionGateGT = tickCount;
        if (success) {
            log("t=" + tickCount + " combining: used herb");
            nextAction = NextAction.DROP_ONE;
            Condition.sleep(Random.nextInt(20, 35));
            handleDropOne();
        }
    }

    private void handleDropOne() {
        nextAction = NextAction.CLICK_SPOT;
        boolean success = dropOneLeapingFish();
        if (success) {
            log("t=" + tickCount + " dropping: dropped one leaping fish");
        }
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
            return false;
        }

        for (Tile n : neighbours) {
            if (n == null || n.equals(me)) {
                continue;
            }

            if (!n.reachable()) {
                continue;
            }
            log("Stepping to nearby tile: " + n);
            return n.matrix().click();
        }
        return false;
    }

    private boolean hasLevelRequirements() {
        return Skill.Fishing.realLevel() >= 48 &&
                Skill.Strength.realLevel() >= 15 &&
                Skill.Agility.realLevel() >= 15;
    }

    void switchToPermanentNormalMode() {
        config.setFrequencyMode(ThreeTickFrequencyMode.NEVER);
        modeScheduler.clearQueue();
        applyMode(FishingMode.NORMAL);
        modeScheduler.refreshScheduleAfterConfigChange();
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
        suppliesManager.setHerbName(config.herbName());
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

    long currentTick() {
        return tickCount;
    }

    Barb3TickConfig getConfig() {
        return config;
    }

    WorldHopController getWorldHopController() {
        return worldHopController;
    }

    boolean isTickFishing() {
        return modeScheduler.tickFishing();
    }
}
