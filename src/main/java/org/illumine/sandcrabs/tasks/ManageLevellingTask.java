package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.List;

public class ManageLevellingTask extends SandCrabsTask {

    public ManageLevellingTask(SandCrabsScript script, SandCrabsContext context) {
        super(script, context);
    }

    @Override
    public boolean validate() {
        if (!context.levellingService().isLevellingEnabled()) {
            return false;
        }

        if (context.levellingService().allGoalsReached()) {
            return true; // Stop the script in run()
        }

        List<Skill> candidates = context.levellingService().computeCandidates();
        if (candidates.isEmpty()) {
            return true; // Nothing trainable -> stop
        }

        Skill desired = candidates.get(0);
        Combat.Style current = Combat.style();
        Skill currentSkill = context.levellingService().mapStyleToSkill(current);
        if (context.levellingService().getCurrentTrainingSkill() == null) {
            return true; // Initialize displayed training target
        }
        // Require action when our current style does not match desired target
        return currentSkill != desired;
    }

    @Override
    public void run() {
        if (context.levellingService().allGoalsReached()) {
            script.setCurrentStatus("All goals reached — stopping");
            script.getController().stop();
            return;
        }

        List<Skill> candidates = context.levellingService().computeCandidates();
        if (candidates.isEmpty()) {
            script.setCurrentStatus("No trainable skills available — stopping");
            script.getController().stop();
            return;
        }

        for (Skill s : candidates) {
            Combat.Style target = context.levellingService().styleFor(s);
            Combat.Style current = Combat.style();
            if (current == target) {
                context.levellingService().setCurrentTrainingSkill(s);
                return;
            }
            boolean ok = Combat.style(target);
            if (ok) {
                context.levellingService().setCurrentTrainingSkill(s);
                return;
            }
        }

        // Could not set any required styles (not available for the current weapon)
        script.setCurrentStatus("Required combat styles unavailable — stopping");
        script.getController().stop();
    }

    @Override
    public String status() {
        // Keep status terse; paint shows training target separately
        return "Managing levelling";
    }

}
