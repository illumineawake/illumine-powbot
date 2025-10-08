package org.illumine.barb3tickfishing;

import org.powbot.api.Random;

class ModeScheduler {
    private final Barb3TickFishingScript script;
    private final Barb3TickConfig config;

    private long modeExpiresAtMs = 0L;
    private boolean switchQueued = false;
    private Barb3TickFishingScript.FishingMode fishingMode = Barb3TickFishingScript.FishingMode.THREE_TICK;

    ModeScheduler(Barb3TickFishingScript script, Barb3TickConfig config) {
        this.script = script;
        this.config = config;
    }

    void reset() {
        modeExpiresAtMs = 0L;
        switchQueued = false;
        fishingMode = Barb3TickFishingScript.FishingMode.THREE_TICK;
    }

    void initialiseMode() {
        fishingMode = config.frequencyMode().startsInThreeTick()
                ? Barb3TickFishingScript.FishingMode.THREE_TICK
                : Barb3TickFishingScript.FishingMode.NORMAL;
        switchQueued = false;
        scheduleNextWindow();
    }

    boolean tickFishing() {
        return fishingMode == Barb3TickFishingScript.FishingMode.THREE_TICK;
    }

    long modeExpiresAtMs() {
        return modeExpiresAtMs;
    }

    boolean switchQueued() {
        return switchQueued;
    }

    void queueSwitch() {
        switchQueued = true;
    }

    void clearQueue() {
        switchQueued = false;
    }

    void setFishingMode(Barb3TickFishingScript.FishingMode mode) {
        fishingMode = mode;
        scheduleNextWindow();
        script.log("Switched Fishing mode to " + (tickFishing() ? "3Tick Fishing" : "Normal Fishing"));
    }

    private void scheduleNextWindow() {
        if (!config.switchingEnabled()) {
            modeExpiresAtMs = 0L;
            return;
        }
        long duration = tickFishing() ? rollThreeTickDurationMs() : rollNormalDurationMs();
        modeExpiresAtMs = System.currentTimeMillis() + duration;
    }

    void refreshScheduleAfterConfigChange() {
        scheduleNextWindow();
    }

    private long rollThreeTickDurationMs() {
        switch (config.frequencyMode()) {
            case ALWAYS:
            case MOSTLY:
                return Random.nextInt(90_000, 180_000);
            case SOMETIMES:
                return Random.nextInt(30_000, 90_000);
            case NEVER:
            default:
                return Random.nextInt(30_000, 90_000);
        }
    }

    private long rollNormalDurationMs() {
        switch (config.frequencyMode()) {
            case MOSTLY:
                return Random.nextInt(30_000, 90_000);
            case SOMETIMES:
                return Random.nextInt(180_000, 300_000);
            case NEVER:
                return Random.nextInt(180_000, 300_000);
            case ALWAYS:
            default:
                return Random.nextInt(30_000, 90_000);
        }
    }
}
