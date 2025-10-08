package org.illumine.sandcrabs;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.rt4.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Encapsulates logic for reserving sand crab spots, checking occupancy/crash
 * status, and world hopping when new spots are needed.
 */
public class SpotManager {

    private final Logger logger;
    private SandCrabsConfig config;
    private final SandCrabsState state;
    private final CombatMonitor combatMonitor;

    public SpotManager(Logger logger, SandCrabsConfig config, SandCrabsState state, CombatMonitor combatMonitor) {
        this.logger = logger;
        this.config = config;
        this.state = state;
        this.combatMonitor = combatMonitor;
    }

    public void updateConfig(SandCrabsConfig config) {
        this.config = config;
    }

    public List<Tile> spotTiles() {
        return config.getSpotTiles();
    }

    public Tile getCurrentSpot() {
        return state.getCurrentSpotTile();
    }

    public void setCurrentSpot(Tile tile) {
        state.setCurrentSpotTile(tile);
    }

    public void clearCurrentSpot() {
        state.resetCurrentSpotTile();
    }

    public boolean isSpotOccupied(Tile spot) {
        if (spot == null) {
            return true;
        }
        return Players.stream()
                .within(spot, 3)
                .notLocalPlayer()
                .first()
                .valid();
    }

    public boolean isSpotCrashed(Tile spot) {
        if (spot == null) {
            return true;
        }

        Player local = Players.local();
        boolean someoneNearby = Players.stream()
                .within(spot, 3)
                .filtered(p -> !p.equals(local))
                .first()
                .valid();

        String key = String.valueOf(spot);
        long now = System.currentTimeMillis();
        Map<String, Long> seen = state.spotCrashFirstSeen();

        if (!someoneNearby) {
            state.clearSpotCrashTracking(key);
            return false;
        }

        Long firstSeen = seen.get(key);
        if (firstSeen == null) {
            seen.put(key, now);
            return false;
        }
        return now - firstSeen >= config.getSpotCrashThresholdMillis();
    }

    public List<Tile> eligibleSpots() {
        List<Tile> eligible = new ArrayList<>();
        for (Tile spot : config.getSpotTiles()) {
            if (!isSpotOccupied(spot)) {
                eligible.add(spot);
            }
        }
        return eligible;
    }

    public boolean canAttemptWorldHop() {
        return System.currentTimeMillis() - state.getLastWorldHopMillis() >= config.getWorldHopCooldownMillis();
    }

    public boolean hopToNextWorld() {
        if (!combatMonitor.waitUntilOutOfCombat()) {
            return false;
        }

        long requiredNoCombatMs = combatMonitor.getCurrentNoCombatThresholdMillis();
        long cap = Math.max(4000, Math.min(20000, requiredNoCombatMs));
        long sinceLastXp = combatMonitor.minTrackedSkillExpDelta();
        if (sinceLastXp < cap) {
            if (logger != null) {
                logger.info("Waiting " + cap + "ms of no combat before hopping...");
            }
            int attempts = (int) Math.ceil(cap / 200.0) + 5;
            boolean waited = Condition.wait(() -> combatMonitor.minTrackedSkillExpDelta() >= cap, 200, attempts);
            if (!waited) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        if (now - state.getLastWorldHopMillis() < config.getWorldHopCooldownMillis()) {
            return false;
        }
        state.setLastWorldHopMillis(now);

        List<World> candidates = new ArrayList<>(Worlds.stream()
                .filtered(World::valid)
                .filtered(world -> world.type() == World.Type.MEMBERS)
                .filtered(world -> {
                    World.Specialty specialty = world.specialty();
                    return specialty != World.Specialty.PVP && specialty != World.Specialty.HIGH_RISK;
                })
                .list());

        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort(Comparator.comparingInt(World::getPopulation));
        int poolSize = Math.min(10, candidates.size());
        int index = Random.nextInt(0, poolSize);
        World target = candidates.get(index);
        if (target == null || !target.valid()) {
            return false;
        }

        boolean hopped = target.hop();
        if (!hopped) {
            return false;
        }

        Condition.wait(() -> !Game.loggedIn(), 200, 25);
        Condition.wait(Game::loggedIn, 200, 50);
        clearCurrentSpot();
        return true;
    }

    public boolean hasNearbyDormantCrab() {
        return combatMonitor.isDormantCrabNearby();
    }

    public void resetSpotCrashTracking(Tile spot) {
        if (spot == null) {
            return;
        }
        state.clearSpotCrashTracking(String.valueOf(spot));
    }
}
