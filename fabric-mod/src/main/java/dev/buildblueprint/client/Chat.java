package dev.buildblueprint.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Messages shown only to you in chat. */
public final class Chat {
    private Chat() {}

    public static void say(ChatFormatting color, String text) {
        text = Tr.t(text);   // in the game's language
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** A short message above the hotbar (used by the keybinds so chat isn't spammed). */
    public static void bar(String text) {
        text = Tr.t(text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(text));
    }

    // the sounds you can pick (Settings > Style): a layer is done, the whole build is done
    public static final String[] LAYER_SOUNDS = {"Orb", "Bell", "Sparkle", "Off"};
    public static final String[] BUILD_SOUNDS = {"Level up", "Challenge", "Bell", "Sparkle", "Off"};

    /** A little sound when a layer (or the whole build) is finished. */
    public static void ding(boolean big) { play(big, big ? Settings.buildSound : Settings.layerSound); }

    /** Plays one of the sounds above (also used to try them out in Settings). */
    public static void play(boolean big, int which) {
        if (!Settings.sounds) return;
        String name = (big ? BUILD_SOUNDS : LAYER_SOUNDS)[Math.max(0, Math.min(which, (big ? BUILD_SOUNDS : LAYER_SOUNDS).length - 1))];
        net.minecraft.sounds.SoundEvent ev;
        switch (name) {
            case "Level up": ev = net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP; break;
            case "Challenge": ev = net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE; break;
            case "Bell": ev = net.minecraft.sounds.SoundEvents.BELL_BLOCK; break;
            case "Sparkle": ev = net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_TWINKLE; break;
            case "Orb": ev = net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP; break;
            default: return;   // Off
        }
        float pitch = name.equals("Orb") ? 1.2f : 1f, vol = Math.max(0, Math.min(100, Settings.soundVolume)) / 100f;
        if (vol <= 0) return;
        try {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, pitch, vol));
        } catch (Exception ignored) {}
    }

    public static void say(String text) { say(ChatFormatting.WHITE, text); }
    public static void ok(String text) { say(ChatFormatting.GREEN, text); }
    public static void warn(String text) { say(ChatFormatting.GOLD, text); }
    public static void err(String text) { say(ChatFormatting.RED, text); }
}
