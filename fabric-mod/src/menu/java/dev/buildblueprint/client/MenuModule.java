package dev.buildblueprint.client;

import net.minecraft.client.Minecraft;

/** Opens the menus (called by name from Modules). Page Up: materials when a blueprint is loaded, otherwise saved builds. */
public final class MenuModule {
    private MenuModule() {}

    public static void open() {
        if (BuildBlueprintClient.SESSION.loaded()) openMaterials(); else openSaved();
    }
    public static void openMaterials() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Mc.setScreen(new MaterialsScreen()));
    }
    public static void openSettings(net.minecraft.client.gui.screens.Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Mc.setScreen(new SettingsScreen(parent)));
    }
    public static void openCopy() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Mc.setScreen(new CopyScreen()));
    }
    public static void openSaved() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> Mc.setScreen(new SavedBuildsScreen()));
    }
}
