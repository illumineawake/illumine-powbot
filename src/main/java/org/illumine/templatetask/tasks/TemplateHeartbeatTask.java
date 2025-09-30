package org.illumine.templatetask.tasks;

import org.illumine.taskscript.Task;
import org.illumine.taskscript.TaskScript;

public class TemplateHeartbeatTask implements Task {

    private final TaskScript script;
    private long lastLog = 0L;

    public TemplateHeartbeatTask(TaskScript script) {
        this.script = script;
    }

    @Override
    public String status() {
        return "Heartbeat";
    }

    @Override
    public boolean validate() {
        long now = System.currentTimeMillis();
        return now - lastLog >= 2000L;
    }

    @Override
    public void run() {
        lastLog = System.currentTimeMillis();
        if (script.getLog() != null) {
            script.getLog().info("Idle heartbeat...");
        }
        script.setCurrentStatus("Idle - Heartbeat");
    }
}

