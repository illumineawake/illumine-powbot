package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Player;

public class BankAndStopTask extends SandCrabsTask {

    private static final String STATUS = "Stopping at bank";

    public BankAndStopTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        if (!script.isUseFoodEnabled()) {
            return false;
        }
        return !script.hasRequiredFoodInInventory();
    }

    @Override
    public void run() {
        Tile bankTile = script.getShoreBankTile();
        Player player = local();
        if (bankTile == null || player == null) {
            return;
        }

        script.maybeEnableRun();
        Movement.moveTo(bankTile);
        Condition.wait(() -> {
            Player current = local();
            return current != null && bankTile.distanceTo(current) <= 5.0;
        }, 200, 30);

        if (script.getController() != null) {
            script.getController().stop();
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
