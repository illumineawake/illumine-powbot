package org.illumine.sandcrabs;

import org.powbot.api.Area;
import org.powbot.api.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration for a Sand Crabs script run. Values are populated once during
 * script start (or when the user adjusts settings) and shared across the collaborating
 * services handling combat, levelling, and spot selection.
 */
public final class SandCrabsConfig {

    private final List<Tile> spotTiles;
    private final Area resetArea;
    private final Tile bankTile;
    private final boolean useFood;
    private final String foodName;
    private final int eatMinPercent;
    private final int eatMaxPercent;
    private final boolean levellingEnabled;
    private final String levellingMode;
    private final int maxAttackLevel;
    private final int maxStrengthLevel;
    private final int maxDefenceLevel;
    private final int keepWithinLevels;
    private final int minNoCombatSeconds;
    private final int maxNoCombatSeconds;
    private final long worldHopCooldownMillis;
    private final long dormantWarningDelayMillis;
    private final long spotCrashThresholdMillis;
    private final boolean stopWhenOutOfPotions;

    private SandCrabsConfig(Builder builder) {
        this.spotTiles = Collections.unmodifiableList(new ArrayList<>(builder.spotTiles));
        this.resetArea = builder.resetArea;
        this.bankTile = builder.bankTile;
        this.useFood = builder.useFood;
        this.foodName = builder.foodName;
        this.eatMinPercent = builder.eatMinPercent;
        this.eatMaxPercent = builder.eatMaxPercent;
        this.levellingEnabled = builder.levellingEnabled;
        this.levellingMode = builder.levellingMode;
        this.maxAttackLevel = builder.maxAttackLevel;
        this.maxStrengthLevel = builder.maxStrengthLevel;
        this.maxDefenceLevel = builder.maxDefenceLevel;
        this.keepWithinLevels = builder.keepWithinLevels;
        this.minNoCombatSeconds = builder.minNoCombatSeconds;
        this.maxNoCombatSeconds = builder.maxNoCombatSeconds;
        this.worldHopCooldownMillis = builder.worldHopCooldownMillis;
        this.dormantWarningDelayMillis = builder.dormantWarningDelayMillis;
        this.spotCrashThresholdMillis = builder.spotCrashThresholdMillis;
        this.stopWhenOutOfPotions = builder.stopWhenOutOfPotions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Tile> getSpotTiles() {
        return spotTiles;
    }

    public Area getResetArea() {
        return resetArea;
    }

    public Tile getBankTile() {
        return bankTile;
    }

    public boolean isUseFood() {
        return useFood;
    }

    public String getFoodName() {
        return foodName;
    }

    public int getEatMinPercent() {
        return eatMinPercent;
    }

    public int getEatMaxPercent() {
        return eatMaxPercent;
    }

    public boolean isLevellingEnabled() {
        return levellingEnabled;
    }

    public String getLevellingMode() {
        return levellingMode;
    }

    public int getMaxAttackLevel() {
        return maxAttackLevel;
    }

    public int getMaxStrengthLevel() {
        return maxStrengthLevel;
    }

    public int getMaxDefenceLevel() {
        return maxDefenceLevel;
    }

    public int getKeepWithinLevels() {
        return keepWithinLevels;
    }

    public int getMinNoCombatSeconds() {
        return minNoCombatSeconds;
    }

    public int getMaxNoCombatSeconds() {
        return maxNoCombatSeconds;
    }

    public long getWorldHopCooldownMillis() {
        return worldHopCooldownMillis;
    }

    public long getDormantWarningDelayMillis() {
        return dormantWarningDelayMillis;
    }

    public long getSpotCrashThresholdMillis() {
        return spotCrashThresholdMillis;
    }

    public boolean isStopWhenOutOfPotions() {
        return stopWhenOutOfPotions;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private List<Tile> spotTiles = Collections.emptyList();
        private Area resetArea;
        private Tile bankTile;
        private boolean useFood;
        private String foodName = "";
        private int eatMinPercent;
        private int eatMaxPercent;
        private boolean levellingEnabled;
        private String levellingMode = "";
        private int maxAttackLevel;
        private int maxStrengthLevel;
        private int maxDefenceLevel;
        private int keepWithinLevels;
        private int minNoCombatSeconds;
        private int maxNoCombatSeconds;
        private long worldHopCooldownMillis;
        private long dormantWarningDelayMillis;
        private long spotCrashThresholdMillis;
        private boolean stopWhenOutOfPotions;

        private Builder() {
        }

        private Builder(SandCrabsConfig config) {
            this.spotTiles = config.spotTiles;
            this.resetArea = config.resetArea;
            this.bankTile = config.bankTile;
            this.useFood = config.useFood;
            this.foodName = config.foodName;
            this.eatMinPercent = config.eatMinPercent;
            this.eatMaxPercent = config.eatMaxPercent;
            this.levellingEnabled = config.levellingEnabled;
            this.levellingMode = config.levellingMode;
            this.maxAttackLevel = config.maxAttackLevel;
            this.maxStrengthLevel = config.maxStrengthLevel;
            this.maxDefenceLevel = config.maxDefenceLevel;
            this.keepWithinLevels = config.keepWithinLevels;
            this.minNoCombatSeconds = config.minNoCombatSeconds;
            this.maxNoCombatSeconds = config.maxNoCombatSeconds;
            this.worldHopCooldownMillis = config.worldHopCooldownMillis;
            this.dormantWarningDelayMillis = config.dormantWarningDelayMillis;
            this.spotCrashThresholdMillis = config.spotCrashThresholdMillis;
            this.stopWhenOutOfPotions = config.stopWhenOutOfPotions;
        }

        public Builder spotTiles(List<Tile> spotTiles) {
            this.spotTiles = Objects.requireNonNull(spotTiles, "spotTiles");
            return this;
        }

        public Builder resetArea(Area resetArea) {
            this.resetArea = Objects.requireNonNull(resetArea, "resetArea");
            return this;
        }

        public Builder bankTile(Tile bankTile) {
            this.bankTile = Objects.requireNonNull(bankTile, "bankTile");
            return this;
        }

        public Builder useFood(boolean useFood) {
            this.useFood = useFood;
            return this;
        }

        public Builder foodName(String foodName) {
            this.foodName = foodName == null ? "" : foodName;
            return this;
        }

        public Builder eatMinPercent(int eatMinPercent) {
            this.eatMinPercent = eatMinPercent;
            return this;
        }

        public Builder eatMaxPercent(int eatMaxPercent) {
            this.eatMaxPercent = eatMaxPercent;
            return this;
        }

        public Builder levellingEnabled(boolean levellingEnabled) {
            this.levellingEnabled = levellingEnabled;
            return this;
        }

        public Builder levellingMode(String levellingMode) {
            this.levellingMode = levellingMode == null ? "" : levellingMode;
            return this;
        }

        public Builder maxAttackLevel(int maxAttackLevel) {
            this.maxAttackLevel = maxAttackLevel;
            return this;
        }

        public Builder maxStrengthLevel(int maxStrengthLevel) {
            this.maxStrengthLevel = maxStrengthLevel;
            return this;
        }

        public Builder maxDefenceLevel(int maxDefenceLevel) {
            this.maxDefenceLevel = maxDefenceLevel;
            return this;
        }

        public Builder keepWithinLevels(int keepWithinLevels) {
            this.keepWithinLevels = keepWithinLevels;
            return this;
        }

        public Builder minNoCombatSeconds(int minNoCombatSeconds) {
            this.minNoCombatSeconds = minNoCombatSeconds;
            return this;
        }

        public Builder maxNoCombatSeconds(int maxNoCombatSeconds) {
            this.maxNoCombatSeconds = maxNoCombatSeconds;
            return this;
        }

        public Builder worldHopCooldownMillis(long worldHopCooldownMillis) {
            this.worldHopCooldownMillis = worldHopCooldownMillis;
            return this;
        }

        public Builder dormantWarningDelayMillis(long dormantWarningDelayMillis) {
            this.dormantWarningDelayMillis = dormantWarningDelayMillis;
            return this;
        }

        public Builder spotCrashThresholdMillis(long spotCrashThresholdMillis) {
            this.spotCrashThresholdMillis = spotCrashThresholdMillis;
            return this;
        }

        public Builder stopWhenOutOfPotions(boolean stop) {
            this.stopWhenOutOfPotions = stop;
            return this;
        }

        public SandCrabsConfig build() {
            Objects.requireNonNull(spotTiles, "spotTiles");
            Objects.requireNonNull(resetArea, "resetArea");
            Objects.requireNonNull(bankTile, "bankTile");
            Objects.requireNonNull(foodName, "foodName");
            Objects.requireNonNull(levellingMode, "levellingMode");
            return new SandCrabsConfig(this);
        }
    }
}

