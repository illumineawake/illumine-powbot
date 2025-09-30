package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsScript;
import org.powbot.api.rt4.Combat;
import org.powbot.api.Tile;

public class AttackTask extends SandCrabsTask {

    private static final String STATUS = "Holding camp";

    public AttackTask(SandCrabsScript script) {
        super(script);
    }

    @Override
    public boolean validate() {
        Tile camp = script.getCurrentCampTile();
        return camp != null && isOnTile(camp);
    }

    @Override
    public void run() {
        if (!Combat.autoRetaliate()) {
            Combat.autoRetaliate(true);
        }

        Tile camp = script.getCurrentCampTile();
        if (camp == null) {
            return;
        }
    }

    @Override
    public String status() {
        return STATUS;
    }
}
