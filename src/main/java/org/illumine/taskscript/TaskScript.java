package org.illumine.taskscript;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.script.AbstractScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Base script that runs a fixed, ordered list of {@link Task} instances.
 *
 * Subclasses must implement {@link #createTasks()} to provide the task list at startup.
 */
public abstract class TaskScript extends AbstractScript {

    protected List<Task> tasks;
    private volatile String currentStatus = "Initialising";

    /**
     * Provide the ordered list of tasks to run. Called once during {@link #onStart()}.
     */
    protected abstract List<Task> createTasks();

    /**
     * Get the current status string for overlays/diagnostics.
     */
    public String getCurrentStatus() {
        return currentStatus;
    }

    /**
     * Allow tasks or scripts to update the current status.
     */
    public void setCurrentStatus(String status) {
        this.currentStatus = status;
    }

    @Override
    public void onStart() {
        try {
            tasks = Objects.requireNonNull(createTasks(), "createTasks() returned null");
            if (getLog() != null) {
                getLog().info("Loaded tasks: " + tasks.size());
                for (int i = 0; i < tasks.size(); i++) {
                    Task t = tasks.get(i);
                    getLog().info("  [" + i + "] " + (t != null ? t.getClass().getSimpleName() : "<null>"));
                }
            }
        } catch (Exception e) {
            if (getLog() != null) {
                getLog().log(Level.SEVERE, "Error in onStart while creating tasks", e);
            }
        }
    }

    @Override
    public void poll() {
        if (tasks == null || tasks.isEmpty()) {
            setCurrentStatus("Idle");
            Condition.sleep(Random.nextInt(25, 41));
            return;
        }

        for (Task task : tasks) {
            if (task == null) continue;
            boolean shouldRun = false;
            try {
                shouldRun = task.validate();
            } catch (Exception e) {
                if (getLog() != null) {
                    getLog().log(Level.SEVERE, "Exception in validate() of " + task.getClass().getSimpleName(), e);
                }
            }

            if (shouldRun) {
                String status;
                try {
                    status = task.status();
                } catch (Exception e) {
                    status = task.getClass().getSimpleName();
                }
                setCurrentStatus(status);
                try {
                    task.run();
                } catch (Exception e) {
                    if (getLog() != null) {
                        getLog().log(Level.SEVERE, "Exception in run() of " + task.getClass().getSimpleName(), e);
                    }
                }
                return; // Run only one task per poll
            }
        }

        // No tasks validated
        setCurrentStatus("Idle");
        Condition.sleep(Random.nextInt(0, 25));
    }

    @Override
    public void onStop() {
        setCurrentStatus("Stopped");
        if (getLog() != null) {
            getLog().info("TaskScript stopped.");
        }
    }

    /**
     * Convenience builder to create an ordered immutable-like list of tasks
     * during {@link #createTasks()}.
     *
     * Example:
     * <pre>
     *     return addAll(new TaskA(this), new TaskB(this));
     * </pre>
     */
    protected final List<Task> addAll(Task... toAdd) {
        ArrayList<Task> list = new ArrayList<>();
        if (toAdd != null) {
            for (Task t : toAdd) {
                if (t != null) list.add(t);
            }
        }
        return list;
    }
}
