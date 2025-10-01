package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.Npc;
import org.powbot.api.rt4.Npcs;
import org.powbot.api.rt4.Players;

public class AttackTask extends SandCrabsTask {

    private static final String STATUS = "Attacking Sand Crabs";

    public AttackTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        Tile camp = script.getCurrentCampTile();
        return camp != null && isOnTile(camp);
    }

    @Override
    public void run() {
        if (!Combat.autoRetaliate()) {
            Combat.autoRetaliate(true);
        }

        Tile camp = script.getCurrentCampTile();
        if (camp == null) {
            return;
        }

        // If we've been idle (no XP gain) for a short while and not in combat,
        // proactively attack an available crab nearby.
        if (!script.isInCombat()) {
            long noExpMs = script.minTrackedSkillExpDelta();
            long threshold = Math.max(2500, Math.min(6000, script.getCurrentNoCombatThresholdMillis() / 2));

            if (noExpMs >= threshold) {
                Npc target = Npcs.stream()
                        .name("Sand Crab")
                        .within(camp, 7)
                        .filtered(n -> n.valid()
                                && !n.interacting().valid()
                                && n.actions() != null
                                && n.actions().contains("Attack"))
                        .nearest()
                        .first();

                if (target != null && target.valid()) {
                    if (target.interact("Attack")) {
                        Condition.wait(() -> script.isInCombat() || Players.local().interacting().valid(), 200, 25);
                        Condition.sleep(Random.nextInt(150, 350));
                    }
                }
            }
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
