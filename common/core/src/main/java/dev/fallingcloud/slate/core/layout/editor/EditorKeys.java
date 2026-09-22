package dev.fallingcloud.slate.core.layout.editor;

import net.minecraft.client.gui.screens.Screen;

/** GLFW key codes the editor cares about, and the modifier queries (Ctrl means Cmd on macOS). */
final class EditorKeys {

    static final int ESCAPE = 256, ENTER = 257, TAB = 258, BACKSPACE = 259, DELETE = 261;
    static final int RIGHT = 262, LEFT = 263, DOWN = 264, UP = 265, HOME = 268, END = 269;
    static final int F1 = 290, F12 = 301, KP_ENTER = 335;
    static final int A = 65, C = 67, D = 68, G = 71, L = 76, P = 80, S = 83, V = 86, Y = 89, Z = 90;

    static boolean ctrl() { return Screen.hasControlDown(); }

    static boolean shift() { return Screen.hasShiftDown(); }

    static boolean alt() { return Screen.hasAltDown(); }

    static boolean isEnter(final int key) { return key == ENTER || key == KP_ENTER; }

    private EditorKeys() {}
}
