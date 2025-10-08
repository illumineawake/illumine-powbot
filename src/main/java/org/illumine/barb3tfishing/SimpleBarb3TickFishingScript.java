package org.illumine.barb3tfishing;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.event.NpcAnimationChangedEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.Tile;
import org.powbot.api.rt4.stream.item.ItemStream;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptManifest;
import com.google.common.eventbus.Subscribe;
import org.powbot.api.event.TickEvent;
import org.powbot.api.script.paint.PaintBuilder;
import org.powbot.mobile.script.ScriptManager;

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

    // Simple poll-driven scheduling
    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1;
    private String lastSpotSource = "";
    private long tickCount = 0;

    @Override
    public void onStart() {
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] starting");
        }
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        addPaint(PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(Skill.Fishing)
                .trackSkill(Skill.Agility)
                .trackSkill(Skill.Strength)
                .build());
        // Initialize tick cycle
        targetSpotTile = null;
        currentFishSpot = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        tickCount = 0;
        if (Camera.getZoom() > 0) {
            Camera.moveZoomSlider(0);
        }
        if (!Inventory.opened()) {
            Inventory.open();
        }
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
    }

    @Override
    public boolean canBreak() {
        return nextAction == NextAction.SELECT_TAR || nextAction == NextAction.COMBINE_HERB;
    }

    @Override
    public void poll() {
        if (!hasItem("Swamp tar") || !hasItem("Feather")) {
            logOnce("Stopping", "Missing Swamp Tar or Feathers");
            return;
        }

        if (!hasItem(HERB_NAME)) {
            if (cleanHerb()) {
                Condition.sleep(Random.nextInt(200, 1000));
                return;
            } else {
                logOnce("Stopping", "Missing clean herb in inventory");
                return;
            }
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

        if (!currentFishSpot.inViewport()) {
            return false;
        }
        // Lock/refresh the target to this spot's tile
        targetSpotTile = currentFishSpot.tile();
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
        actionGateGT = tickCount;
        if (success) {
            nextAction = NextAction.SELECT_TAR;
        } else if (Players.local().animation() != -1) {
//            if (!pickupFish()) {
            stepToAdjacentTile();
            Condition.sleep(600);
//            }
        } else if (currentFishSpot != null && !currentFishSpot.inViewport()) {
            Camera.turnTo(currentFishSpot);
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
        boolean success = dropOneLeapingFish();
        dbgExec("poll", "t=" + tickCount + " | drop one leaping=" + success);
        nextAction = NextAction.CLICK_SPOT;
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

}
