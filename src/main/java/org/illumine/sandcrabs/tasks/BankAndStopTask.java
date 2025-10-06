package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Tile;
import org.powbot.api.rt4.*;

public class BankAndStopTask extends SandCrabsTask {

    private static final String STATUS = "Restocking food";

    public BankAndStopTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        if (!context.config().isUseFood()) {
            return false;
        }
        return !context.hasRequiredFoodInInventory();
    }

    @Override
    public void run() {
        Tile bankTile = context.config().getBankTile();
        Player current = Players.local();

        Movement.moveTo(bankTile);
        Condition.wait(() -> bankTile.distanceTo(current) <= 5, 200, 30);

        // Try to open the bank when it's in viewport
        if (Bank.inViewport()) {
            Condition.wait(Bank::open, 100, 30);
        }

        if (!Bank.opened()) {
            return;
        }

        // Only deposit if inventory is full and contains no food (safety)
        // Avoid dumping partial inventories unnecessarily.
        final int maxSlots = 28;
        int occupied = (int) Inventory.stream().count();
        boolean invFull = occupied >= maxSlots;
        if (invFull && !context.hasRequiredFoodInInventory()) {
            Bank.depositInventory();
            Condition.wait(() -> (int) Inventory.stream().count() < maxSlots, 100, 20);
        }

        // Withdraw all configured food
        String foodName = context.config().getFoodName();
        if (foodName != null && !foodName.isEmpty()) {
            Bank.withdraw(foodName, Integer.MAX_VALUE);
            Condition.wait(context::hasRequiredFoodInInventory, 100, 20);
        }

        // If we still don't have food in inventory after withdraw, stop the script (none in bank)
        if (!context.hasRequiredFoodInInventory()) {
            script.getController().stop();
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
