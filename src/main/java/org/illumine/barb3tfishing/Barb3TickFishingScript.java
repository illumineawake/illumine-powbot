package org.illumine.barb3tfishing;

import com.google.common.eventbus.Subscribe;
import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.Tile;
import org.powbot.api.event.TickEvent;
import org.powbot.api.rt4.*;
import org.powbot.api.rt4.walking.model.Skill;
import org.powbot.api.script.*;
import org.powbot.api.script.paint.Paint;
import org.powbot.api.script.paint.PaintBuilder;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@ScriptConfiguration(name = "3-Tick Method", description = "Select the 3-tick method to use", optionType = OptionType.STRING, defaultValue = "Herb + Tar", allowedValues = {"Herb + Tar", "Knife + Logs"})
@ScriptConfiguration(name = "Herb Name", description = "Clean herb used for Herb + Tar", optionType = OptionType.STRING, defaultValue = "Guam leaf")
@ScriptConfiguration(name = "Logs Name", description = "Logs used for Knife + Logs", optionType = OptionType.STRING, defaultValue = "Logs")
@ScriptConfiguration(name = "Enable World Hop", description = "Enable periodic world hopping", optionType = OptionType.BOOLEAN, defaultValue = "false")
@ScriptConfiguration(name = "Hop Interval Minutes", description = "Minutes between world hops", optionType = OptionType.INTEGER, defaultValue = "15")
@ScriptConfiguration(name = "Enable Overlay", description = "Show the status overlay", optionType = OptionType.BOOLEAN, defaultValue = "true")
@ScriptManifest(name = "Barb 3T Fishing", description = "Barbarian 3-tick fishing", author = "illumine", category = ScriptCategory.Fishing, version = "0.1.0")
public class Barb3TickFishingScript extends AbstractScript {

    private static final String FISHING_SPOT_NAME = "Fishing spot";
    private static final String FISHING_ACTION = "Use-rod";
    private static final String BARBARIAN_ROD_NAME = "Barbarian rod";
    private static final String FEATHER_NAME = "Feather";
    private static final int SPOT_RADIUS_TILES = 15;
    private static final long NO_SPOT_TIMEOUT_MS = 120_000L;
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");
    private static final long CAST_RETRY_INTERVAL_MS = 1_200L;
    private static final EnumSet<World.Specialty> DISALLOWED_SPECIALTIES = EnumSet.of(
            World.Specialty.PVP,
            World.Specialty.HIGH_RISK,
            World.Specialty.DEAD_MAN,
            World.Specialty.BOUNTY_HUNTER
    );

    private static final int[] CAST_ANIMATIONS = new int[]{622, 623, 8193, 8194, 9350};

    private enum Phase {
        CAST("Casting"),
        THREE_TICK("3-Tick Action"),
        DROP_ONE("Dropping"),
        RECAST("Recasting");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private enum TickMethod {
        HERB_TAR,
        KNIFE_LOGS
    }

    private Tile startTile;
    private Phase phase;
    private TickMethod tickMethod;
    private String herbName;
    private String logsName;
    private boolean hopEnabled;
    private int hopIntervalMinutes;
    private boolean overlayEnabled;

    private boolean tickAdvanced;
    private long lastSpotSeenMillis;
    private long nextHopAtMillis;
    private boolean shouldHop;
    private long scriptStartMillis;
    private long fishingStartExp;
    private long agilityStartExp;
    private long strengthStartExp;
    private String stateText;
    private String stopReason;
    private int currentWorldId;
    private World.Server homeServer;
    private Tile targetSpotTile;
    private long lastCastAttemptMillis;
    private long currentTick;

    private Paint overlay;

    // Tracks the last log key to suppress duplicate messages
    private String lastLogKey;

