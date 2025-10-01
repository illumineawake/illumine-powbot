package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;

import java.util.List;

public class TravelToCampTask extends SandCrabsTask {

    private static final String STATUS = "Travelling to Sand Crabs";

    public TravelToCampTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        if (local() == null) {
            return false;
        }

        List<Tile> eligible = script.eligibleCampTiles();
        if (eligible.isEmpty()) {
            return script.canAttemptWorldHop();
        }

        Tile currentTarget = script.getCurrentCampTile();
        if (currentTarget == null) {
            return true;
        }

        // Consider a camp "crashed" only if another player remains ~10s
        if (script.isCampTileCrashed(currentTarget)) {
            return true;
        }

        return !isOnTile(currentTarget);
    }

    @Override
    public void run() {
        List<Tile> eligible = script.eligibleCampTiles();
        if (eligible.isEmpty()) {
            script.hopToNextWorld();
            return;
        }

        Tile currentTarget = script.getCurrentCampTile();
        if (currentTarget == null || !eligible.contains(currentTarget)) {
            // Prefer the nearest eligible camp to the local player instead of a random one
            Tile me = localTile();
            Tile nearest = null;
            double best = Double.MAX_VALUE;
            for (Tile t : eligible) {
                double d = (me == null) ? Double.MAX_VALUE : me.distanceTo(t);
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
            script.setCurrentCampTile(currentTarget);
        }

        if (currentTarget == null || isOnTile(currentTarget)) {
            return;
        }

        Movement.moveTo(currentTarget);
        final Tile target = currentTarget;
        boolean arrived = Condition.wait(() -> isOnTile(target), 200, 25);
        if (!arrived && !isOnTile(target)) {
            Movement.step(target);
            Condition.wait(() -> isOnTile(target), 200, 10);
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
