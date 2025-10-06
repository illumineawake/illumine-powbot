package org.illumine.sandcrabs;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.rt4.Npc;
import org.powbot.api.rt4.Npcs;
import org.powbot.api.rt4.Player;
import org.powbot.api.rt4.Players;
import org.powbot.api.rt4.Skills;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.logging.Logger;

/**
 * Handles combat-related bookkeeping such as XP gain tracking, combat timers,
 * and identifying dormant Sand Crabs nearby the reserved spot.
 */
public class CombatMonitor {

    private static final Skill[] TRACKED_COMBAT_SKILLS = new Skill[]{
            Skill.Attack,
            Skill.Strength,
            Skill.Defence,
            Skill.Ranged,
            Skill.Magic,
            Skill.Hitpoints
    };

    private final Logger logger;
    private SandCrabsConfig config;
    private final SandCrabsState state;

    public CombatMonitor(Logger logger, SandCrabsConfig config, SandCrabsState state) {
        this.logger = logger;
        this.config = config;
        this.state = state;
    }

    public void updateConfig(SandCrabsConfig config) {
        this.config = config;
    }

    public int getCurrentEatThresholdPercent() {
        return state.getEatThresholdPercent();
    }

    public void rollNextEatThreshold() {
        int next = Random.nextInt(config.getEatMinPercent(), config.getEatMaxPercent() + 1);
        state.setEatThresholdPercent(next);
    }

    public long getCurrentNoCombatThresholdMillis() {
        return state.getNoCombatThresholdMillis();
    }

    public void rollNextNoCombatThreshold() {
        int seconds = Random.nextInt(config.getMinNoCombatSeconds(), config.getMaxNoCombatSeconds() + 1);
        state.setNoCombatThresholdMillis(seconds * 1000L);
    }

    public long minTrackedSkillExpDelta() {
        long min = Long.MAX_VALUE;
        for (Skill skill : TRACKED_COMBAT_SKILLS) {
            long delta = Skills.timeSinceExpGained(skill);
            if (delta < min) {
                min = delta;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    public boolean isDormantCrabNearby() {
        Player local = Players.local();
        Npc dormant = Npcs.stream()
                .name("Sandy rocks")
                .within(local, 2)
                .first();

        if (dormant.valid()) {
            state.setLastDormantSeenTime(System.currentTimeMillis());
            return true;
        }

        long sinceSeen = System.currentTimeMillis() - state.getLastDormantSeenTime();
        if (!state.isDormantWarningShown() && sinceSeen >= config.getDormantWarningDelayMillis()) {
            state.setDormantWarningShown(true);
            if (logger != null) {
                logger.warning("No 'Sandy rocks' NPCs detected nearby for an extended period. Check naming or aggro state.");
            }
        }
        return false;
    }

    public boolean isInCombat() {
        Player player = Players.local();
        try {
            return player.interacting().valid() || player.healthBarVisible();
        } catch (Exception ignored) {
            return player.interacting().valid();
        }
    }

    public boolean waitUntilOutOfCombat() {
        if (!isInCombat()) {
            return true;
        }
        return Condition.wait(() -> !isInCombat(), 200, 150);
    }
}

