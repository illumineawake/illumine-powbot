package org.illumine.barb3tickfishing;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.rt4.Game;
import org.powbot.api.rt4.World;
import org.powbot.api.rt4.Worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class WorldHopController {
    private final Barb3TickFishingScript script;

    private boolean hopEnabled = false;
    private int hopIntervalMinutes = 10;
    private long nextHopAtMs = 0L;
    private boolean shouldHop = false;
    private int currentWorldId = -1;
    private World.Server homeServer = null;

    WorldHopController(Barb3TickFishingScript script) {
        this.script = script;
    }

    void applyInitialVisibility() {
        try {
            boolean initialHopEnabled = ScriptOptionParser.asBoolean(script.getOption("Allow World Hopping"), false);
            applyHopOptionVisibility(initialHopEnabled);
        } catch (Exception ignored) {
        }
    }

    void initialize() {
        hopEnabled = ScriptOptionParser.asBoolean(script.getOption("Allow World Hopping"), false);
        hopIntervalMinutes = ScriptOptionParser.asInt(script.getOption("World Hop every X minutes"), 10, 1);
        applyHopOptionVisibility(hopEnabled);
        currentWorldId = resolveCurrentWorldId();
        homeServer = resolveCurrentServer();
        if (hopEnabled) {
            scheduleNextHop();
        } else {
            clearHopSchedule();
        }
    }

    void reset() {
        clearHopSchedule();
        hopEnabled = false;
        hopIntervalMinutes = 10;
        currentWorldId = -1;
        homeServer = null;
    }

    void updateHopDue(long now) {
        if (hopEnabled && nextHopAtMs > 0L && now >= nextHopAtMs) {
            shouldHop = true;
        }
    }

    boolean canWorldHop(boolean canBreak) {
        if (!hopEnabled) {
            return false;
        }
        if (!shouldHop) {
            return false;
        }
        return canBreak;
    }

    void scheduleNextHop() {
        if (!hopEnabled) {
            clearHopSchedule();
            return;
        }
        nextHopAtMs = computeNextHopDueMs(hopIntervalMinutes);
        shouldHop = false;
    }

    void clearHopSchedule() {
        nextHopAtMs = 0L;
        shouldHop = false;
    }

    boolean performWorldHop() {
        World current = resolveCurrentWorld();
        if (!current.valid()) {
            script.log("t=" + script.currentTick() + " world-hop: unable to resolve current world");
            return false;
        }
        currentWorldId = current.id();
        homeServer = current.server();

        List<World> candidates = new ArrayList<>(Worlds.stream()
                .filtered(World::valid)
                .filtered(world -> world.id() != current.id())
                .filtered(world -> world.type() == World.Type.MEMBERS)
                .filtered(world -> world.specialty() == World.Specialty.NONE)
                .filtered(world -> world.server() == homeServer)
                .list());
        if (candidates.isEmpty()) {
            script.log("t=" + script.currentTick() + " world-hop: no eligible worlds for server " + homeServer);
            return false;
        }
        Collections.shuffle(candidates);
        int attempts = 0;
        for (World candidate : candidates) {
            if (!candidate.valid()) {
                continue;
            }
            if (attemptHop(candidate)) {
                script.log("t=" + script.currentTick() + " world-hop: switched to world " + candidate.id());
                return true;
            }
            attempts++;
            if (attempts >= 2) {
                break;
            }
        }
        script.log("t=" + script.currentTick() + " world-hop: attempts failed");
        return false;
    }

    boolean isHopEnabled() {
        return hopEnabled;
    }

    void setHopEnabled(boolean enabled) {
        hopEnabled = enabled;
        applyHopOptionVisibility(hopEnabled);
        if (hopEnabled) {
            hopIntervalMinutes = ScriptOptionParser.asInt(script.getOption("World Hop every X minutes"), hopIntervalMinutes, 1);
            scheduleNextHop();
        } else {
            clearHopSchedule();
        }
    }

    void setHopIntervalMinutes(int minutes) {
        hopIntervalMinutes = minutes;
        if (hopEnabled) {
            scheduleNextHop();
        }
    }

    int hopIntervalMinutes() {
        return hopIntervalMinutes;
    }

    String formatTimeToHop() {
        if (!hopEnabled || nextHopAtMs <= 0L) {
            return "—";
        }
        return formatMs(nextHopAtMs - System.currentTimeMillis());
    }

    String formatCurrentWorld() {
        if (currentWorldId <= 0) {
            currentWorldId = resolveCurrentWorldId();
        }
        if (currentWorldId <= 0) {
            return "—";
        }
        return "W" + currentWorldId;
    }

    private boolean attemptHop(World target) {
        if (target == null || !target.valid()) {
            return false;
        }
        boolean hopped = target.hop();
        if (!hopped) {
            script.log("t=" + script.currentTick() + " world-hop: failed to initiate hop to world " + target.id());
            return false;
        }
        Condition.wait(() -> !Game.loggedIn(), 200, 25);
        boolean relogged = Condition.wait(Game::loggedIn, 200, 40);
        if (!relogged) {
            script.log("t=" + script.currentTick() + " world-hop: login timed out for world " + target.id());
            return false;
        }
        currentWorldId = target.id();
        World.Server server = target.server();
        if (server != null) {
            homeServer = server;
        }
        script.onWorldHopSuccess();
        return true;
    }

    private long computeNextHopDueMs(int minutes) {
        long baseMillis = Math.max(1, minutes) * 60_000L;
        double factor = Random.nextDouble(0.85, 1.15);
        return System.currentTimeMillis() + (long) (baseMillis * factor);
    }

    private World resolveCurrentWorld() {
        return Worlds.current();
    }

    private int resolveCurrentWorldId() {
        World current = resolveCurrentWorld();
        if (current.valid()) {
            currentWorldId = current.id();
        }
        return currentWorldId;
    }

    private World.Server resolveCurrentServer() {
        World current = resolveCurrentWorld();
        World.Server server = (current.valid()) ? current.server() : null;
        if (server != null) {
            homeServer = server;
        }
        return server;
    }

    private void applyHopOptionVisibility(boolean enabled) {
        script.updateVisibility("World Hop every X minutes", enabled);
    }

    private String formatMs(long msRemaining) {
        if (msRemaining <= 0) {
            return "00:00";
        }
        long totalSeconds = msRemaining / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
