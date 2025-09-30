**Template Task Script Usage**
- Copy package `org.illumine.templatetask` to your new package (e.g., `org.illumine.sandcrabs`).
- Rename `TemplateTaskScript` and update `@ScriptManifest` (name, description, category, version).
- Create your tasks in `org.illumine.sandcrabs.tasks` implementing `org.illumine.taskscript.Task`.
  - Constructor should accept your script: `public YourTask(YourScript script)`.
  - Implement `validate()`, `run()`, and optionally override `status()`.
- Register tasks in `createTasks()` using `addAll(...)` in priority order; keep the heartbeat last if used.
- Expose state via `getCurrentStatus()`; tasks can update it with `setCurrentStatus(...)`.
- Build: `cd illumine-powbot && ./gradlew :illumine-powbot:build` (run with elevated permissions).
- Start locally via `main` or `ScriptUploader`.

Example registration
```
@Override
protected List<Task> createTasks() {
    Task eat = new EatFoodTask(this);
    Task fight = new FightNpcTask(this);
    Task heartbeat = new TemplateHeartbeatTask(this); // optional fallback
    return addAll(eat, fight, heartbeat);
}
```

Key classes
- `org.illumine.taskscript.Task` — minimal task interface.
- `org.illumine.taskscript.TaskScript` — base script with ordered task loop, idle throttle, and status.
- `org.illumine.templatetask.TemplateTaskScript` — copy this as your starting point.
- `org.illumine.templatetask.tasks.TemplateHeartbeatTask` — optional “idle heartbeat” example task.

