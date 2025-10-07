package org.illumine.sandcrabs;

import org.powbot.api.Tile;

/**
 * Immutable metadata for a Sand Crab spot.
 */
public final class SpotInfo {

    private final Tile tile;
    private final boolean hasThreeCrabs;

    public SpotInfo(Tile tile, boolean hasThreeCrabs) {
        this.tile = tile;
        this.hasThreeCrabs = hasThreeCrabs;
    }

    public Tile getTile() {
        return tile;
    }

    public boolean hasThreeCrabs() {
        return hasThreeCrabs;
    }
}

