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

        Movement.moveTo(bankTile);

        if (!Bank.inViewport()) {
            Camera.turnTo(Bank.nearest());
        }

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
        if (Inventory.isFull() && !context.hasRequiredFoodInInventory()) {
            Bank.depositInventory();
            Condition.wait(() -> (int) Inventory.stream().count() < maxSlots, 100, 20);
        }

        // Withdraw all configured food
        String foodName = context.config().getFoodName();

        if (foodName == null || foodName.isEmpty()) {
            script.setCurrentStatus("Configuration for food name is empty! Stopping.");
            return;
        }

        Item foodBank = Bank.stream().name(foodName).first();
        if (foodBank.valid()) {
            Bank.withdraw(foodName, Integer.MAX_VALUE);
            Condition.wait(context::hasRequiredFoodInInventory, 100, 20);
        } else {
            script.setCurrentStatus(foodName + " not found in Bank. Stopping.");
            script.getController().stop();
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
