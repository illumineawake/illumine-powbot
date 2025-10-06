package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Item;

public class EatFoodTask extends SandCrabsTask {

    private static final String STATUS = "Eating";

    public EatFoodTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        if (!context.config().isUseFood()) {
            return false;
        }
        if (!context.hasRequiredFoodInInventory()) {
            return false;
        }
        int healthPercent = Combat.healthPercent();
        return healthPercent > 0 && healthPercent <= context.combatMonitor().getCurrentEatThresholdPercent();
    }

    @Override
    public void run() {
        int before = Combat.healthPercent();
        Item food = Inventory.stream()
                .filtered(item -> item != null && item.valid() && context.isConfiguredFood(item.name()))
                .first();

        if (food == null || !food.valid()) {
            return;
        }

        if (food.interact("Eat")) {
            Condition.wait(() -> Combat.healthPercent() > before, 200, 10);
            context.combatMonitor().rollNextEatThreshold();
            Condition.sleep(Random.nextInt(200, 400));
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
