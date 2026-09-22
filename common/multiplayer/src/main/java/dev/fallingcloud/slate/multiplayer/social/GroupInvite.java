package dev.fallingcloud.slate.multiplayer.social;

/** A pending invitation into a friend group. */
public record GroupInvite(GroupInfo group, PlayerRef from, long atMs) {}
