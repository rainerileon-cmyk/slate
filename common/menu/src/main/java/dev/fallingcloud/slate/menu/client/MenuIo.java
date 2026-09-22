package dev.fallingcloud.slate.menu.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * The module's background pool: disk walks (world sizes, screenshot listing), folder copies, server
 * pings (DNS resolution blocks), clipboard work. Daemon threads so a hung ping never keeps the JVM alive.
 */
public final class MenuIo {

    private static final AtomicInteger N = new AtomicInteger();
    public static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        final Thread t = new Thread(r, "slate-menu-io-" + N.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** Run on the render thread (now if already there, else queued). */
    public static void onClient(final Runnable r) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) r.run();
        else mc.execute(r);
    }

    private MenuIo() {}
}
