package org.illumine.barb3tickfishing;

import org.powbot.api.script.paint.PaintBuilder;
import org.powbot.api.rt4.walking.model.Skill;

class Barb3TickPaint {
    private final Barb3TickFishingScript script;

    Barb3TickPaint(Barb3TickFishingScript script) {
        this.script = script;
    }

    void apply() {
        script.addPaint(PaintBuilder.newBuilder()
                .x(20)
                .y(40)
                .trackSkill(Skill.Fishing)
                .trackSkill(Skill.Agility)
                .trackSkill(Skill.Strength)
                .addString("Mode: ", () -> script.isTickFishing() ? "3Tick Fishing" : "Normal Fishing")
                .addString("3T Frequency: ", script::getFrequencyDisplay)
                .addString("Time(%) Spent 3Tick Fishing: ", script::formatThreeTickShare)
                .addString("Switching fishing mode in: ", script::formatSwitchCountdown)
                .addString("World: ", script.getWorldHopController()::formatCurrentWorld)
                .addString("Next Hop: ", script.getWorldHopController()::formatTimeToHop)
                .build());
    }

    static String formatMs(long msRemaining) {
        if (msRemaining <= 0) {
            return "00:00";
        }
        long totalSeconds = msRemaining / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
