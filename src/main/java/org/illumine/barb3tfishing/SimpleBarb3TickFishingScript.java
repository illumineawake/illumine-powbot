package org.illumine.barb3tfishing;

import org.powbot.api.rt4.*;
import org.powbot.api.Tile;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptManifest;
import com.google.common.eventbus.Subscribe;
import org.powbot.api.event.TickEvent;
import java.util.List;

@ScriptManifest(
        name = "Simple Barb 3T",
        description = "Barebones 3-tick barbarian fishing using Guam leaf + Swamp tar",
        author = "illumine",
        category = ScriptCategory.Fishing,
        version = "0.0.1"
)
public class SimpleBarb3TickFishingScript extends AbstractScript {
    // Prefer first-option on specific barbarian spot IDs over action text
    private static final int[] BARB_SPOT_IDS = new int[]{1542, 7323};
    private static final String HERB_NAME = "Guam leaf";
    private static final String TAR_NAME = "Swamp tar";

    private Tile targetSpotTile = null;
    private enum NextAction { CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE }

    // Simple poll-driven scheduling
    private NextAction nextAction = NextAction.CLICK_SPOT;
    private long actionGateGT = -1L;
    private String lastSpotSource = "";
    private long tickCount = 0L;

    @Override
    public void onStart() {
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] starting");
        }
        try {
            clearPaints();
        } catch (Exception ignored) { }
        addPaint(org.powbot.api.script.paint.PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Fishing)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Agility)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Strength)
                .build());
        // Initialize tick cycle
        targetSpotTile = null;
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1L;
        tickCount = 0L;
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
        nextAction = NextAction.CLICK_SPOT;
        actionGateGT = -1;
        try {
            clearPaints();
        } catch (Exception ignored) { }
    }

    @Override
    public void poll() {
        // Supplies check; skip if unavailable
        if (!hasItem(HERB_NAME) || !hasItem(TAR_NAME)) {
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
                return;
            }
            case DROP_ONE: {
                handleDropOne();
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent tick) {
        tickCount++;
    }

    private boolean clickFishingSpot() {
        Npc spot = findSpotAtTargetOrNearest();
        if (!spot.valid()) {
            logOnce("spot", "No fishing spot found");
            return false;
        }
        // Lock/refresh the target to this spot's tile
        targetSpotTile = spot.tile();
        dbgExec("clicking_spot", "id=" + spot.id() + ", source=" + lastSpotSource + ", tile=" + targetSpotTile);
        boolean ok = spot.interact("Use-rod", false);
        if (!ok) {
            dbgExec("click", "interact returned false");
        }
        return ok;
    }

    private Npc findSpotAtTargetOrNearest() {
        if (targetSpotTile != null) {
            Npc locked = Npcs.stream()
                    .id(BARB_SPOT_IDS)
                    .at(targetSpotTile)
                    .first();
            if (locked.valid()) {
                lastSpotSource = "locked";
                return locked;
            }
        }

        Npc nearest = Npcs.stream()
                .id(BARB_SPOT_IDS)
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
//
//        Item selected = Inventory.selectedItem();
//        if (selected.valid()) {
//            selected.interact("Cancel");
//        }
        dbgExec("poll", "t=" + tickCount + " | click spot: attempt");
        boolean success = clickFishingSpot();
        dbgExec("poll", "t=" + tickCount + " | click spot: " + (success ? "success" : "failed"));
        actionGateGT = tickCount;
        if (success) {
            nextAction = NextAction.SELECT_TAR;
        }
    }

    private void handleSelectTar() {
        if (tickCount <= actionGateGT) {
            return;
        }
        dbgExec("poll", "t=" + tickCount + " | select tar: attempt");
        Item tar = Inventory.stream().name(TAR_NAME).first();
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
            handleDropOne();
        }
    }

    private void handleDropOne() {
        boolean success = dropOneLeapingFish();
        dbgExec("poll", "t=" + tickCount + " | drop one leaping=" + success);
        nextAction = NextAction.CLICK_SPOT;
    }
}
