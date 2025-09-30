package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.illumine.taskscript.Task;
import org.powbot.api.Tile;
import org.powbot.api.rt4.Player;
import org.powbot.api.rt4.Players;

public abstract class SandCrabsTask implements Task {

    protected final SandCrabsScript script;

    protected SandCrabsTask(SandCrabsScript script) {
        this.script = script;
    }

    protected Player local() {
        Player player = Players.local();
        return player != null && player.valid() ? player : null;
    }

    protected Tile localTile() {
        Player player = local();
        return player != null ? player.tile() : null;
    }

    protected boolean isOnTile(Tile tile) {
        Tile current = localTile();
        return tile != null && current != null && current.equals(tile);
    }
}
