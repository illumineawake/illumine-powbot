package org.illumine.sandcrabs;

import org.powbot.api.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised list of known Sand Crab spots with metadata.
 */
public final class SandCrabSpots {

    private SandCrabSpots() {
    }

    /**
     * Hosidius beach spots and whether each has three crabs available.
     * Coordinates mirror SandCrabsScript.HOSIDIUS_SPOT_TILES.
     */
    public static final List<SpotInfo> HOSIDIUS_SPOTS = List.of(
            new SpotInfo(new Tile(1776, 3468, 0), true), 
            new SpotInfo(new Tile(1773, 3461, 0), true),
            new SpotInfo(new Tile(1765, 3468, 0), false),
            new SpotInfo(new Tile(1749, 3469, 0), true),
            new SpotInfo(new Tile(1738, 3468, 0), true)
    );

    /**
     * Convenience accessor for just the tiles of Hosidius spots.
     */
    public static List<Tile> hosidiusTiles() {
        List<Tile> tiles = new ArrayList<>(HOSIDIUS_SPOTS.size());
        for (SpotInfo s : HOSIDIUS_SPOTS) {
            tiles.add(s.getTile());
        }
        return tiles;
    }

    public static boolean isThreeCrabSpot(Tile tile) {
        if (tile == null) {
            return false;
        }
        for (SpotInfo s : HOSIDIUS_SPOTS) {
            if (s.getTile().equals(tile)) {
                return s.hasThreeCrabs();
            }
        }
        return false;
    }

    public static boolean hasMoreThanThreeCrabs(Tile tile) {
        return !isThreeCrabSpot(tile);
    }
}
