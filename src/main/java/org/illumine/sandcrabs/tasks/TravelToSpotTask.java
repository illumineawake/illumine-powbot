package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
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

        // Apply simple prioritization filter (prefer >3 crabs when enabled)
        eligible = prioritizedEligible(eligible);

        Tile currentTarget = context.spotManager().getCurrentSpot();
        if (currentTarget == null || !contains(eligible, currentTarget)) {
            currentTarget = chooseNearest(eligible, Players.local().tile());
            context.spotManager().setCurrentSpot(currentTarget);
        }

        Movement.moveTo(currentTarget);
    }

    private List<Tile> prioritizedEligible(List<Tile> eligible) {
        return filterByCrabPreference(eligible, shouldPrioritizeMoreThanThree());
    }

    private boolean contains(List<Tile> tiles, Tile t) {
        for (Tile x : tiles) {
            if (x.equals(t)) return true;
        }
        return false;
    }

    @Override
    public String status() {
        return STATUS;
    }
}
