package dev.fallingcloud.slate.config.option;

/**
 * The kind of control an {@link OptionBinding} wants. Value types per kind: BOOLEAN = Boolean,
 * INT = Number (integer-valued), DOUBLE = Number, CHOICE = String (a {@link Choice#id()}), STRING = String,
 * COLOR = Integer (ARGB), LIST = List of String, KEYBIND = String (key name), ACTION/INFO = no value.
 */
public enum OptionType {
    BOOLEAN, INT, DOUBLE, CHOICE, STRING, COLOR, LIST, KEYBIND, ACTION, INFO;

    /** Whether values of this kind can be stored in a preset / applied later. */
    public boolean snapshotable() {
        return this != ACTION && this != INFO;
    }
}
