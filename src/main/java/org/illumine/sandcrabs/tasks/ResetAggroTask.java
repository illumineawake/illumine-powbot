package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Player;

public class ResetAggroTask extends SandCrabsTask {

    private static final String STATUS = "Resetting aggro";

    public ResetAggroTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        Tile camp = script.getCurrentCampTile();
        Player player = local();
        if (camp == null || player == null) {
            return false;
        }

        if (player.tile() == null || player.tile().distanceTo(camp) > 10) {
            return false;
        }

        long elapsed = script.minTrackedSkillExpDelta();
        if (elapsed < script.getCurrentNoCombatThresholdMillis()) {
            return false;
        }

        return script.isDormantCrabNearby();
    }

    @Override
    public void run() {
        Tile camp = script.getCurrentCampTile();
        if (camp == null) {
            return;
        }

        Tile resetTile = script.getResetArea().getRandomTile();
        script.maybeEnableRun();
        if (resetTile != null) {
            Movement.moveTo(resetTile);
            Condition.wait(() -> {
                Player current = local();
                return current != null && script.getResetArea().contains(current);
            }, 200, 25);
        }

        // Brief pause before returning toward camp
        Condition.sleep(Random.nextInt(600, 901));

        // Walk back toward the previous camp, but stop within 7 tiles to reassess occupancy
        script.maybeEnableRun();
        if (local() != null && local().tile() != null) {
            if (local().tile().distanceTo(camp) > 7) {
                Movement.moveTo(camp);
                Condition.wait(() -> {
                    Player p = local();
                    return p != null && p.tile() != null && p.tile().distanceTo(camp) <= 7;
                }, 200, 30);
            }
        }

        // Reassess our last spot; if free, return to it. Otherwise try another free camp.
        boolean lastSpotFree = !script.isCampTileOccupied(camp);
        if (lastSpotFree) {
            script.maybeEnableRun();
            Movement.moveTo(camp);
            Condition.wait(() -> isOnTile(camp), 200, 30);
        } else {
            // Try another available camp; if none are free, follow hop logic
            java.util.List<Tile> eligible = script.eligibleCampTiles();
            // Remove our previous camp from consideration since it's occupied
            eligible.removeIf(t -> t != null && t.equals(camp));

            if (eligible.isEmpty()) {
                // No free spots; rely on existing hop logic (now combat-safe in script)
                script.hopToNextWorld();
            } else {
                int idx = Random.nextInt(0, eligible.size());
                Tile nextCamp = eligible.get(idx);
                script.setCurrentCampTile(nextCamp);
                script.maybeEnableRun();
                Movement.moveTo(nextCamp);
                final Tile target = nextCamp;
                boolean arrived = Condition.wait(() -> isOnTile(target), 200, 25);
                if (!arrived && !isOnTile(target)) {
                    Movement.step(target);
                    Condition.wait(() -> isOnTile(target), 200, 10);
                }
            }
        }

        script.rollNextNoCombatThreshold();
    }

    @Override
    public String status() {
        return STATUS;
    }
}
