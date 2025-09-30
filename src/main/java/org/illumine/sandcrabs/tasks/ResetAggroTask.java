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

        Condition.sleep(Random.nextInt(600, 901));

        script.maybeEnableRun();
        Movement.moveTo(camp);
        Condition.wait(() -> isOnTile(camp), 200, 30);
        script.rollNextNoCombatThreshold();
    }

    @Override
    public String status() {
        return STATUS;
    }
}
