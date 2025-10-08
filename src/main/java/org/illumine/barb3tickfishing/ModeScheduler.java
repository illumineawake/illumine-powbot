package org.illumine.barb3tickfishing;

import org.powbot.api.Random;

class ModeScheduler {
    private final Barb3TickFishingScript script;
    private final Barb3TickConfig config;

    private long modeExpiresAtMs = 0L;
    private boolean switchQueued = false;
    private Barb3TickFishingScript.FishingMode fishingMode = Barb3TickFishingScript.FishingMode.THREE_TICK;

    private ThreeTickFrequencyMode activeRandomProfile = null;
    private RandomPhase randomPhase = RandomPhase.NONE;

    private enum RandomPhase { NONE, PHASE_3T, PHASE_NORMAL }

    ModeScheduler(Barb3TickFishingScript script, Barb3TickConfig config) {
        this.script = script;
        this.config = config;
    }

    void reset() {
        modeExpiresAtMs = 0L;
        switchQueued = false;
        fishingMode = Barb3TickFishingScript.FishingMode.THREE_TICK;
        activeRandomProfile = null;
        randomPhase = RandomPhase.NONE;
    }

    void initialiseMode() {
        switchQueued = false;
        if (isRandomActive()) {
            // Pick a starting profile at random and start its first window.
            pickNewRandomProfile();
            boolean startThreeTick = startsInThreeTickForProfile(activeRandomProfile);
            fishingMode = startThreeTick
                    ? Barb3TickFishingScript.FishingMode.THREE_TICK
                    : Barb3TickFishingScript.FishingMode.NORMAL;
            randomPhase = startThreeTick ? RandomPhase.PHASE_3T : RandomPhase.PHASE_NORMAL;
        } else {
            fishingMode = config.frequencyMode().startsInThreeTick()
                    ? Barb3TickFishingScript.FishingMode.THREE_TICK
                    : Barb3TickFishingScript.FishingMode.NORMAL;
        }
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
        if (!isRandomActive() && !config.switchingEnabled()) {
            modeExpiresAtMs = 0L;
            return;
        }
        long duration = isRandomActive()
                ? rollDurationForRandomCurrentWindow()
                : (tickFishing() ? rollThreeTickDurationMs() : rollNormalDurationMs());
        modeExpiresAtMs = System.currentTimeMillis() + duration;
    }

    void refreshScheduleAfterConfigChange() {
        scheduleNextWindow();
    }

    boolean isRandomActive() {
        return config.frequencyMode() == ThreeTickFrequencyMode.RANDOM;
    }

    ThreeTickFrequencyMode activeRandomProfile() {
        return activeRandomProfile;
    }

    void onWindowExpired() {
        clearQueue();
        if (!isRandomActive()) {
            if (config.switchingEnabled()) {
                queueSwitch();
            } else {
                // No scheduling in non-switching modes
                modeExpiresAtMs = 0L;
            }
            return;
        }

        // Random meta-mode expiry handling
        if (activeRandomProfile == null) {
            pickNewRandomProfile();
            randomPhase = startsInThreeTickForProfile(activeRandomProfile) ? RandomPhase.PHASE_3T : RandomPhase.PHASE_NORMAL;
        }

        switch (activeRandomProfile) {
            case MOSTLY:
            case SOMETIMES: {
                if (randomPhase == RandomPhase.PHASE_3T) {
                    // Complete 3T window → switch to Normal within same profile cycle
                    queueSwitch();
                    randomPhase = RandomPhase.PHASE_NORMAL;
                    return; // schedule after switch
                } else {
                    // Completed Normal window → start a new random profile cycle
                    pickNewRandomProfile();
                    boolean startThreeTick = startsInThreeTickForProfile(activeRandomProfile);
                    RandomPhase nextPhase = startThreeTick ? RandomPhase.PHASE_3T : RandomPhase.PHASE_NORMAL;
                    if (tickFishing() != startThreeTick) {
                        queueSwitch();
                        randomPhase = nextPhase;
                    } else {
                        randomPhase = nextPhase;
                        scheduleNextWindow();
                    }
                    return;
                }
            }
            case ALWAYS: {
                // Single 3T window; then pick a new profile cycle
                pickNewRandomProfile();
                boolean startThreeTick = startsInThreeTickForProfile(activeRandomProfile);
                randomPhase = startThreeTick ? RandomPhase.PHASE_3T : RandomPhase.PHASE_NORMAL;
                if (tickFishing() != startThreeTick) {
                    queueSwitch();
                } else {
                    scheduleNextWindow();
                }
                return;
            }
            case NEVER: {
                // Single Normal window; then pick a new profile cycle
                pickNewRandomProfile();
                boolean startThreeTick = startsInThreeTickForProfile(activeRandomProfile);
                randomPhase = startThreeTick ? RandomPhase.PHASE_3T : RandomPhase.PHASE_NORMAL;
                if (tickFishing() != startThreeTick) {
                    queueSwitch();
                } else {
                    scheduleNextWindow();
                }
                return;
            }
            case RANDOM:
            default: {
                // Shouldn't happen: RANDOM isn't a concrete profile
                pickNewRandomProfile();
                scheduleNextWindow();
            }
        }
    }

    private void pickNewRandomProfile() {
        ThreeTickFrequencyMode[] profiles = new ThreeTickFrequencyMode[] {
                ThreeTickFrequencyMode.ALWAYS,
                ThreeTickFrequencyMode.MOSTLY,
                ThreeTickFrequencyMode.SOMETIMES,
                ThreeTickFrequencyMode.NEVER
        };
        int idx = Random.nextInt(0, profiles.length - 1);
        activeRandomProfile = profiles[idx];
        script.log("Random: selected profile " + activeRandomProfile.shortLabel());
    }

    private boolean startsInThreeTickForProfile(ThreeTickFrequencyMode profile) {
        return profile != ThreeTickFrequencyMode.NEVER;
    }

    private long rollDurationForRandomCurrentWindow() {
        if (tickFishing()) {
            return rollThreeTickDurationForProfile(activeRandomProfile);
        } else {
            return rollNormalDurationForProfile(activeRandomProfile);
        }
    }

    private long rollThreeTickDurationForProfile(ThreeTickFrequencyMode profile) {
        if (profile == null) {
            return Random.nextInt(30_000, 90_000);
        }
        switch (profile) {
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

    private long rollNormalDurationForProfile(ThreeTickFrequencyMode profile) {
        if (profile == null) {
            return Random.nextInt(30_000, 90_000);
        }
        switch (profile) {
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
