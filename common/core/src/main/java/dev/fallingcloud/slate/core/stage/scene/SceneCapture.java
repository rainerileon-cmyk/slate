package dev.fallingcloud.slate.core.stage.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.core.Slate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a structure-block template the player saved in a singleplayer world into a scene: the camera is put at the
 * player's eye (relative to the structure's origin) looking where the player looks, every sign in the template whose
 * first line starts with {@code #} becomes an anchor (named by the rest of the line, positioned at the sign, facing
 * the way the sign faces) and is hidden from the rendered structure, and the JSON plus a copy of the .nbt land in
 * {@code config/slate/scenes/<ns>.<path>.json|.nbt}, where {@link SceneLoader} finds them first.
 *
 * <p>The origin is the structure's world position: taken from the structure block the player is looking at (its
 * position plus its relative offset), or passed explicitly. Reachable through the {@code /slate scene capture}
 * client command and the {@code slate:scene_capture} dev-mode action.</p>
 */
public final class SceneCapture {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Captures {@code template} (a structure name like {@code minecraft:title} or {@code title}) as scene {@code sceneId}. */
    public static Component capture(final String template, final String sceneId, @Nullable BlockPos origin) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Component.translatable("slate.stage.capture.no_world");
        final IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return Component.translatable("slate.stage.capture.singleplayer_only");
        final ResourceLocation templateId = ResourceLocation.tryParse(template.contains(":") ? template : "minecraft:" + template);
        final ResourceLocation scene = ResourceLocation.tryParse(sceneId.contains(":") ? sceneId : "slate:" + sceneId);
        if (templateId == null || scene == null) return Component.translatable("slate.stage.capture.bad_id");
        final Path nbtFile = server.getWorldPath(LevelResource.GENERATED_DIR).resolve(templateId.getNamespace()).resolve("structures")
            .resolve(templateId.getPath() + ".nbt");
        if (!Files.isRegularFile(nbtFile)) return Component.translatable("slate.stage.capture.no_template", nbtFile.toString());

        if (origin == null) origin = originFromLookedAtStructureBlock(mc);
        if (origin == null) return Component.translatable("slate.stage.capture.no_origin");

        final CompoundTag nbt;
        try {
            nbt = NbtIo.readCompressed(nbtFile, NbtAccounter.unlimitedHeap());
        } catch (final Exception e) {
            return Component.translatable("slate.stage.capture.failed", e.toString());
        }

