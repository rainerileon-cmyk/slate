package dev.fallingcloud.slate.core.stage.scene;

import dev.fallingcloud.slate.core.stage.node.StageNode;
import org.joml.Vector3f;

/**
 * A named place in a scene where code puts an interactive node: position in scene blocks and a rotation in degrees
 * (yaw around Y, then pitch, then roll). {@link #place} applies it to a node.
 */
public record Anchor(String name, Vector3f position, float yaw, float pitch, float roll) {

    public Anchor(final String name, final float x, final float y, final float z, final float yaw) {
        this(name, new Vector3f(x, y, z), yaw, 0f, 0f);
    }

    public float x() { return position.x; }

    public float y() { return position.y; }

    public float z() { return position.z; }

    /** Moves and rotates the node to this anchor; returns the node for chaining. */
    public <T extends StageNode> T place(final T node) {
        node.at(position.x, position.y, position.z);
        node.rotate(yaw, pitch, roll);
        return node;
    }

    /** The same anchor shifted by a local offset (blocks). */
    public Anchor offset(final float dx, final float dy, final float dz) {
        return new Anchor(name, new Vector3f(position.x + dx, position.y + dy, position.z + dz), yaw, pitch, roll);
    }
}
