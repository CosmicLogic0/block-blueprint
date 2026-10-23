package dev.buildblueprint.client;

/** 1.21 stand-in for the newer KeyEvent, so the screens can share code with the 26.x version. */
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean hasShiftDown() { return (modifiers & 1) != 0; }
    public boolean hasControlDown() { return (modifiers & 2) != 0; }
    public boolean hasAltDown() { return (modifiers & 4) != 0; }
}
