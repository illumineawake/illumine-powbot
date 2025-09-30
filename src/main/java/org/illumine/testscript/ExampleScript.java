package org.illumine.testscript;

import org.powbot.api.Condition;
import org.powbot.api.rt4.*;
import org.powbot.api.script.*;
import org.powbot.mobile.service.ScriptUploader;

// Example of GUI option which asks for a string
@ScriptConfiguration(name = "NPC", description = "NPC to fight", optionType = OptionType.STRING)
@ScriptConfiguration(name = "Example GUI option", description = "Example GUI option", optionType = OptionType.STRING, visible = false)

@ScriptManifest(name = "Example Script", description = "Example Script", author = "illumine", category = ScriptCategory.Fletching, version = "1")
public class ExampleScript extends AbstractScript {

    private String GuiNPC; // Creates an empty
    @Override
    public void onStart() {
        // Logic to happen before poll() starts
//        boolean GuiNPC = getOption("NPC"); // Gets value of specific GUI key and saves it in a variable.
    }

    @Override
    public void poll() {
        // Looped tasks go here. Example below is of a simple fighter script
        System.out.println("polling");
        if(!Players.local().interacting().valid()) { // Check for if you're not interacting with an NPC. Else skips what's below and keeps looping until you are not interacting with an NPC.
            Npc fightNpc = Npcs.stream().name(GuiNPC).filtered(it -> !it.interacting().valid()).nearest().first(); // Gets the nearest NPC that's not interacting with anyone else / is not in combat
            if(fightNpc.valid() && fightNpc.interact("Attack")) { // Checks if NPC is valid and then interacts with it
                Condition.wait(() -> Players.local().interacting().valid(), 200, 200); // If above was successful, waits until you are interacting with the NPC
            }
        }
    }

    @ValueChanged(keyName = "NPC") // Listens for changes to the specific GUI config key.
    private void exampleConfigChanged(Boolean valueChanged) {
        updateVisibility("Example GUI option", true); // Changes visibility of the second GUI config key if another key is modified
    }

    public static void main(String[] args) {
//        new ScriptUploader().uploadAndStart("Example Script", "", "localhost:5555", false, false);
        // Start your script with this function. Make sure your device is connected via ADB, and only one is connected
        new ExampleScript().startScript();
    }
}