        final Vec3 eye = mc.player.getEyePosition();
        final Vec3 look = mc.player.getViewVector(1f);
        final JsonObject json = build(nbt, eye.subtract(origin.getX(), origin.getY(), origin.getZ()), look, scene);
        try {
            final Path dir = SceneLoader.configDir();
            Files.createDirectories(dir);
            final Path jsonOut = dir.resolve(SceneLoader.configName(scene, "json"));
            final Path nbtOut = dir.resolve(SceneLoader.configName(scene, "nbt"));
            Files.writeString(jsonOut, GSON.toJson(json));
            Files.copy(nbtFile, nbtOut, StandardCopyOption.REPLACE_EXISTING);
            SceneLoader.clear();
            Slate.LOGGER.info("[Slate] scene {} captured from {} to {}", scene, templateId, jsonOut);
            return Component.translatable("slate.stage.capture.done", scene.toString(), json.getAsJsonObject("anchors").size(), jsonOut.toString());
        } catch (final Exception e) {
            return Component.translatable("slate.stage.capture.failed", e.toString());
        }
    }

    @Nullable
    private static BlockPos originFromLookedAtStructureBlock(final Minecraft mc) {
        final HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || mc.level == null) return null;
        final BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
        if (!(be instanceof StructureBlockEntity sbe)) return null;
        return bhr.getBlockPos().offset(sbe.getStructurePos());
    }

    /** The scene JSON for a template: camera from the eye/look, anchors from {@code #name} signs. */
    static JsonObject build(final CompoundTag nbt, final Vec3 eyeRelative, final Vec3 look, final ResourceLocation scene) {
        final JsonObject root = new JsonObject();
        root.addProperty("background", "#161615");
        final JsonObject structure = new JsonObject();
        structure.addProperty("file", scene.getPath());
        structure.addProperty("hideAnchorBlocks", true);
        final JsonArray hide = new JsonArray();
        final JsonObject anchors = new JsonObject();
        collectAnchors(nbt, anchors, hide);
        structure.add("hide", hide);
        root.add("structure", structure);

        final JsonObject camera = new JsonObject();
        camera.addProperty("fov", 50);
        final Vec3 target = eyeRelative.add(look.scale(6.0));
        final JsonObject rest = pose(eyeRelative, target);
        camera.add("pose", rest);
        final JsonArray intro = new JsonArray();
        final JsonObject line = new JsonObject();
        line.addProperty("type", "line");
        line.addProperty("duration", 1800);
        line.addProperty("ease", "outCubic");
        line.add("from", pose(eyeRelative.subtract(look.scale(3.0)).add(0, 0.6, 0), target));
        line.add("to", rest);
        intro.add(line);
        camera.add("intro", intro);
        final JsonObject idle = new JsonObject();
        idle.addProperty("type", "sway");
        idle.addProperty("period", 14000);
        idle.addProperty("yaw", 2);
        idle.addProperty("pitch", 0.8);
        idle.addProperty("bob", 0.03);
        camera.add("idle", idle);
        root.add("camera", camera);

        final JsonObject light = new JsonObject();
        light.add("sun", array(0.35, 1.0, 0.55));
        light.addProperty("ambient", 0.45);
        root.add("light", light);
        root.add("anchors", anchors);
        return root;
    }

    private static void collectAnchors(final CompoundTag nbt, final JsonObject anchors, final JsonArray hide) {
        ListTag palette;
        if (nbt.contains("palettes", Tag.TAG_LIST)) {
            final ListTag palettes = nbt.getList("palettes", Tag.TAG_LIST);
            palette = palettes.isEmpty() ? new ListTag() : palettes.getList(0);
        } else {
            palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        }
        final ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            final CompoundTag b = blocks.getCompound(i);
            if (!b.contains("nbt", Tag.TAG_COMPOUND)) continue;
            final CompoundTag be = b.getCompound("nbt");
            if (!be.contains("front_text", Tag.TAG_COMPOUND)) continue;
            final ListTag messages = be.getCompound("front_text").getList("messages", Tag.TAG_STRING);
            if (messages.isEmpty()) continue;
            final String first = plainText(messages.getString(0)).trim();
            if (!first.startsWith("#") || first.length() < 2) continue;
            final String name = first.substring(1).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            final ListTag pos = b.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) continue;
            final int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            float yaw = 0f;
            final int stateIndex = b.getInt("state");
            if (stateIndex >= 0 && stateIndex < palette.size()) {
                try {
                    final BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(stateIndex));
                    if (state.hasProperty(BlockStateProperties.ROTATION_16)) yaw = RotationSegment.convertToDegrees(state.getValue(BlockStateProperties.ROTATION_16));
                    else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
                } catch (final Exception ignored) {}
            }
            final JsonObject a = new JsonObject();
            a.add("pos", array(x + 0.5, y, z + 0.5));
            a.add("rot", array(yaw, 0, 0));
            anchors.add(name, a);
            hide.add(array(x, y, z));
        }
    }

    /** The plain text of a sign line stored as JSON ({@code "\"#play\""} or {@code {"text":"#play"}}). */
    static String plainText(final String json) {
        try {
            final JsonElement el = JsonParser.parseString(json);
            if (el.isJsonPrimitive()) return el.getAsString();
            if (el.isJsonObject() && el.getAsJsonObject().has("text")) return el.getAsJsonObject().get("text").getAsString();
        } catch (final Exception ignored) {}
        return json;
    }

    private static JsonObject pose(final Vec3 position, final Vec3 target) {
        final JsonObject o = new JsonObject();
        o.add("position", array(position.x, position.y, position.z));
        o.add("target", array(target.x, target.y, target.z));
        return o;
    }

    private static JsonArray array(final double x, final double y, final double z) {
        final JsonArray a = new JsonArray();
        a.add(round(x));
        a.add(round(y));
        a.add(round(z));
        return a;
    }

    private static double round(final double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private SceneCapture() {}
}