    @Override
    public void onStart() {
        super.onStart();
        scriptStartMillis = System.currentTimeMillis();
        Player local = Players.local();
        startTile = (local != null && local.valid()) ? local.tile() : null;

        parseConfiguration();

        phase = Phase.CAST;
        tickAdvanced = false;
        shouldHop = false;
        stateText = phase.label();
        stopReason = "";
        lastSpotSeenMillis = System.currentTimeMillis();
        nextHopAtMillis = -1L;
        targetSpotTile = null;
        lastCastAttemptMillis = 0L;
        currentTick = 0L;

        fishingStartExp = Skills.experience(Skill.Fishing);
        agilityStartExp = Skills.experience(Skill.Agility);
        strengthStartExp = Skills.experience(Skill.Strength);

        currentWorldId = resolveCurrentWorldId();
        homeServer = resolveCurrentServer();

        if (overlayEnabled) {
            initOverlay();
        } else {
            try {
                clearPaints();
            } catch (Exception ignored) {
            }
        }

        if (hopEnabled) {
            scheduleNextHop();
        }

        logInfoOnce("start", String.format(Locale.ENGLISH,
                "Barb3T ready | method=%s | hop=%s | overlay=%s",
                tickMethod, hopEnabled, overlayEnabled));
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        currentTick = 0L;
        currentTick = 0L;
        if (stopReason == null || stopReason.isEmpty()) {
            logInfoOnce("stop", "Barb 3T Fishing stopped.");
        } else {
            logInfoOnce("stop", "Barb 3T Fishing stopped: " + stopReason);
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        currentTick++;
        tickAdvanced = true;

        if (hopEnabled && nextHopAtMillis > 0 && System.currentTimeMillis() >= nextHopAtMillis) {
            shouldHop = true;
            logInfoOnce("tick-hop", "hop timer elapsed (nextHopAt=" + nextHopAtMillis + ")");
        }

        Npc spot = locateSpot();
        if (spot != null && spot.valid()) {
            lastSpotSeenMillis = System.currentTimeMillis();
        }

        currentWorldId = resolveCurrentWorldId();
    }

    @Override
    public void poll() {
        if (!stopReason.isEmpty()) {
            if (getController() != null) {
                getController().stop();
            }
            return;
        }

        if (!ensureSupplies()) {
            return;
        }

        if (System.currentTimeMillis() - lastSpotSeenMillis >= NO_SPOT_TIMEOUT_MS) {
            requestStop("No fishing spot within radius for 2 minutes");
            return;
        }

        if (hopEnabled && shouldHop) {
            stateText = "Preparing hop";
            dropOneLeapingFish();
            if (performWorldHop()) {
                shouldHop = false;
                scheduleNextHop();
                setPhase(Phase.CAST);
            } else {
                rescheduleHopRetry();
            }
            return;
        }

        if (!tickAdvanced) {
            return;
        }
        tickAdvanced = false;

        switch (phase) {
            case CAST:
            case RECAST:
                boolean confirmed = castConfirmed();
                logInfoOnce("phase-gate", phase + " lastCastAttemptMillis=" + lastCastAttemptMillis
                        + " confirmed=" + confirmed);
                if (lastCastAttemptMillis > 0L && confirmed) {
                    setPhase(Phase.THREE_TICK);
                    break;
                }
                long sinceAttempt = lastCastAttemptMillis == 0L
                        ? Long.MAX_VALUE
                        : System.currentTimeMillis() - lastCastAttemptMillis;
                logInfoOnce("phase-gate", "sinceAttemptMs=" + sinceAttempt);
                if (sinceAttempt >= CAST_RETRY_INTERVAL_MS) {
                    castRod();
                }
                break;
            case THREE_TICK:
                if (performTickAction()) {
                    setPhase(Phase.DROP_ONE);
                }
                break;
            case DROP_ONE:
                dropOneLeapingFish();
                setPhase(Phase.RECAST);
                break;
            default:
                setPhase(Phase.CAST);
                break;
        }
    }

    private void parseConfiguration() {
        Object methodOption = getOption("3-Tick Method");
        String methodValue = methodOption instanceof String ? ((String) methodOption).trim() : "Herb + Tar";
        if ("Knife + Logs".equalsIgnoreCase(methodValue)) {
            tickMethod = TickMethod.KNIFE_LOGS;
        } else {
            tickMethod = TickMethod.HERB_TAR;
        }

        herbName = normaliseName(getOption("Herb Name"), "Guam leaf");
        logsName = normaliseName(getOption("Logs Name"), "Logs");

        hopEnabled = Boolean.TRUE.equals(getOption("Enable World Hop"));
        hopIntervalMinutes = parseInt(getOption("Hop Interval Minutes"), 15);
        if (hopIntervalMinutes < 1) {
            hopIntervalMinutes = 1;
        }
        overlayEnabled = !Boolean.FALSE.equals(getOption("Enable Overlay"));
    }

    private boolean ensureSupplies() {
        if (!hasItem(BARBARIAN_ROD_NAME)) {
            requestStop("Barbarian rod missing");
            return false;
        }
        if (!hasItem(FEATHER_NAME)) {
            requestStop("Feathers depleted");
            return false;
        }

        if (tickMethod == TickMethod.HERB_TAR) {
            if (!hasHerbSupply()) {
                requestStop(String.format(Locale.ENGLISH, "%s unavailable", herbName));
                return false;
            }
            if (!hasTar()) {
                requestStop("Tar unavailable for Herb+Tar");
                return false;
            }
        } else if (tickMethod == TickMethod.KNIFE_LOGS) {
            if (!hasItem("Knife")) {
                requestStop("Knife missing");
                return false;
            }
            if (!hasLogs()) {
                requestStop(String.format(Locale.ENGLISH, "%s unavailable", logsName));
                return false;
            }
        }
        return true;
    }

    private boolean castRod() {
        Npc spot = locateSpot();
        if (spot == null || !spot.valid()) {
            stateText = "Searching spot";
            lastCastAttemptMillis = 0L;
            logInfoOnce("cast", "no spot available (phase=" + phase + ")");
            return false;
        }
        boolean interacted = spot.interact(FISHING_ACTION, FISHING_SPOT_NAME);
        if (interacted) {
            stateText = "Casting Use-rod";
            lastCastAttemptMillis = System.currentTimeMillis();
            logInfoOnce("cast", "clicked spot at " + spot.tile() + " phase=" + phase);
        } else if (getLog() != null) {
            logInfoOnce("cast", "click failed on spot at " + spot.tile());
        }
        return interacted;
    }

    private boolean performTickAction() {
        boolean success = false;
        if (tickMethod == TickMethod.HERB_TAR) {
            success = useHerbOnTar();
        } else if (tickMethod == TickMethod.KNIFE_LOGS) {
            success = useKnifeOnLogs();
        }
        if (success) {
            stateText = "3-tick action";
            logInfoOnce("tick-action", "method=" + tickMethod + " success=true");
        } else {
            logInfoOnce("tick-action", "method=" + tickMethod + " success=false");
        }
        return success;
    }

    private boolean useHerbOnTar() {
        Item cleanHerb = Inventory.stream()
                .filtered(item -> matchesName(item, herbName))
                .first();
        if (cleanHerb == null || !cleanHerb.valid()) {
            Item grimyHerb = Inventory.stream()
                    .filtered(item -> matchesName(item, "Grimy " + herbName))
                    .first();
            if (grimyHerb != null && grimyHerb.valid()) {
                if (grimyHerb.interact("Clean")) {
                    logInfoOnce("tick-action", "cleaned grimy herb");
                    return true;
                }
            }
            return false;
        }

        Item tar = Inventory.stream()
                .filtered(item -> matchesName(item, "Swamp tar"))
                .first();
        if (tar == null || !tar.valid()) {
            logInfoOnce("tick-action", "missing swamp tar");
            return false;
        }
        Item selected = Inventory.selectedItem();
        if (selected != null && selected.valid()) {
            selected.interact("Cancel");
        }
        logInfoOnce("tick-action", "using " + cleanHerb.name() + " on Swamp tar");
        return cleanHerb.useOn(tar);
    }

    private boolean useKnifeOnLogs() {
        Item knife = Inventory.stream()
                .filtered(item -> matchesName(item, "Knife"))
                .first();
        if (knife == null || !knife.valid()) {
            return false;
        }
        Item logs = Inventory.stream()
                .filtered(item -> matchesName(item, logsName))
                .first();
        if (logs == null || !logs.valid()) {
            return false;
        }
        return knife.useOn(logs);
    }

    private void dropOneLeapingFish() {
        List<Item> leapingFish = Inventory.stream()
                .filtered(item -> item != null && item.valid() && nameContains(item, "Leaping"))
                .list();
        if (leapingFish.size() <= 1) {
            return;
        }
        Item drop = leapingFish.get(0);
        if (drop.valid() && drop.interact("Drop")) {
            stateText = "Drop one leaping fish";
            logInfoOnce("drop", "dropped " + drop.name());
        }
    }

    private boolean performWorldHop() {
        stateText = "Hopping worlds";
        World currentWorld = resolveCurrentWorld();
        if (currentWorld == null || !currentWorld.valid()) {
            logWarnOnce("hop", "Unable to resolve current world before hop");
            return false;
        }
        World.Server server = currentWorld.server();
        if (server != null) {
            homeServer = server;
        }
        final World.Server targetServer = server != null ? server : homeServer;
        logInfoOnce("hop", "current=" + currentWorld.id() + " server=" + server);
        List<World> candidates = new ArrayList<>(Worlds.stream()
                .filtered(World::valid)
                .filtered(world -> world.id() != currentWorld.id())
                .filtered(world -> world.type() == World.Type.MEMBERS)
                .filtered(world -> targetServer == null || world.server() == targetServer)
                .filtered(world -> !DISALLOWED_SPECIALTIES.contains(world.specialty()))
                .list());
        if (candidates.isEmpty()) {
            return false;
        }
        int index = Random.nextInt(0, candidates.size());
        World target = candidates.get(index);
        if (target == null || !target.valid()) {
            return false;
        }
        logInfoOnce("hop", "target=" + target.id() + " server=" + target.server());
        boolean hopped = target.hop();
        if (!hopped) {
            return false;
        }
        Condition.wait(() -> !Game.loggedIn(), 200, 20);
        Condition.wait(Game::loggedIn, 200, 40);
        currentWorldId = target.id();
        targetSpotTile = null;
        lastCastAttemptMillis = 0L;
        return true;
    }

    private void scheduleNextHop() {
        long baseMillis = Math.max(1, hopIntervalMinutes) * 60_000L;
        double factor = Random.nextDouble(0.8, 1.2);
        nextHopAtMillis = System.currentTimeMillis() + (long) (baseMillis * factor);
    }

    private void rescheduleHopRetry() {
        nextHopAtMillis = System.currentTimeMillis() + 60_000L;
        shouldHop = false;
    }

    private void setPhase(Phase newPhase) {
        if (newPhase != null) {
            phase = newPhase;
            stateText = newPhase.label();
            if (newPhase == Phase.CAST || newPhase == Phase.RECAST) {
                lastCastAttemptMillis = 0L;
            }
            logInfoOnce("phase", "-> " + newPhase);
        }
    }

    private Npc locateSpot() {
        Player local = Players.local();
        if (local == null || !local.valid()) {
            return null;
        }
        Tile origin = startTile != null ? startTile : local.tile();

        if (targetSpotTile != null) {
            Npc locked = Npcs.stream()
                    .name(FISHING_SPOT_NAME)
                    .within(origin, SPOT_RADIUS_TILES)
                    .filtered(npc -> npc != null && npc.valid()
                            && targetSpotTile.equals(npc.tile())
                            && hasAction(npc, FISHING_ACTION))
                    .first();
            if (locked != null && locked.valid()) {
                logInfoOnce("spot", "locked target still valid at " + locked.tile());
                return locked;
            }
            targetSpotTile = null;
            logInfoOnce("spot", "previous target expired");
        }

        Npc nearest = Npcs.stream()
                .name(FISHING_SPOT_NAME)
                .within(origin, SPOT_RADIUS_TILES)
                .filtered(npc -> npc != null && npc.valid() && hasAction(npc, FISHING_ACTION))
                .nearest()
                .first();
        if (nearest != null && nearest.valid()) {
            targetSpotTile = nearest.tile();
            logInfoOnce("spot", "acquired new target at " + targetSpotTile);
        }
        return nearest;
    }

    private boolean hasAction(Npc npc, String action) {
        if (npc == null || action == null) {
            return false;
        }
        List<String> actions = npc.actions();
        if (actions == null) {
            return false;
        }
        for (String a : actions) {
            if (action.equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }

    private boolean castConfirmed() {
        Player local = Players.local();
        if (local == null || !local.valid()) {
            logInfoOnce("confirm", "local player invalid");
            return false;
        }

        int animation = local.animation();
        if (animation != -1) {
            for (int id : CAST_ANIMATIONS) {
                if (animation == id) {
                    logInfoOnce("confirm", "animation match id=" + animation);
                    return true;
                }
            }
        }

        Actor interacting = local.interacting();
        if (interacting instanceof Npc) {
            Npc npc = (Npc) interacting;
            if (npc.valid()) {
                String name = npc.name();
                if (name != null && FISHING_SPOT_NAME.equalsIgnoreCase(name)) {
                    if (targetSpotTile == null || targetSpotTile.equals(npc.tile())) {
                        logInfoOnce("confirm", "interacting spot at " + npc.tile());
                        return true;
                    }
                }
            }
        }
        logInfoOnce("confirm", "no animation or interacting match");
        return false;
    }

    private void requestStop(String reason) {
        if (!stopReason.isEmpty()) {
            return;
        }
        stopReason = reason == null ? "" : reason;
        logWarnOnce("stop", stopReason);
        if (getController() != null) {
            getController().stop();
        }
    }

    private boolean hasItem(String name) {
        Item item = Inventory.stream()
                .filtered(it -> matchesName(it, name))
                .first();
        boolean present = item != null && item.valid();
        if (!present) {
            logInfoOnce("supplies", name + " missing");
        }
        return present;
    }

    private boolean hasHerbSupply() {
        Item clean = Inventory.stream()
                .filtered(item -> matchesName(item, herbName))
                .first();
        if (clean != null && clean.valid()) {
            return true;
        }
        Item grimy = Inventory.stream()
                .filtered(item -> matchesName(item, "Grimy " + herbName))
                .first();
        return grimy != null && grimy.valid();
    }

    private boolean hasTar() {
        Item tar = Inventory.stream()
                .filtered(item -> matchesName(item, "Swamp tar"))
                .first();
        return tar != null && tar.valid();
    }

    private boolean hasLogs() {
        Item logs = Inventory.stream()
                .filtered(item -> matchesName(item, logsName))
                .first();
        return logs != null && logs.valid();
    }

    private String normaliseName(Object option, String fallback) {
        if (option instanceof String) {
            String value = ((String) option).trim();
            return value.isEmpty() ? fallback : value;
        }
        return fallback;
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean matchesName(Item item, String expected) {
        if (item == null || !item.valid() || expected == null) {
            return false;
        }
        String name = item.name();
        return name != null && name.equalsIgnoreCase(expected);
    }

    private boolean nameContains(Item item, String token) {
        if (item == null || !item.valid() || token == null) {
            return false;
        }
        String name = item.name();
        return name != null && name.toLowerCase(Locale.ENGLISH).contains(token.toLowerCase(Locale.ENGLISH));
    }

    private World resolveCurrentWorld() {
        try {
            for (World world : Worlds.stream().filtered(World::valid).list()) {
                if (isCurrentWorld(world)) {
                    return world;
                }
            }
            logInfoOnce("world", "no current world resolved via stream");
        } catch (Exception ignored) {
        }
        return null;
    }

    private void logInfoOnce(String category, String message) {
        if (message == null) {
            return;
        }
        if (getLog() == null) {
            return;
        }
        String key = (category == null ? "" : category) + "|" + message;
        if (!key.equals(lastLogKey)) {
            lastLogKey = key;
            getLog().info(logPrefix(category == null ? "info" : category) + message);
        }
    }

    private void logWarnOnce(String category, String message) {
        if (message == null) {
            return;
        }
        if (getLog() == null) {
            return;
        }
        String key = "WARN:" + (category == null ? "" : category) + "|" + message;
        if (!key.equals(lastLogKey)) {
            lastLogKey = key;
            getLog().warning(logPrefix(category == null ? "warn" : category) + message);
        }
    }

    private World.Server resolveCurrentServer() {
        World current = resolveCurrentWorld();
        return current != null ? current.server() : null;
    }

    private int resolveCurrentWorldId() {
        World current = resolveCurrentWorld();
        return current != null && current.valid() ? current.id() : -1;
    }

    private boolean isCurrentWorld(World world) {
        if (world == null || !world.valid()) {
            return false;
        }
        try {
            Method method = world.getClass().getMethod("current");
            Object result = method.invoke(world);
            if (result instanceof Boolean && (Boolean) result) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = world.getClass().getMethod("isCurrent");
            Object result = method.invoke(world);
            if (result instanceof Boolean && (Boolean) result) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return currentWorldId > 0 && world.id() == currentWorldId;
    }

    private void initOverlay() {
        try {
            clearPaints();
        } catch (Exception ignored) {
        }
        overlay = PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(Skill.Fishing)
                .trackSkill(Skill.Agility)
                .trackSkill(Skill.Strength)
                .addString("Runtime", () -> formatDuration(System.currentTimeMillis() - scriptStartMillis))
                .addString("State", () -> stateText)
                .addString("Next Hop", this::formatTimeToHop)
                .addString("World", this::formatCurrentWorld)
                .addString("Stop Reason", this::formatStopReason)
                .addString("Cast Attempted", () -> lastCastAttemptMillis > 0L ? "yes" : "no")
                .addString("Cast Confirmed", () -> castConfirmed() ? "yes" : "no")
                .addString("Fish XP/h", () -> formatXpPerHour(Skill.Fishing, fishingStartExp))
                .addString("Agility XP/h", () -> formatXpPerHour(Skill.Agility, agilityStartExp))
                .addString("Strength XP/h", () -> formatXpPerHour(Skill.Strength, strengthStartExp))
                .build();
        addPaint(overlay);
    }

    private String formatDuration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, secs);
    }

    private String formatTimeToHop() {
        if (!hopEnabled || nextHopAtMillis <= 0) {
            return "—";
        }
        long remaining = nextHopAtMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return "0s";
        }
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return String.format(Locale.ENGLISH, "%dm %02ds", minutes, secs);
        }
        return String.format(Locale.ENGLISH, "%ds", secs);
    }

    private String formatCurrentWorld() {
        if (currentWorldId > 0) {
            return "W" + currentWorldId;
        }
        return "—";
    }

    private String formatStopReason() {
        return stopReason == null || stopReason.isEmpty() ? "—" : stopReason;
    }

    private String formatXpPerHour(Skill skill, long startingXp) {
        long gained = Skills.experience(skill) - startingXp;
        long runtime = System.currentTimeMillis() - scriptStartMillis;
        if (runtime <= 0 || gained <= 0) {
            return "0";
        }
        long xpPerHour = gained * 3_600_000L / runtime;
        return NUMBER_FORMAT.format(xpPerHour);
    }

    private String logPrefix(String label) {
        return "[Barb3T] " + label + " | tick=" + currentTick + " | phase=" + phase
                + " | target=" + targetSpotTile + " | castAttemptAt=" + lastCastAttemptMillis;
    }

    public static void main(String[] args) {
        new Barb3TickFishingScript().startScript();
    }
}
