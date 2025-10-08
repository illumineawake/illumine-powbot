package org.illumine.barb3tickfishing;

class Barb3TickConfig {
    private final Barb3TickFishingScript script;
    private final String defaultHerbName;

    private ThreeTickFrequencyMode frequencyMode = ThreeTickFrequencyMode.SOMETIMES;
    private boolean switchingEnabled = frequencyMode.switchingEnabled();
    private String herbName;
    private boolean switchToNormalOnSuppliesOut = true;

    Barb3TickConfig(Barb3TickFishingScript script, String defaultHerbName) {
        this.script = script;
        this.defaultHerbName = defaultHerbName;
        this.herbName = defaultHerbName;
    }

    void initialize() {
        Object optionValue = script.getOption("3Tick Frequency Mode");
        String optionString = optionValue == null ? ThreeTickFrequencyMode.SOMETIMES.label() : optionValue.toString();
        setFrequencyMode(ThreeTickFrequencyMode.fromOptionString(optionString));

        herbName = resolveHerbNameOption();

        Object fallbackOption = script.getOption("Switch to normal fishing if out of 3Tick supplies");
        switchToNormalOnSuppliesOut = ScriptOptionParser.asBoolean(fallbackOption, true);
    }

    void applyInitialVisibility() {
        try {
            Object optionValue = script.getOption("3Tick Frequency Mode");
            String optionString = optionValue == null ? ThreeTickFrequencyMode.SOMETIMES.label() : optionValue.toString();
            applyOptionVisibility(ThreeTickFrequencyMode.fromOptionString(optionString));
        } catch (Exception ignored) {
        }
    }

    ThreeTickFrequencyMode frequencyMode() {
        return frequencyMode;
    }

    boolean switchingEnabled() {
        return switchingEnabled;
    }

    String herbName() {
        return herbName;
    }

    boolean switchToNormalOnSuppliesOut() {
        return switchToNormalOnSuppliesOut;
    }

    void setFrequencyMode(ThreeTickFrequencyMode mode) {
        if (mode == null) {
            mode = ThreeTickFrequencyMode.SOMETIMES;
        }
        frequencyMode = mode;
        switchingEnabled = frequencyMode.switchingEnabled();
        applyOptionVisibility(frequencyMode);
    }

    void setHerbName(String name) {
        if (name == null) {
            herbName = defaultHerbName;
            return;
        }
        String trimmed = name.trim();
        herbName = trimmed.isEmpty() ? defaultHerbName : trimmed;
    }

    void reset() {
        frequencyMode = ThreeTickFrequencyMode.SOMETIMES;
        switchingEnabled = frequencyMode.switchingEnabled();
        herbName = defaultHerbName;
        switchToNormalOnSuppliesOut = true;
    }

    void updateVisibilityForMode(ThreeTickFrequencyMode mode) {
        if (mode == null) {
            mode = ThreeTickFrequencyMode.SOMETIMES;
        }
        applyOptionVisibility(mode);
    }

    private String resolveHerbNameOption() {
        return ScriptOptionParser.asString(script.getOption("Clean Herb Name"), defaultHerbName);
    }

    private void applyOptionVisibility(ThreeTickFrequencyMode mode) {
        boolean showThreeTickOptions = mode != ThreeTickFrequencyMode.NEVER;
        script.updateVisibility("Switch to normal fishing if out of 3Tick supplies", showThreeTickOptions);
        script.updateVisibility("Clean Herb Name", showThreeTickOptions);
    }
}
