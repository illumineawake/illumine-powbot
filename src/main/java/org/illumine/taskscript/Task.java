package org.illumine.taskscript;

/**
 * Minimal task contract for Powbot task scripts.
 *
 * Tasks are evaluated in order each poll; the first task whose {@link #validate()} returns true
 * will have its {@link #run()} executed.
 */
public interface Task {

    /**
     * Decide whether this task should run now.
     *
     * @return true if this task should run on this poll
     */
    boolean validate();

    /**
     * Execute the task logic. Keep this lightweight; avoid blocking waits when possible.
     */
    void run();

    /**
     * A short status string for display/logging when this task is running.
     *
     * @return human-readable status
     */
    default String status() {
        return getClass().getSimpleName();
    }
}

