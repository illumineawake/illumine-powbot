package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Player;
import org.powbot.api.rt4.Players;

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
        Tile spot = context.spotManager().getCurrentSpot();
        if (spot == null) {
            return;
        }

        Tile resetTile = context.config().getResetArea().getRandomTile();
            Movement.moveTo(resetTile);
            Condition.wait(() ->
               context.config().getResetArea().contains(Players.local()), 200, 25);


        // Random pause before returning toward the previous spot
        Condition.sleep(Random.nextInt(600, 1000));

        // Walk back toward the previous spot, but stop within 7 tiles to reassess occupancy
            if (Players.local().tile().distanceTo(spot) > 7) {
                Movement.moveTo(spot);
                Condition.wait(() -> {
                    Player p = Players.local();
                    return p.tile().distanceTo(spot) <= 7;
                }, 200, 30);
            }

        // Reassess our last spot; if free, return to it. Otherwise try another free spot.
        boolean lastSpotFree = !context.spotManager().isSpotOccupied(spot);
        if (lastSpotFree) {
            Movement.moveTo(spot);
            Condition.wait(() -> Players.local().tile().equals(spot), 200, 30);
        } else {
            // Try another available spot; if none are free, follow hop logic
            List<Tile> eligible = context.spotManager().eligibleSpots();
            // Remove our previous spot from consideration since it's occupied
            eligible.removeIf(t -> t != null && t.equals(spot));

            if (eligible.isEmpty()) {
                // No free spots; rely on existing hop logic (combat-safe)
                context.spotManager().hopToNextWorld();
            } else {
                int idx = Random.nextInt(0, eligible.size());
                Tile nextSpot = eligible.get(idx);
                context.spotManager().setCurrentSpot(nextSpot);
                Movement.moveTo(nextSpot);
                final Tile target = nextSpot;
                boolean arrived = Condition.wait(() -> Players.local().tile().equals(target), 200, 25);
                if (!arrived && !Players.local().tile().equals(target)) {
                    Movement.step(target);
                    Condition.wait(() -> Players.local().tile().equals(target), 200, 10);
                }
            }
        }

        context.combatMonitor().rollNextNoCombatThreshold();
    }

    @Override
    public String status() {
        return STATUS;
    }
}
