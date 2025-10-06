package org.illumine.barb3tfishing;

import org.powbot.api.rt4.*;
import org.powbot.api.Tile;
import org.powbot.api.script.AbstractScript;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptManifest;
import com.google.common.eventbus.Subscribe;
import org.powbot.api.event.TickEvent;
import org.powbot.api.Random;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ScriptManifest(
        name = "Simple Barb 3T",
        description = "Barebones 3-tick barbarian fishing using Guam leaf + Swamp tar",
        author = "illumine",
        category = ScriptCategory.Fishing,
        version = "0.0.1"
)
public class SimpleBarb3TickFishingScript extends AbstractScript {
    private long lastTime = 0;

    private static final String FISHING_SPOT_NAME = "Fishing spot";
    // Prefer first-option on specific barbarian spot IDs over action text
    private static final int[] BARB_SPOT_IDS = new int[]{1542, 7323};
    private static final String HERB_NAME = "Guam leaf";
    private static final String TAR_NAME = "Swamp tar";

    // Fishing animations observed across clients/variants
    private static final List<Integer> FISHING_ANIMATIONS = Arrays.asList(622, 623, 8193, 8194, 9350);

    // After we mix herb+tar, we want to click the spot during the mix animation window.
    private long lastMixAtMs = 0L;
    private Tile targetSpotTile = null;
    private org.powbot.api.script.paint.Paint overlay;
    private enum NextAction { CLICK_SPOT, SELECT_TAR, COMBINE_HERB, DROP_ONE }

    // Simple poll-driven scheduling
    private NextAction nextAction = NextAction.CLICK_SPOT;
    private boolean dropPendingSameTick = false;
    private long actionGateGT = -1L;
    private String lastSpotSource = "";
    private long tickCount = 0L;
    private volatile boolean preloadedSelection = false;

