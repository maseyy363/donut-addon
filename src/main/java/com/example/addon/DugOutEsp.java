package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Dug-out ESP: highlights straight 1-wide tunnels and 1x1 shafts that players dug.
 * Reads chunk data the server already sent; sends nothing. Boxes are visible through walls.
 */
public class DugOutEsp extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minRun = sg.add(new IntSetting.Builder()
        .name("min-length").description("Shortest straight run that counts as dug out. Longer = fewer false hits.")
        .defaultValue(8).min(TunnelFinder.MIN_RUN).max(16).sliderRange(TunnelFinder.MIN_RUN, 16).build());
    private final Setting<Integer> minChunkBlocks = sg.add(new IntSetting.Builder()
        .name("min-blocks-per-chunk").description("Total dug-out length needed in a chunk before it is shown.")
        .defaultValue(14).min(1).sliderMax(100).build());
    private final Setting<Boolean> showTunnels = sg.add(new BoolSetting.Builder()
        .name("tunnels").description("Show horizontal tunnels.").defaultValue(true).build());
    private final Setting<Boolean> showShafts = sg.add(new BoolSetting.Builder()
        .name("shafts").description("Show vertical 1x1 holes.").defaultValue(true).build());
    private final Setting<Integer> chunksPerTick = sg.add(new IntSetting.Builder()
        .name("chunks-per-tick").description("How many chunks to scan each tick (lower = less lag).")
        .defaultValue(1).min(1).sliderMax(6).build());
    private final Setting<Integer> rescanSeconds = sg.add(new IntSetting.Builder()
        .name("rescan-seconds").description("How often to rescan chunks you have already checked.")
        .defaultValue(60).min(10).sliderMax(300).build());
    private final Setting<Integer> maxDistance = sg.add(new IntSetting.Builder()
        .name("max-distance").description("Only draw things this many blocks away.")
        .defaultValue(96).min(16).sliderMax(256).build());

    private final Setting<Boolean> notify = sgRender.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new dug-out area is found.").defaultValue(true).build());
    private final Setting<String> message = sgRender.add(new StringSetting.Builder()
        .name("message").defaultValue("DUG OUT FOUND BOMBACLAT").build());
    private final Setting<Boolean> chat = sgRender.add(new BoolSetting.Builder()
        .name("chat-message").defaultValue(false).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(170, 0, 255, 45)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(190, 70, 255, 255)).build());

    private final Set<ChunkPos> notified = new HashSet<>();
    private int notifyTimer;

    public DugOutEsp() {
        super(AddonTemplate.CATEGORY, "donut-dug-out-esp", "Highlights player-dug tunnels and shafts.");
    }

    @Override
    public void onActivate() { notified.clear(); notifyTimer = 0; }

    private int total(TunnelFinder.Result r) {
        int n = 0;
        for (TunnelFinder.Segment s : r.segments) {
            if (s.length < minRun.get()) continue;
            if (s.vertical ? !showShafts.get() : !showTunnels.get()) continue;
            n += s.length;
        }
        return n;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        int vd = mc.options.getViewDistance().getValue();
        TunnelFinder.tick(mc.world, mc.player.getChunkPos().x, mc.player.getChunkPos().z, vd,
            chunksPerTick.get(), rescanSeconds.get() * 20);

        if (++notifyTimer < 20) return;
        notifyTimer = 0;
        if (!notify.get() && !chat.get()) return;

        for (Map.Entry<ChunkPos, TunnelFinder.Result> e : TunnelFinder.all().entrySet()) {
            ChunkPos key = e.getKey();
            if (notified.contains(key)) continue;
            if (total(e.getValue()) < minChunkBlocks.get()) continue;

            boolean duplicate = false; // same dug-out area spread over several chunks
            for (int dx = -1; dx <= 1 && !duplicate; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && notified.contains(new ChunkPos(key.x + dx, key.z + dz))) {
                        duplicate = true;
                        break;
                    }
                }
            }
            notified.add(key);
            if (duplicate) continue;

            TunnelFinder.Segment s = e.getValue().segments.get(0);
            if (notify.get()) {
                SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal(message.get()),
                    Text.literal(s.x1 + ", " + s.y1 + ", " + s.z1));
            }
            if (chat.get()) info("Dug out area near %d %d %d", s.x1, s.y1, s.z1);
            break; // one notification per pass so they never stack
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null) return;
        double maxD2 = (double) maxDistance.get() * maxDistance.get();
        double px = mc.player.getX(), pz = mc.player.getZ();

        for (TunnelFinder.Result r : TunnelFinder.all().values()) {
            if (total(r) < minChunkBlocks.get()) continue;
            for (TunnelFinder.Segment s : r.segments) {
                if (s.length < minRun.get()) continue;
                if (s.vertical ? !showShafts.get() : !showTunnels.get()) continue;
                double dx = (s.x1 + s.x2) / 2.0 - px, dz = (s.z1 + s.z2) / 2.0 - pz;
                if (dx * dx + dz * dz > maxD2) continue;
                event.renderer.box(s.x1, s.y1, s.z1, s.x2, s.y2, s.z2,
                    sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        int n = 0;
        for (TunnelFinder.Result r : TunnelFinder.all().values()) if (total(r) >= minChunkBlocks.get()) n++;
        return String.valueOf(n);
    }
}
