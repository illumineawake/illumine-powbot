package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ManageLevellingTask extends SandCrabsTask {

    public ManageLevellingTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        if (!script.isLevellingEnabled()) {
            return false;
        }

        if (script.allGoalsReached()) {
            return true; // Stop the script in run()
        }

        List<Skill> candidates = computeCandidates();
        if (candidates.isEmpty()) {
            return true; // Nothing trainable -> stop
        }

        Skill desired = candidates.get(0);
        Combat.Style current = Combat.style();
        Skill currentSkill = script.mapStyleToSkill(current);
        if (script.getCurrentTrainingSkill() == null) {
            return true; // Initialize displayed training target
        }
        // Require action when our current style does not match desired target
        return currentSkill != desired;
    }

    @Override
    public void run() {
        if (script.allGoalsReached()) {
            script.setCurrentStatus("All goals reached — stopping");
            if (script.getController() != null) script.getController().stop();
            return;
        }

        List<Skill> candidates = computeCandidates();
        if (candidates.isEmpty()) {
            script.setCurrentStatus("No trainable skills available — stopping");
            if (script.getController() != null) script.getController().stop();
            return;
        }

        for (Skill s : candidates) {
            Combat.Style target = script.styleFor(s);
            Combat.Style current = Combat.style();
            if (current == target) {
                script.setCurrentTrainingSkill(s);
                return;
            }
            boolean ok = Combat.style(target);
            if (ok) {
                script.setCurrentTrainingSkill(s);
                return;
            }
        }

        // Could not set any required styles (not available for the current weapon)
        script.setCurrentStatus("Required combat styles unavailable — stopping");
        if (script.getController() != null) script.getController().stop();
    }

    @Override
    public String status() {
        // Keep status terse; paint shows training target separately
        return "Managing levelling";
    }

    private List<Skill> computeCandidates() {
        ArrayList<Skill> eligible = new ArrayList<>();
        if (!script.reachedLimit(Skill.Attack)) eligible.add(Skill.Attack);
        if (!script.reachedLimit(Skill.Strength)) eligible.add(Skill.Strength);
        if (!script.reachedLimit(Skill.Defence)) eligible.add(Skill.Defence);

        if (eligible.isEmpty()) return eligible;

        String mode = script.getLevellingMode();
        if (SandCrabsScript.MODE_ON_LIMIT.equals(mode)) {
            Skill lock = script.getInitialLockedSkill();
            if (lock != null && !script.reachedLimit(lock)) {
                ArrayList<Skill> only = new ArrayList<>();
                only.add(lock);
                return only;
            }
            return sortByPriority(eligible);
        }
        if (SandCrabsScript.MODE_OPTIMAL.equals(mode)) {
            return new ArrayList<>(script.computeOptimalCandidates());
        }

        // MODE_WITHIN_RANGE
        int max = Integer.MIN_VALUE;
        for (Skill s : eligible) {
            max = Math.max(max, script.realLevel(s));
        }
        final int within = script.getKeepWithin();
        ArrayList<Skill> under = new ArrayList<>();
        for (Skill s : eligible) {
            int lv = script.realLevel(s);
            if (max - lv >= within) {
                under.add(s);
            }
        }

        if (!under.isEmpty()) {
            under.sort(Comparator.comparingInt(script::realLevel)
                    .thenComparing(this::priorityIndex));
            return under;
        }

        ArrayList<Skill> result = new ArrayList<>();
        // Prefer to keep training the current target if still eligible
        Skill current = script.getCurrentTrainingSkill();
        if (current != null && eligible.contains(current)) {
            result.add(current);
        }
        // Append remaining by fixed priority
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (eligible.contains(s) && (current == null || s != current)) {
                result.add(s);
            }
        }
        return result;
    }

    private List<Skill> sortByPriority(List<Skill> list) {
        ArrayList<Skill> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(this::priorityIndex));
        return copy;
    }

    private int priorityIndex(Skill s) {
        if (s == Skill.Attack) return 0;
        if (s == Skill.Strength) return 1;
        if (s == Skill.Defence) return 2;
        return 3;
    }
}
