package org.illumine.barb3tickfishing;

enum ThreeTickFrequencyMode {
    ALWAYS("Always 3Tick (VERY DANGEROUS!)"),
    MOSTLY("Mostly 3Tick"),
    SOMETIMES("Sometimes 3Tick"),
    NEVER("Never 3Tick");

    private final String label;

    ThreeTickFrequencyMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean startsInThreeTick() {
        return this != NEVER;
    }

    public boolean switchingEnabled() {
        return this == MOSTLY || this == SOMETIMES;
    }

    public static ThreeTickFrequencyMode fromOptionString(String value) {
        if (value != null) {
            for (ThreeTickFrequencyMode mode : values()) {
                if (mode.label.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        return SOMETIMES;
    }
}
