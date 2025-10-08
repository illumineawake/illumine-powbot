package org.illumine.templatetask;

import org.illumine.taskscript.Task;
import org.illumine.taskscript.TaskScript;
import org.illumine.templatetask.tasks.TemplateHeartbeatTask;
import org.powbot.api.script.OptionType;
import org.powbot.api.script.ScriptCategory;
import org.powbot.api.script.ScriptConfiguration;
import org.powbot.api.script.ScriptManifest;

import java.util.List;

@ScriptConfiguration(name = "Enable Heartbeat", description = "Log idle heartbeat every ~2s", optionType = OptionType.BOOLEAN)
@ScriptManifest(name = "Template Task Script", description = "Starter template using TaskScript base", author = "illumine", category = ScriptCategory.Other, version = "1")
public class TemplateTaskScript extends TaskScript {

    @Override
    protected List<Task> createTasks() {
        // Add your tasks in priority order here
        // Task first = new YourFirstTask(this);
        // Task second = new YourSecondTask(this);

        Boolean enableHeartbeat = getOption("Enable Heartbeat");
        Task heartbeat = Boolean.TRUE.equals(enableHeartbeat) ? new TemplateHeartbeatTask(this) : null;

        // Keep heartbeat last so it only runs when nothing else validates
        return addAll(
                // first,
                // second,
                heartbeat
        );
    }

    public static void main(String[] args) {
        // Start your script with this function. Ensure only one device is connected via ADB.
        new TemplateTaskScript().startScript();
        // Alternatively, use ScriptUploader if desired:
        // new ScriptUploader().uploadAndStart("Template Task Script", "", "localhost:5555", false, false);
    }
}
