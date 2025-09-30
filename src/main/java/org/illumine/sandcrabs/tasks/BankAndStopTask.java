package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Bank;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Movement;
import org.powbot.api.rt4.Player;

public class BankAndStopTask extends SandCrabsTask {

    private static final String STATUS = "Restocking food";

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

        Movement.moveTo(bankTile);
        Condition.wait(() -> {
            Player current = local();
            return current != null && bankTile.distanceTo(current) <= 5.0;
        }, 200, 30);

        // Try to open the bank when it's in viewport
        if (Bank.inViewport()) {
            Condition.wait(Bank::open, 100, 30);
        }

        // If still not open, step again toward the bank tile and retry
        if (!Bank.opened()) {
            Movement.step(bankTile);
            Condition.wait(Bank::inViewport, 150, 20);
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
        if (invFull && !script.hasRequiredFoodInInventory()) {
            Bank.depositInventory();
            Condition.wait(() -> (int) Inventory.stream().count() < maxSlots, 100, 20);
        }

        // Withdraw all configured food
        String foodName = script.getConfiguredFoodName();
        if (foodName != null && !foodName.isEmpty()) {
            Bank.withdraw(foodName, Bank.Amount.ALL);
            Condition.wait(script::hasRequiredFoodInInventory, 100, 20);
        }

        // If we still don't have food in inventory after withdraw, stop the script (none in bank)
        if (!script.hasRequiredFoodInInventory() && script.getController() != null) {
            script.getController().stop();
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
