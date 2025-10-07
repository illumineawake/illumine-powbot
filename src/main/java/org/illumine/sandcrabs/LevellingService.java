package org.illumine.sandcrabs;

import org.powbot.api.rt4.Combat;
import org.powbot.api.rt4.Skills;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralises levelling decisions, including mode-specific target selection and
 * presentation of the current training status for the paint overlay.
 */
public class LevellingService {

    private static final class SkillTarget {
        final int attack;
        final int strength;
        final int defence;
        final int ranged;
        final int magic;

        SkillTarget(int attack, int strength, int defence, int ranged, int magic) {
            this.attack = attack;
            this.strength = strength;
            this.defence = defence;
            this.ranged = ranged;
            this.magic = magic;
        }

        int forSkill(Skill s) {
            if (s == Skill.Attack) return attack;
            if (s == Skill.Strength) return strength;
            if (s == Skill.Defence) return defence;
            return 0;
        }
    }

    private static final List<SkillTarget> OPTIMAL_TARGETS = List.of(
            new SkillTarget(10, 10, 10, 1, 1),
            new SkillTarget(30, 30, 30, 40, 35),
            new SkillTarget(30, 35, 30, 40, 35),
            new SkillTarget(40, 35, 30, 40, 35),
            new SkillTarget(40, 54, 30, 40, 35),
            new SkillTarget(50, 58, 30, 40, 35),
            new SkillTarget(60, 58, 60, 40, 35),
            new SkillTarget(60, 60, 60, 60, 59),
            new SkillTarget(60, 70, 60, 60, 59),
            new SkillTarget(70, 70, 70, 70, 70),
            new SkillTarget(70, 99, 70, 70, 70),
            new SkillTarget(99, 99, 70, 80, 80),
            new SkillTarget(99, 99, 99, 99, 99)
    );

    private SandCrabsConfig config;
    private final SandCrabsState state;

    public LevellingService(SandCrabsConfig config, SandCrabsState state) {
        this.config = config;
        this.state = state;
    }

    public void updateConfig(SandCrabsConfig config) {
        this.config = config;
    }

    public boolean isLevellingEnabled() {
        return config.isLevellingEnabled();
    }

    public String getLevellingMode() {
        return config.getLevellingMode();
    }

    public int getMaxFor(Skill skill) {
        if (skill == Skill.Attack) return config.getMaxAttackLevel();
        if (skill == Skill.Strength) return config.getMaxStrengthLevel();
        if (skill == Skill.Defence) return config.getMaxDefenceLevel();
        return SandCrabsScript.MAX_LEVEL;
    }

    public int realLevel(Skill skill) {
        return Math.max(SandCrabsScript.MIN_LEVEL, Math.min(SandCrabsScript.MAX_LEVEL, Skills.realLevel(skill)));
    }

    public boolean reachedLimit(Skill skill) {
        return realLevel(skill) >= getMaxFor(skill);
    }

    public boolean allGoalsReached() {
        return reachedLimit(Skill.Attack) && reachedLimit(Skill.Strength) && reachedLimit(Skill.Defence);
    }

    public Skill getInitialLockedSkill() {
        return state.getInitialLockedSkill();
    }

    public void setInitialLockedSkill(Skill skill) {
        state.setInitialLockedSkill(skill);
    }

    public int getKeepWithin() {
        return config.getKeepWithinLevels();
    }

    public void setCurrentTrainingSkill(Skill skill) {
        state.setCurrentTrainingSkill(skill);
    }

    public Skill getCurrentTrainingSkill() {
        return state.getCurrentTrainingSkill();
    }

    public String trainingStatus() {
        if (!isLevellingEnabled()) {
            return "Off";
        }
        if (allGoalsReached()) {
            return "All goals reached";
        }
        Skill current = state.getCurrentTrainingSkill();
        if (current == null) {
            return "Training Pending";
        }
        String name = (current == Skill.Attack ? "Attack" : current == Skill.Strength ? "Strength" : current == Skill.Defence ? "Defence" : current.name());
        int targetLevel = nextTargetLevel(current);
        if (targetLevel <= 0) {
            return "Training " + name;
        }
        return "Training " + name + " to " + targetLevel;
    }

    public String optimalTargetStatus() {
        if (!isLevellingEnabled() || !SandCrabsScript.MODE_OPTIMAL.equals(getLevellingMode())) {
            return "";
        }
        SkillTarget target = currentOptimalTarget();
        if (target == null) {
            return "Milestones complete";
        }
        return "A/S/D: " + target.attack + "/" + target.strength + "/" + target.defence;
    }

    public Combat.Style styleFor(Skill skill) {
        if (skill == Skill.Attack) return Combat.Style.ACCURATE;
        if (skill == Skill.Strength) return Combat.Style.AGGRESSIVE;
        if (skill == Skill.Defence) return Combat.Style.DEFENSIVE;
        return Combat.Style.CONTROLLED;
    }

