package dev.fallingcloud.slate.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/slate/core.json}. Public fields with initialiser defaults (see {@link JsonConfig}).
 * The Config module edits these through its Interface page; Core's own small settings page covers the
 * essentials when Config is not installed.
 */
public final class CoreConfig {

    /** {@code DARK} or {@code VANILLA}. */
    public String skin = "DARK";
    /** Accent colour as {@code #RRGGBB}. */
    public String accent = "#D9805E";
    /** Animation speed multiplier: 0 disables motion, 1 default, 2 slow. */
    public double motion = 1.0;
    /** Use the Pixelify Sans heading font (false = vanilla font everywhere). */
    public boolean headingFont = true;
    /** Cross-screen fade. */
    public boolean transitions = true;
    /** Subtle click/hover sounds on Slate widgets. */
    public boolean uiSounds = true;
    /** Slate toasts (friend online, message received, ...). */
    public boolean toasts = true;
    /** Corner radius in pixels for the dark skin (0-4). */
    public int radius = 3;
    /** Blur the world behind in-game menus (vanilla does; some players prefer a plain dim). */
    public boolean blurInGame = true;

    /** Dev mode: the layout editor is reachable (pencil button on editable screens + the keybind). */
    public boolean devMode = false;
    /** Show the editor's grid by default. */
    public boolean devGrid = true;
    /** Editor snap size in pixels. */
    public int devSnap = 4;

    /**
     * Which screens Core restyles (widgets, backgrounds, tooltips, lists):
     * {@code VANILLA_AND_SLATE} (vanilla screens + Slate's own), {@code ALLOWLIST} (plus screens whose
     * class package starts with one of {@link #reskinAllowlist}), {@code ALL_NON_CONTAINER}, {@code NONE}.
     */
    public String reskinScope = "ALLOWLIST";
    /** Package prefixes / mod ids whose screens get restyled under ALLOWLIST. */
    public List<String> reskinAllowlist = new ArrayList<>(List.of(
        "me.shedaniel.clothconfig2", "dev.isxander.yacl3", "com.mrcrayfish.configured",
        "net.neoforged.neoforge.client.gui", "com.terraformersmc.modmenu", "com.blamejared.controlling",
        "com.mojang.realmsclient", "net.caffeinemc.mods.sodium.client.gui", "net.irisshaders.iris.gui"));
    /** Screens (class name prefixes) never restyled even under ALL_NON_CONTAINER. */
    public List<String> reskinDenylist = new ArrayList<>(List.of(
        "de.keksuccino.fancymenu", "mezz.jei", "com.simibubi.create", "xaero"));
    /**
     * Dark skin: inventory and container screens (chests, furnaces, machines, ...) get Slate's panel in place of
     * their background texture, slot wells, light labels and Slate tooltips ({@code ContainerReskin}). A screen that
     * draws its background in pieces keeps its own look; the creative inventory and the deny-list are never touched.
     */
    public boolean reskinContainers = true;

    public boolean isVanillaSkin() {
        return "VANILLA".equalsIgnoreCase(skin);
    }
}
