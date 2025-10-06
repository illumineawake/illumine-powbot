package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Players;

import java.util.List;

public class TravelToSpotTask extends SandCrabsTask {

    private static final String STATUS = "Travelling to Sand Crabs";

    public TravelToSpotTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        List<Tile> eligible = context.spotManager().eligibleSpots();
        if (eligible.isEmpty()) {
            return context.spotManager().canAttemptWorldHop();
        }

        Tile currentTarget = context.spotManager().getCurrentSpot();
        if (currentTarget == null) {
            return true;
        }

        // Consider a spot "crashed" only if another player remains ~10s
        if (context.spotManager().isSpotCrashed(currentTarget)) {
            return true;
        }

        return !Players.local().tile().equals(currentTarget);
    }

    @Override
    public void run() {
        List<Tile> eligible = context.spotManager().eligibleSpots();
        if (eligible.isEmpty()) {
            context.spotManager().hopToNextWorld();
            return;
        }

        Tile currentTarget = context.spotManager().getCurrentSpot();
        Tile currentTile = Players.local().tile();
        if (currentTarget == null || !eligible.contains(currentTarget)) {
            // Prefer the nearest eligible spot to the local player instead of a random one
            Tile nearest = null;
            double best = Double.MAX_VALUE;
            for (Tile t : eligible) {
                double d = currentTile.distanceTo(t);
                if (d < best) {
                    best = d;
                    nearest = t;
                }
            }
            if (nearest == null) {
                int index = Random.nextInt(0, eligible.size());
                nearest = eligible.get(index);
            }
            currentTarget = nearest;
            context.spotManager().setCurrentSpot(currentTarget);
        }

        if (currentTarget == null || currentTile.equals(currentTarget)) {
            return;
        }

        Movement.moveTo(currentTarget);
        final Tile target = currentTarget;
        boolean arrived = Condition.wait(() -> Players.local().tile().equals(target), 200, 25);
        if (!arrived && !Players.local().tile().equals(target)) {
            Movement.step(target);
            Condition.wait(() -> Players.local().tile().equals(target), 200, 10);
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
