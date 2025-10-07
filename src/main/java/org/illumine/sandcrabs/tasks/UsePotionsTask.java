package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.rt4.Bank;

public class UsePotionsTask extends SandCrabsTask {

    private static final String STATUS = "Drinking potions";

    public UsePotionsTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        if (!context.potionService().shouldUsePotions()) {
            return false;
        }
        if (Bank.opened()) {
            return false;
        }
        return context.combatMonitor().isInCombat();
    }

    @Override
    public void run() {
        context.potionService().drinkIfNeeded();
    }

    @Override
    public String status() {
        return STATUS;
    }
}

