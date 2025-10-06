package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.*;

public class AttackTask extends SandCrabsTask {

    private static final String STATUS = "Attacking Sand Crabs";

    public AttackTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        Tile spot = context.spotManager().getCurrentSpot();
        return spot != null && Players.local().tile().equals(spot);
    }

    @Override
    public void run() {
        if (!Combat.autoRetaliate()) {
            Combat.autoRetaliate(true);
        }

        Tile spot = context.spotManager().getCurrentSpot();
        if (spot == null) {
            return;
        }

        // If we've been idle (no XP gain) for a short while and not in combat,
        // proactively attack an available crab nearby.
        if (!context.combatMonitor().isInCombat()) {
            long noExpMs = context.combatMonitor().minTrackedSkillExpDelta();
            long threshold = Math.max(2500, Math.min(6000, context.combatMonitor().getCurrentNoCombatThresholdMillis() / 2));

            if (noExpMs >= threshold) {
                Npc target = Npcs.stream()
                        .name("Sand Crab")
                        .within(spot, 7)
                        .filtered(n -> n.valid()
                                && !n.interacting().valid()
                                && n.actions().contains("Attack"))
                        .nearest()
                        .first();

                if (!target.valid()) {
                    return;
                }

                if (!target.inViewport()) {
                    Camera.turnTo(target);
                }

                if (target.interact("Attack")) {
                    Condition.wait(() -> context.combatMonitor().isInCombat() || Players.local().interacting().valid(), 200, 25);
                    Condition.sleep(Random.nextInt(150, 350));
                }
            }
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
