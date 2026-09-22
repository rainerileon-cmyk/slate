package dev.fallingcloud.slate.multiplayer.social;

/** A pending friend request (incoming: {@code ref} is who asked; outgoing: who was asked). */
public record RequestInfo(PlayerRef ref, long atMs) {}
