package org.illumine.sandcrabs.tasks;

import org.illumine.sandcrabs.SandCrabsContext;
import org.illumine.sandcrabs.SandCrabsScript;
import org.illumine.taskscript.Task;

public abstract class SandCrabsTask implements Task {

    protected final SandCrabsScript script;
    protected final SandCrabsContext context;

    protected SandCrabsTask(SandCrabsScript script, SandCrabsContext context) {
        this.script = script;
        this.context = context;
    }
}
