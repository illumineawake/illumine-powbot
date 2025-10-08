package org.illumine.barb3tfishing;

import com.google.common.eventbus.Subscribe;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.event.TickEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptManifest;
import org.powbot.api.script.paint.PaintBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ScriptManifest(
        name = "Simple Barb 3T",
        description = "Barebones 3-tick barbarian fishing using Guam leaf + Swamp tar",
        author = "illumine",
        category = ScriptCategory.Fishing,
        version = "0.0.1"
)
public class SimpleBarb3TickFishingScript extends AbstractScript {
    private static final String HERB_NAME = "Guam leaf";

    private Tile targetSpotTile = null;
    private Npc currentFishSpot = null;

    private enum NextAction {CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE}

    private enum FishingMode {THREE_TICK, NORMAL}

    // Simple poll-driven scheduling
    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1;
    private String lastSpotSource = "";
    private long tickCount = 0;
    private FishingMode fishingMode = FishingMode.THREE_TICK;
    private boolean tickFishing = true;
    private long modeExpiresAtMs = 0L;
    private boolean switchQueued = false;

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
        fishingMode = FishingMode.THREE_TICK;
        tickFishing = true;
        scheduleNextWindow();
        switchQueued = false;
        addPaint(PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(Skill.Fishing)
                .trackSkill(Skill.Agility)
                .trackSkill(Skill.Strength)
                .addString("Mode: ", () -> tickFishing ? "3Tick Fishing" : "Normal Fishing")
                .addString("Switch mode in: ", () -> formatMs(modeExpiresAtMs - System.currentTimeMillis()))
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
        fishingMode = FishingMode.THREE_TICK;
        tickFishing = true;
        switchQueued = false;
        modeExpiresAtMs = 0L;
    }

    @Override
    public boolean canBreak() {
        return nextAction == NextAction.SELECT_TAR || nextAction == NextAction.COMBINE_HERB || !tickFishing;
    }

    @Override
    public void poll() {
        long now = System.currentTimeMillis();
        if (!switchQueued && modeExpiresAtMs > 0 && now >= modeExpiresAtMs) {
            switchQueued = true;
            dbgSched("mode", "Mode switch queued");
        }

        String missingItem = missingItem();

        if (!missingItem.isBlank()) {
            logOnce("Stopping", "Missing item" + missingItem);
            getController().stop();
            return;
        }

        if (!tickFishing) {
            handleNormalMode();
            return;
        }

        Inventory.disableShiftDropping();

        if (!hasItem(HERB_NAME)) {
            if (cleanHerb()) {
                Condition.sleep(Random.nextInt(200, 3000));
            } else {
                logOnce("Stopping", "Missing clean herb in inventory");
            }
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

    private String missingItem() {
        if (!hasItem("Swamp tar")) {
            return "Swamp tar";
        }

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

    private long rollThreeTickDurationMs() {
        return Random.nextInt(30_000, 120_000);
    }

    private long rollNormalDurationMs() {
        return Random.nextInt(120_000, 300_000);
    }

    private void scheduleNextWindow() {
        long duration = tickFishing ? rollThreeTickDurationMs() : rollNormalDurationMs();
        modeExpiresAtMs = System.currentTimeMillis() + duration;
    }

    private void setFishingMode(FishingMode mode) {
        fishingMode = mode;
        tickFishing = mode == FishingMode.THREE_TICK;
        scheduleNextWindow();
        dbgSched("mode", "Switched to " + (tickFishing ? "3Tick Fishing" : "Normal Fishing"));
    }

    private void toggleMode() {
        setFishingMode(tickFishing ? FishingMode.NORMAL : FishingMode.THREE_TICK);
    }

    private void consumeSwitchQueueAfterClick() {
        if (!switchQueued) {
            return;
        }
        switchQueued = false;
        toggleMode();
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
        Item herb = Inventory.stream().name(HERB_NAME).first();
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

}