    @Override
    public void onStart() {
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] starting");
        }
        try {
            clearPaints();
        } catch (Exception ignored) { }
        overlay = org.powbot.api.script.paint.PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Fishing)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Agility)
                .trackSkill(org.powbot.api.rt4.walking.model.Skill.Strength)
                .build();
        addPaint(overlay);
        // Initialize tick cycle
        lastMixAtMs = 0L;
        targetSpotTile = null;
        nextAction = NextAction.CLICK_SPOT;
        dropPendingSameTick = false;
        actionGateGT = -1L;
        tickCount = 0L;
        dbgSched("init", "initialized");
        preloadedSelection = false;
    }

    @Override
    public void onStop() {
        if (getLog() != null) {
            getLog().info("[SimpleBarb3T] stopped");
        }
        lastMixAtMs = 0L;
        targetSpotTile = null;
        nextAction = NextAction.CLICK_SPOT;
        dropPendingSameTick = false;
        actionGateGT = -1L;
        try {
            clearPaints();
        } catch (Exception ignored) { }
        preloadedSelection = false;
    }

    @Override
    public void poll() {
        // Supplies check; skip if unavailable
        if (!hasItem(HERB_NAME) || !hasItem(TAR_NAME)) {
            return;
        }

        switch (nextAction) {
            case CLICK_SPOT: {
                // Cancel any selected item before clicking
                Item sel = Inventory.selectedItem();
                if (sel != null && sel.valid()) {
                    sel.interact("Cancel");
                }
                dbgExec("poll", "t=" + tickCount + " | click spot: attempt");
                boolean ok = clickFishingSpot();
                dbgExec("poll", "t=" + tickCount + " | click spot: " + (ok ? "success" : "failed"));
                actionGateGT = tickCount; // only one attempt this GT
                if (ok) {
                    nextAction = NextAction.SELECT_TAR;
                }
                return;
            }
            case SELECT_TAR: {
                // One attempt per GT per action: require tickCount > actionGateGT
                if (tickCount <= actionGateGT) {
                    return;
                }
                dbgExec("poll", "t=" + tickCount + " | select tar: attempt");
                Item tar = Inventory.stream().name(TAR_NAME).first();
                boolean ok = tar != null && tar.valid() && (tar.click());
                dbgExec("poll", "t=" + tickCount + " | select tar: " + ok);
                actionGateGT = tickCount;
                if (ok) {
                    nextAction = NextAction.COMBINE_HERB;
                }
                return;
            }
            case COMBINE_HERB: {
                // One attempt per GT per action: require tickCount > actionGateGT
                if (tickCount <= actionGateGT) {
                    return;
                }
                dbgExec("poll", "t=" + tickCount + " | combine herb: attempt");
                Item herb = Inventory.stream().name(HERB_NAME).first();
                boolean ok = herb != null && herb.valid() && herb.click();
                dbgExec("poll", "t=" + tickCount + " | combine herb: " + ok);
                actionGateGT = tickCount;
                if (ok) {
                    nextAction = NextAction.DROP_ONE;
                    dropPendingSameTick = true; // perform on next poll, same GT
                }
                return;
            }
            case DROP_ONE: {
                boolean ok = dropOneLeapingFish();
                dbgExec("poll", "t=" + tickCount + " | drop one leaping=" + ok);
                actionGateGT = tickCount; // ensure next action waits until next GT
                nextAction = NextAction.CLICK_SPOT;
                return;
            }
        }
    }

    @Subscribe
    public void onTick(TickEvent tick) {
        tickCount++;
    }

    private boolean mixHerbAndTar() {
        Item herb = Inventory.stream().name(HERB_NAME).first();
        if (herb == null || !herb.valid()) {
            // Try grimy variant if present
            Item grimy = Inventory.stream().name("Grimy " + HERB_NAME).first();
            if (grimy != null && grimy.valid()) {
                return grimy.interact("Clean");
            }
            return false;
        }
        Item tar = Inventory.stream().name(TAR_NAME).first();
        if (tar == null || !tar.valid()) {
            return false;
        }
        // Ensure no item is pre-selected that could interfere
        Item selected = Inventory.selectedItem();
        if (selected != null && selected.valid()) {
            selected.interact("Cancel");
        }
        boolean ok = herb.useOn(tar);
        dbgExec("mix", "useOn=" + ok);
        return ok;
    }

    // Preload selection by left-clicking the herb on the wait tick
    private boolean preloadHerbSelection() {
        Item herb = Inventory.stream().name(HERB_NAME).first();
        if (herb != null && herb.valid()) {
            if (herb.click()) {
                return true;
            }
            return herb.interact("Use");
        }
        return false;
    }

    // Perform combine preferring preloaded left-click path (herb selected, click tar)
    private boolean performCombine() {
        Item selected = Inventory.selectedItem();
        if (preloadedSelection && selected != null && selected.valid()) {
            String selName = selected.name();
            if (selName != null && selName.equalsIgnoreCase(HERB_NAME)) {
                Item tar = Inventory.stream().name(TAR_NAME).first();
                if (tar != null && tar.valid()) {
                    if (tar.click()) {
                        dbgExec("combine", "left-click tar with selected herb");
                        return true;
                    }
                    boolean ok = selected.useOn(tar);
                    dbgExec("combine", "fallback useOn with selected herb=" + ok);
                    return ok;
                }
            }
        }
        boolean ok = mixHerbAndTar();
        dbgExec("combine", "fallback mixHerbAndTar=" + ok);
        return ok;
    }

    private boolean clickFishingSpot() {
        Npc spot = findSpotAtTargetOrNearest();
        if (spot == null || !spot.valid()) {
            logOnce("spot", "No fishing spot found");
            return false;
        }
        // Lock/refresh the target to this spot's tile
        targetSpotTile = spot.tile();
        // Use the first available action on this NPC (equivalent to NPC_FIRST_OPTION)
        String action = null;
        try {
            List<String> actions = spot.actions();
            if (actions != null && !actions.isEmpty()) {
                action = actions.get(0);
            }
        } catch (Exception ignored) {}
        if (action == null || action.isEmpty()) {
            action = "Use-rod"; // fallback
        }
        dbgExec("clicking_spot", "id=" + spot.id() + ", source=" + lastSpotSource + ", tile=" + targetSpotTile + ", action=" + action);
        boolean ok = spot.interact(action);
        if (!ok) {
            dbgExec("click", "interact returned false");
        }
        return ok;
    }

    private Npc findSpotAtTargetOrNearest() {
        if (targetSpotTile != null) {
            Npc locked = Npcs.stream()
                    .id(BARB_SPOT_IDS)
                    .filtered(n -> n != null && n.valid() && targetSpotTile.equals(n.tile()))
                    .first();
            if (locked != null && locked.valid()) {
                lastSpotSource = "locked";
                return locked;
            }
        }

        Npc nearest = Npcs.stream()
                .id(BARB_SPOT_IDS)
                .nearest()
                .first();
        if (nearest != null && nearest.valid()) {
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
        return item != null && item.valid();
    }

    private boolean isFishingAnimation(int animationId) {
        return FISHING_ANIMATIONS.contains(animationId);
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
        Item fish = Inventory.stream().nameContains("Leaping").first();
        if (fish.valid()) {
            fish.interact("Drop");
//            boolean ok = fish.interact("Drop");
//            dbgExec("drop", "one leaping fish: " + ok);
            return true;
        }
        return false;
    }

    public static void main(String[] args) {
        new SimpleBarb3TickFishingScript().startScript();
    }
}
