package org.illumine.barb3tickfishing;

import org.powbot.api.Condition;
import org.powbot.api.Random;
import org.powbot.api.rt4.Inventory;
import org.powbot.api.rt4.Item;

class SuppliesManager {
    private final Barb3TickFishingScript script;

    private String herbName;
    private boolean fallbackTriggered = false;

    SuppliesManager(Barb3TickFishingScript script) {
        this.script = script;
        this.herbName = Barb3TickFishingScript.DEFAULT_HERB_NAME;
    }

    void reset() {
        herbName = Barb3TickFishingScript.DEFAULT_HERB_NAME;
        fallbackTriggered = false;
    }

    void setHerbName(String herbName) {
        if (herbName == null) {
            this.herbName = Barb3TickFishingScript.DEFAULT_HERB_NAME;
            return;
        }
        String trimmed = herbName.trim();
        this.herbName = trimmed.isEmpty() ? Barb3TickFishingScript.DEFAULT_HERB_NAME : trimmed;
    }

    String herbName() {
        return herbName;
    }

    boolean hasThreeTickSuppliesAvailable() {
        return hasItem("Swamp tar") && canObtainCleanHerb();
    }

    boolean ensureSuppliesForActiveMode() {
        return ensureSupplies(true);
    }

    boolean ensureSuppliesForUpcomingMode() {
        return ensureSupplies(true);
    }

    void handleOutOfSupplies(String missingItem, boolean shouldSwitchToNormal) {
        if (fallbackTriggered) {
            return;
        }
        if (missingItem == null || missingItem.isBlank()) {
            missingItem = determineMissingSupply();
        }
        if (missingItem.isBlank()) {
            missingItem = "3T supplies";
        }
        if (!shouldSwitchToNormal) {
            script.log("Stopping, out of 3T supplies: " + missingItem);
            script.getController().stop();
            return;
        }
        script.log("Fallback, out of 3T supplies (" + missingItem + "), switching to normal fishing");
        fallbackTriggered = true;
        script.switchToPermanentNormalMode();
    }

    String determineMissingSupply() {
        if (!hasItem("Swamp tar")) {
            return "Swamp tar";
        }
        if (!canObtainCleanHerb()) {
            return herbName;
        }
        return "";
    }

    private boolean ensureSupplies(boolean attemptClean) {
        if (!hasItem("Swamp tar")) {
            handleOutOfSupplies("Swamp tar", script.getConfig().switchToNormalOnSuppliesOut());
            return false;
        }
        if (!hasItem(herbName)) {
            if (attemptClean && cleanHerb()) {
                Condition.sleep(Random.nextInt(200, 3000));
                return false;
            }
            handleOutOfSupplies(herbName, script.getConfig().switchToNormalOnSuppliesOut());
            return false;
        }
        return true;
    }

    private boolean canObtainCleanHerb() {
        if (hasItem(herbName)) {
            return true;
        }
        Item cleanable = Inventory.stream().nameContains("Grimy").action("Clean").first();
        return cleanable.valid();
    }

    private boolean hasItem(String name) {
        Item item = Inventory.stream().name(name).first();
        return item.valid();
    }

    private boolean cleanHerb() {
        Item cleanHerb = Inventory.stream().nameContains("Grimy").action("Clean").first();
        if (cleanHerb.valid()) {
            cleanHerb.interact("Clean");
            return true;
        }
        return false;
    }
}
