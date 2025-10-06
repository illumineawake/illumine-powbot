package org.illumine.sandcrabs;

import org.powbot.api.rt4.Inventory;

/**
 * Aggregates the Sand Crabs config, mutable state, and manager classes so that
 * individual tasks can depend on a single context rather than reaching back
 * into the script for every interaction.
 */
public class SandCrabsContext {

    private volatile SandCrabsConfig config;
    private final SandCrabsState state;
    private final CombatMonitor combatMonitor;
    private final SpotManager spotManager;
    private final LevellingService levellingService;

    public SandCrabsContext(SandCrabsConfig config,
                            SandCrabsState state,
                            CombatMonitor combatMonitor,
                            SpotManager spotManager,
                            LevellingService levellingService) {
        this.config = config;
        this.state = state;
        this.combatMonitor = combatMonitor;
        this.spotManager = spotManager;
        this.levellingService = levellingService;
    }

    public SandCrabsConfig config() {
        return config;
    }

    public SandCrabsState state() {
        return state;
    }

    public CombatMonitor combatMonitor() {
        return combatMonitor;
    }

    public SpotManager spotManager() {
        return spotManager;
    }

    public LevellingService levellingService() {
        return levellingService;
    }

    public void updateConfig(SandCrabsConfig config) {
        this.config = config;
        combatMonitor.updateConfig(config);
        spotManager.updateConfig(config);
        levellingService.updateConfig(config);
    }

    public boolean hasRequiredFoodInInventory() {
        SandCrabsConfig cfg = config;
        if (!cfg.isUseFood()) {
            return false;
        }
        String foodName = cfg.getFoodName();
        if (foodName == null || foodName.isEmpty()) {
            return false;
        }
        return Inventory.stream().name(foodName).first().valid();
    }

    public boolean isConfiguredFood(String itemName) {
        SandCrabsConfig cfg = config;
        return cfg.isUseFood()
                && itemName != null
                && !cfg.getFoodName().isEmpty()
                && itemName.equalsIgnoreCase(cfg.getFoodName());
    }
}

