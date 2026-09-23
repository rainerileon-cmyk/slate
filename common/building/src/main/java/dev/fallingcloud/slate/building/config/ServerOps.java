package dev.fallingcloud.slate.building.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code building-server.json → ops}: building-mode rules and limits (design §7). Arrays marked "per tier" hold
 * four values for tool tiers 1..4; read them with {@code ToolTier.index(array, tier)}.
 *
 * <p>Owner: D1 (ops server). Skeleton declares the fields and defaults of design §10.
 */
public final class ServerOps {

    /** Master switch for building modes on this server. */
    public boolean enabled = true;
    /** Modes need the unlocking tool in a carried toolbox (false: every mode is available at tier 4). */
    public boolean requireToolbox = true;
    /** Creative players get everything at tier 4 without a toolbox. */
    public boolean creativeBypass = true;
    /** Most positions per operation, per tier. */
    public int[] maxVolume = {256, 2048, 16384, 65536};
    /** Longest selection edge, per tier. */
    public int[] maxSpan = {16, 32, 64, 128};
    /** Extra anchor reach, per tier. */
    public int[] reachBonus = {0, 8, 16, 32};
    /** Execution speed of one operation, per tier. */
    public int[] blocksPerTick = {8, 24, 64, 160};
    /** Execution budget shared by every running operation, per tick. */
    public int globalBlocksPerTick = 2048;
    /** Volume cap for creative players. */
    public int creativeMaxVolume = 262144;
    /** Undo steps kept per player. */
    public int undoDepth = 10;
    /** Extra undo steps per Memory upgrade. */
    public int undoPerMemory = 10;
    /** Total blocks remembered in one player's history. */
    public int maxUndoBlocks = 200000;
    /** One point of tool durability per this many blocks changed. */
    public int durabilityPerBlocks = 4;
    /** Place what the player can afford instead of refusing an operation they cannot fully pay. */
    public boolean placeWhatYouCan = true;
    /** Ask claim/protection mods (loader break/place events) for every position. */
    public boolean respectClaims = true;
    /** Allow operations to touch block entities (chests, ...) in survival. */
    public boolean allowBlockEntities = false;
    /** Mode ids disabled on this server. */
    public List<String> disabledModes = new ArrayList<>();
    /** Permission level needed to paste (0: everyone). */
    public int pasteOpLevel = 0;
}
