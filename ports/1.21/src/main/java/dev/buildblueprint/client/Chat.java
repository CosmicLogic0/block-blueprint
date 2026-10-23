package dev.buildblueprint.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Messages shown only to you in chat. */
public final class Chat {
    private Chat() {}

    public static void say(ChatFormatting color, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) V.chat(mc.player, Component.literal(text).withStyle(color));
    }

    /** A short message above the hotbar (used by the keybinds so chat isn't spammed). */
    public static void bar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    /** A little sound when a layer (or the whole build) is finished. */
    public static void ding(boolean big) {
        if (!Settings.sounds) return;
        try {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    big ? net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP : net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, big ? 1f : 1.2f, 0.6f));
        } catch (Exception ignored) {}
    }

    public static void say(String text) { say(ChatFormatting.WHITE, text); }
    public static void ok(String text) { say(ChatFormatting.GREEN, text); }
    public static void warn(String text) { say(ChatFormatting.GOLD, text); }
    public static void err(String text) { say(ChatFormatting.RED, text); }
}
