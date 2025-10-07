package org.illumine.sandcrabs;

import org.powbot.api.Tile;
import org.powbot.api.rt4.walking.model.Skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable runtime state shared across Sand Crabs tasks. It captures volatile data such as
 * the currently reserved spot, timing thresholds, and levelling progress so that each
 * component can operate without duplicating bookkeeping.
 */
public class SandCrabsState {

    private Tile currentSpotTile;
    private int eatThresholdPercent;
    private long noCombatThresholdMillis;
    private long lastWorldHopMillis;
    private long lastDormantSeenTime;
    private boolean dormantWarningShown;
    private final Map<String, Long> spotCrashFirstSeen = new HashMap<>();
    private Skill initialLockedSkill;
    private Skill currentTrainingSkill;

    public SandCrabsState(int initialEatThresholdPercent, long initialNoCombatThresholdMillis, long initialDormantSeenTime) {
        this.eatThresholdPercent = initialEatThresholdPercent;
        this.noCombatThresholdMillis = initialNoCombatThresholdMillis;
        this.lastDormantSeenTime = initialDormantSeenTime;
    }

    public Tile getCurrentSpotTile() {
        return currentSpotTile;
    }

    public void setCurrentSpotTile(Tile currentSpotTile) {
        this.currentSpotTile = currentSpotTile;
    }

    public void resetCurrentSpotTile() {
        this.currentSpotTile = null;
    }

    public int getEatThresholdPercent() {
        return eatThresholdPercent;
    }

    public void setEatThresholdPercent(int eatThresholdPercent) {
        this.eatThresholdPercent = eatThresholdPercent;
    }

    public long getNoCombatThresholdMillis() {
        return noCombatThresholdMillis;
    }

    public void setNoCombatThresholdMillis(long noCombatThresholdMillis) {
        this.noCombatThresholdMillis = noCombatThresholdMillis;
    }

    public long getLastWorldHopMillis() {
        return lastWorldHopMillis;
    }

    public void setLastWorldHopMillis(long lastWorldHopMillis) {
        this.lastWorldHopMillis = lastWorldHopMillis;
    }

    public long getLastDormantSeenTime() {
        return lastDormantSeenTime;
    }

    public void setLastDormantSeenTime(long lastDormantSeenTime) {
        this.lastDormantSeenTime = lastDormantSeenTime;
    }

    public boolean isDormantWarningShown() {
        return dormantWarningShown;
    }

    public void setDormantWarningShown(boolean dormantWarningShown) {
        this.dormantWarningShown = dormantWarningShown;
    }

    public Map<String, Long> spotCrashFirstSeen() {
        return spotCrashFirstSeen;
    }

    public Skill getInitialLockedSkill() {
        return initialLockedSkill;
    }

    public void setInitialLockedSkill(Skill initialLockedSkill) {
        this.initialLockedSkill = initialLockedSkill;
    }

    public Skill getCurrentTrainingSkill() {
        return currentTrainingSkill;
    }

    public void setCurrentTrainingSkill(Skill currentTrainingSkill) {
        this.currentTrainingSkill = currentTrainingSkill;
    }

    public boolean hasCurrentTrainingSkill() {
        return currentTrainingSkill != null;
    }

    public void clearCurrentTrainingSkill() {
        this.currentTrainingSkill = null;
    }

    public void resetDormantWarning() {
        this.dormantWarningShown = false;
        this.lastDormantSeenTime = System.currentTimeMillis();
    }

    public void clearSpotCrashTracking(String key) {
        Objects.requireNonNull(key, "key");
        spotCrashFirstSeen.remove(key);
    }
}

