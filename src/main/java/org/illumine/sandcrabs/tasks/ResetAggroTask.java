package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.illumine.sandcrabs.SandCrabSpots;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Players;

import java.util.ArrayList;
import java.util.List;

public class ResetAggroTask extends SandCrabsTask {

    private static final String STATUS = "Resetting aggro";

    public ResetAggroTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        Tile spot = context.spotManager().getCurrentSpot();
        if (spot == null) {
            return false;
        }

        if (Players.local().tile().distanceTo(spot) > 10) {
            return false;
        }

        long elapsed = context.combatMonitor().minTrackedSkillExpDelta();
        if (elapsed < context.combatMonitor().getCurrentNoCombatThresholdMillis()) {
            return false;
        }

        return context.spotManager().hasNearbyDormantCrab();
    }

    @Override
    public void run() {
        Tile currentSpot = context.spotManager().getCurrentSpot();
        if (currentSpot == null) {
            return;
        }

        moveToResetPath(currentSpot);

        List<Tile> eligible = context.spotManager().eligibleSpots();
        Tile target = selectTargetSpot(currentSpot, eligible);
        if (target == null) {
            context.spotManager().hopToNextWorld();
            context.combatMonitor().rollNextNoCombatThreshold();
            return;
        }

        context.spotManager().setCurrentSpot(target);
        Movement.moveTo(target);
        context.combatMonitor().rollNextNoCombatThreshold();
    }

    @Override
    public String status() {
        return STATUS;
    }

    private void moveToResetPath(Tile spot) {
        Tile resetTile = context.config().getResetArea().getRandomTile();
        Movement.moveTo(resetTile);

        // Random pause before returning toward the previous spot
        Condition.sleep(Random.nextInt(600, 4000));

        // Walk back toward the previous spot, but stop within 7 tiles to reassess occupancy
        if (Players.local().tile().distanceTo(spot) > 7) {
            Movement.builder(spot)
                    .setWalkUntil(() -> Players.local().tile().distanceTo(spot) <= 7)
                    .move();
        }
    }

    private Tile selectTargetSpot(Tile previousSpot, List<Tile> eligible) {
        List<Tile> available = eligible == null ? new ArrayList<>() : new ArrayList<>(eligible);

        boolean preferMoreThanThree = shouldPrioritizeMoreThanThree();
        boolean currentIsThree = SandCrabSpots.isThreeCrabSpot(previousSpot);
        if (preferMoreThanThree && currentIsThree) {
            List<Tile> more = spotsMatchingCrabPreference(available, true);
            if (!more.isEmpty()) {
                return chooseNearest(more, Players.local().tile());
            }
        }

        if (!context.spotManager().isSpotOccupied(previousSpot)) {
            return previousSpot;
        }

        available.removeIf(t -> t != null && t.equals(previousSpot));
        if (available.isEmpty()) {
            return null;
        }

        return chooseNearest(available, Players.local().tile());
    }
}
