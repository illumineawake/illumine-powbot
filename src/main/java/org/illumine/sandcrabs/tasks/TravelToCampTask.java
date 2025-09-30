package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;

import java.util.List;

public class TravelToCampTask extends SandCrabsTask {

    private static final String STATUS = "Travelling to camp";

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

        if (script.isCampTileOccupied(currentTarget)) {
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
            int index = Random.nextInt(0, eligible.size());
            currentTarget = eligible.get(index);
            script.setCurrentCampTile(currentTarget);
        }

        if (currentTarget == null || isOnTile(currentTarget)) {
            return;
        }

        script.maybeEnableRun();
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
