package dev.fallingcloud.slate.menu.client.play.model;

import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.List;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import org.jetbrains.annotations.Nullable;

/** Vanilla LAN discovery (multicast listener thread) wrapped so the screen can poll it per tick. */
public final class LanScanner {

    private final LanServerDetection.LanServerList list = new LanServerDetection.LanServerList();
    @Nullable private LanServerDetection.LanServerDetector detector;

    public LanScanner() {
        try {
            detector = new LanServerDetection.LanServerDetector(list);
            detector.start();
        } catch (final Exception e) {
            SlateMenu.LOGGER.warn("[Slate Menu] LAN detection unavailable: {}", e.toString());
            detector = null;
        }
    }

    /** The current LAN servers when something changed since the last poll, else null. */
    @Nullable
    public List<LanServer> poll() {
        return list.takeDirtyServers();
    }

    public boolean isRunning() {
        return detector != null && detector.isAlive();
    }

    public void close() {
        if (detector != null) detector.interrupt();
        detector = null;
    }
}