    public Skill mapStyleToSkill(Combat.Style style) {
        if (style == null) {
            return null;
        }
        switch (style) {
            case ACCURATE:
                return Skill.Attack;
            case AGGRESSIVE:
                return Skill.Strength;
            case DEFENSIVE:
                return Skill.Defence;
            default:
                return null;
        }
    }

    public int nextTargetLevel(Skill skill) {
        if (!isLevellingEnabled() || skill == null) {
            return 0;
        }
        if (allGoalsReached()) {
            return realLevel(skill);
        }

        String mode = getLevellingMode();
        if (SandCrabsScript.MODE_ON_LIMIT.equals(mode)) {
            return getMaxFor(skill);
        }

        if (SandCrabsScript.MODE_OPTIMAL.equals(mode)) {
            SkillTarget target = currentOptimalTarget();
            if (target == null) {
                return getMaxFor(skill);
            }
            int cap = getMaxFor(skill);
            return Math.min(cap, target.forSkill(skill));
        }

        // MODE_WITHIN_RANGE
        int current = realLevel(skill);
        int ownLimit = getMaxFor(skill);
        List<Skill> others = new ArrayList<>();
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (s != skill && !reachedLimit(s)) {
                others.add(s);
            }
        }

        if (others.isEmpty()) {
            return ownLimit;
        }

        int highestOther = 0;
        int minThreshold = Integer.MAX_VALUE;
        for (Skill s : others) {
            int level = realLevel(s);
            if (level > highestOther) {
                highestOther = level;
            }
            int threshold = level + getKeepWithin();
            if (threshold < minThreshold) {
                minThreshold = threshold;
            }
        }

        boolean isTop = current >= highestOther;
        if (isTop) {
            return Math.min(ownLimit, minThreshold);
        }
        return Math.min(ownLimit, highestOther);
    }

    public List<Skill> computeCandidates() {
        ArrayList<Skill> eligible = new ArrayList<>();
        if (!reachedLimit(Skill.Attack)) eligible.add(Skill.Attack);
        if (!reachedLimit(Skill.Strength)) eligible.add(Skill.Strength);
        if (!reachedLimit(Skill.Defence)) eligible.add(Skill.Defence);

        if (eligible.isEmpty()) {
            return eligible;
        }

        String mode = getLevellingMode();
        if (SandCrabsScript.MODE_ON_LIMIT.equals(mode)) {
            Skill lock = getInitialLockedSkill();
            if (lock != null && !reachedLimit(lock)) {
                ArrayList<Skill> only = new ArrayList<>();
                only.add(lock);
                return only;
            }
            return sortByPriority(eligible);
        }

        if (SandCrabsScript.MODE_OPTIMAL.equals(mode)) {
            return new ArrayList<>(computeOptimalCandidates());
        }

        int max = Integer.MIN_VALUE;
        for (Skill s : eligible) {
            max = Math.max(max, realLevel(s));
        }
        final int within = getKeepWithin();
        ArrayList<Skill> under = new ArrayList<>();
        for (Skill s : eligible) {
            int level = realLevel(s);
            if (max - level >= within) {
                under.add(s);
            }
        }

        if (!under.isEmpty()) {
            under.sort(Comparator.comparingInt(this::realLevel)
                    .thenComparing(this::priorityIndex));
            return under;
        }

        ArrayList<Skill> result = new ArrayList<>();
        Skill current = state.getCurrentTrainingSkill();
        if (current != null && eligible.contains(current)) {
            result.add(current);
        }
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (eligible.contains(s) && (current == null || s != current)) {
                result.add(s);
            }
        }
        return result;
    }

    public List<Skill> computeOptimalCandidates() {
        ArrayList<Skill> result = new ArrayList<>();
        SkillTarget target = currentOptimalTarget();
        if (target != null) {
            for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
                if (reachedLimit(s)) {
                    continue;
                }
                int targetLevel = target.forSkill(s);
                if (realLevel(s) < targetLevel) {
                    result.add(s);
                    break;
                }
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        for (Skill s : new Skill[]{Skill.Attack, Skill.Strength, Skill.Defence}) {
            if (!reachedLimit(s)) {
                result.add(s);
            }
        }
        return result;
    }

    private SkillTarget currentOptimalTarget() {
        for (SkillTarget target : OPTIMAL_TARGETS) {
            int attack = Math.min(target.attack, getMaxFor(Skill.Attack));
            int strength = Math.min(target.strength, getMaxFor(Skill.Strength));
            int defence = Math.min(target.defence, getMaxFor(Skill.Defence));
            boolean met = realLevel(Skill.Attack) >= attack
                    && realLevel(Skill.Strength) >= strength
                    && realLevel(Skill.Defence) >= defence;
            if (!met) {
                return new SkillTarget(attack, strength, defence, target.ranged, target.magic);
            }
        }
        return null;
    }

    private List<Skill> sortByPriority(List<Skill> list) {
        ArrayList<Skill> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(this::priorityIndex));
        return copy;
    }

    private int priorityIndex(Skill skill) {
        if (skill == Skill.Attack) return 0;
        if (skill == Skill.Strength) return 1;
        if (skill == Skill.Defence) return 2;
        return 3;
    }
}

