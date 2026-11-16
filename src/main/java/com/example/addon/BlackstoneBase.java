package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Finds chunks with lots of blackstone-family blocks (blackstone bases), draws a big black pillar
 * and a tracer to each one, and shows a top-right toast the first time each is found. Render-only.
 */
public class BlackstoneBase extends Module {
    private static final Set<Block> BLACKSTONE = Set.of(
        Blocks.BLACKSTONE, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_WALL,
        Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_STAIRS,
        Blocks.POLISHED_BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_BRICKS,
        Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
        Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
        Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE
    );

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minBlocks = sg.add(new IntSetting.Builder()
        .name("min-blocks").description("Blackstone blocks needed in one chunk to count as a base.")
        .defaultValue(60).min(1).sliderMax(1000).build());
    private final Setting<Boolean> onlyOverworld = sg.add(new BoolSetting.Builder()
        .name("only-overworld").description("Blackstone is natural in the Nether, so skip it there.")
        .defaultValue(true).build());
    private final Setting<Integer> chunksPerTick = sg.add(new IntSetting.Builder()
        .name("chunks-per-tick").description("How many chunks to scan each tick (lower = less lag).")
        .defaultValue(2).min(1).sliderMax(10).build());
    private final Setting<Integer> rescanSeconds = sg.add(new IntSetting.Builder()
        .name("rescan-seconds").description("How often to rescan chunks you have already checked.")
        .defaultValue(30).min(5).sliderMax(300).build());

    private final Setting<Boolean> notify = sgRender.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new base is found.").defaultValue(true).build());
    private final Setting<String> message = sgRender.add(new StringSetting.Builder()
        .name("message").defaultValue("BLACKSTONE BASEEEE BOMBACLAT").build());
    private final Setting<Boolean> chat = sgRender.add(new BoolSetting.Builder()
        .name("chat-message").description("Also print coordinates in chat.").defaultValue(false).build());
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(true).build());
    private final Setting<Double> pillarWidth = sgRender.add(new DoubleSetting.Builder()
        .name("pillar-width").defaultValue(1.5).min(0.2).sliderMax(5).build());
    private final Setting<Integer> pillarTop = sgRender.add(new IntSetting.Builder()
        .name("pillar-top-y").defaultValue(320).sliderRange(64, 400).build());
    private final Setting<Boolean> pulse = sgRender.add(new BoolSetting.Builder()
        .name("pulse").description("Pillar gently pulses so it is easier to spot.").defaultValue(true).build());
    private final Setting<SettingColor> pillarColor = sgRender.add(new ColorSetting.Builder()
        .name("pillar-color").defaultValue(new SettingColor(0, 0, 0, 200)).build());
    private final Setting<SettingColor> outlineColor = sgRender.add(new ColorSetting.Builder()
        .name("outline-color").defaultValue(new SettingColor(90, 90, 90, 255)).build());
    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private static final class Hit {
        int count;
        BlockPos center;
    }

    private final Map<ChunkPos, Hit> hits = new HashMap<>();
    private final Set<ChunkPos> scanned = new HashSet<>();
    private final Set<ChunkPos> notified = new HashSet<>();
    private int rescanTimer;

    public BlackstoneBase() {
        super(AddonTemplate.CATEGORY, "donut-blackstone-base", "Big black pillar + tracer over blackstone bases.");
    }

    @Override
    public void onActivate() { scanned.clear(); rescanTimer = 0; }

    @Override
    public void onDeactivate() { hits.clear(); scanned.clear(); notified.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (onlyOverworld.get() && mc.world.getRegistryKey() != World.OVERWORLD) { hits.clear(); return; }

        if (++rescanTimer >= rescanSeconds.get() * 20) { rescanTimer = 0; scanned.clear(); }

        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        hits.keySet().removeIf(p -> Math.abs(p.x - cx) > vd || Math.abs(p.z - cz) > vd);

        int budget = chunksPerTick.get();
        outer:
        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                ChunkPos cp = new ChunkPos(x, z);
                if (scanned.contains(cp)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                if (budget-- <= 0) break outer;

                scanned.add(cp);
                Hit h = analyze(chunk);
                if (h != null && h.count >= minBlocks.get()) {
                    hits.put(cp, h);
                    if (notified.add(cp)) {
                        BlockPos c = h.center;
                        if (notify.get()) {
                            SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                                Text.literal(message.get()),
                                Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()));
                        }
                        if (chat.get()) info("Blackstone base at %d %d %d", c.getX(), c.getY(), c.getZ());
                    }
                } else {
                    hits.remove(cp);
                }
            }
        }
    }

    private Hit analyze(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int count = 0;
        long sx = 0, sy = 0, sz = 0;
        int startX = chunk.getPos().getStartX(), startZ = chunk.getPos().getStartZ();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            if (!s.hasAny(st -> BLACKSTONE.contains(st.getBlock()))) continue; // cheap palette check first
            int baseY = (chunk.getBottomSectionCoord() + i) * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (BLACKSTONE.contains(st.getBlock())) {
                            count++;
                            sx += startX + x; sy += baseY + y; sz += startZ + z;
                        }
                    }
                }
            }
        }
        if (count == 0) return null;
        Hit h = new Hit();
        h.count = count;
        h.center = new BlockPos((int) (sx / count), (int) (sy / count), (int) (sz / count));
        return h;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null) return;

        // Tracer start: a point just in front of the crosshair.
        Vec3d eye = mc.player.getEyePos();
        double yaw = Math.toRadians(mc.player.getYaw()), pitch = Math.toRadians(mc.player.getPitch());
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        double sx = eye.x + lx, sy = eye.y + ly, sz = eye.z + lz;

        double w = pillarWidth.get(), o = w / 2;
        for (Hit h : hits.values()) {
            BlockPos c = h.center;
            event.renderer.box(c.getX() + 0.5 - o, c.getY(), c.getZ() + 0.5 - o,
                c.getX() + 0.5 + o, pillarTop.get(), c.getZ() + 0.5 + o,
                Fx.pulse(pillarColor.get(), pulse.get()), outlineColor.get(), ShapeMode.Both, 0);
            if (tracers.get()) {
                event.renderer.line(sx, sy, sz, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, tracerColor.get());
            }
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(hits.size()); }
}
