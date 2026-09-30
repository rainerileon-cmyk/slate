package dev.fallingcloud.slate.menu.client.overhaul.play;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Play screen's own two workers, for what it reads from disk: a world's level.dat, the regions its map is drawn
 * from, a map mod's waypoints. Kept apart from the module's pool, which the same screen fills with server pings: a
 * ping waits on the network for seconds, and a map must not wait behind five of them.
 */
public final class PlayIo {

    private static final AtomicInteger N = new AtomicInteger();
    public static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        final Thread t = new Thread(r, "slate-play-io-" + N.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private PlayIo() {}
}
