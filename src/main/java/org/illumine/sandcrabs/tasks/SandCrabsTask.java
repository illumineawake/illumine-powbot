package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabSpots;
import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.illumine.taskscript.Task;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.ArrayList;
import java.util.List;

public abstract class SandCrabsTask implements Task {

    protected final SandCrabsScript script;
    protected final SandCrabsContext context;

    protected SandCrabsTask(SandCrabsScript script, SandCrabsContext context) {
        this.script = script;
        this.context = context;
    }

    protected boolean shouldPrioritizeMoreThanThree() {
        try {
            if (context == null || context.levellingService() == null) {
                return false;
            }
            int defence = context.levellingService().realLevel(Skill.Defence);
            if (defence >= 30) {
                return true;
            }
            Skill current = context.levellingService().getCurrentTrainingSkill();
            if (current == null) {
                return false;
            }
            int curLevel = context.levellingService().realLevel(current);
            return curLevel >= 50;
        } catch (Exception ignored) {
            return false;
        }
    }

    protected List<Tile> spotsMatchingCrabPreference(List<Tile> eligible, boolean moreThanThree) {
        List<Tile> matches = new ArrayList<>();
        if (eligible == null) {
            return matches;
        }
        for (Tile tile : eligible) {
            if (tile == null) {
                continue;
            }
            boolean match = moreThanThree
                    ? SandCrabSpots.hasMoreThanThreeCrabs(tile)
                    : SandCrabSpots.isThreeCrabSpot(tile);
            if (match) {
                matches.add(tile);
            }
        }
        return matches;
    }

    protected List<Tile> filterByCrabPreference(List<Tile> eligible, boolean preferMoreThanThree) {
        if (eligible == null || eligible.isEmpty()) {
            return eligible;
        }
        List<Tile> preferred = spotsMatchingCrabPreference(eligible, preferMoreThanThree);
        return preferred.isEmpty() ? eligible : preferred;
    }

    protected Tile chooseNearest(List<Tile> tiles, Tile from) {
        if (tiles == null || tiles.isEmpty()) {
            return null;
        }
        Tile nearest = null;
        double best = Double.MAX_VALUE;
        for (Tile tile : tiles) {
            if (tile == null) {
                continue;
            }
            double distance = from.distanceTo(tile);
            if (distance < best) {
                best = distance;
                nearest = tile;
            }
        }
        if (nearest == null) {
            int index = Random.nextInt(0, tiles.size());
            nearest = tiles.get(index);
        }
        return nearest;
    }
}
