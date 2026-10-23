package dev.buildblueprint.core;

/** A problem with pasted blueprint data, worded for the player. */
public class BlueprintException extends Exception {
    private static final long serialVersionUID = 1L;
    public BlueprintException(String message) { super(message); }
}
