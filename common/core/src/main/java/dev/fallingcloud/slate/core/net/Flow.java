package dev.fallingcloud.slate.core.net;

/** Which way a payload travels. */
public enum Flow {
    /** client to server */ C2S,
    /** server to client */ S2C,
    /** both, one registration */ BOTH;

    public boolean toServer() { return this != S2C; }

    public boolean toClient() { return this != C2S; }
}
