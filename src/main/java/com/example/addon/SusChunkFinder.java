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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Finds likely bases. Looks at each chunk together with its neighbours (bases that straddle chunk
 * borders are found, each base shows once), counts player-dug tunnels as extra evidence, and shows a
 * confidence rating (LOW / MEDIUM / HIGH) in the notification.
 */
public class SusChunkFinder extends Module {
    public enum Style { Full, Corners, Floor }

    private static final int TUNNEL_MIN_RUN = 8;

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> undergroundOnly = sg.add(new BoolSetting.Builder()
        .name("underground-only").description("Only count storage below the surface. Turn off to also find bases above ground.")
        .defaultValue(true).build());
    private final Setting<Integer> depth = sg.add(new IntSetting.Builder()
        .name("min-depth-below-surface").description("Storage must be this many blocks under the surface to count.")
        .defaultValue(6).min(0).sliderMax(40).visible(undergroundOnly::get).build());
    private final Setting<Integer> minScore = sg.add(new IntSetting.Builder()
        .name("min-score").description("Score (chunk + neighbours) needed. Plain chest = 1, shulker = 4, ender chest = 6, beacon = 8.")
        .defaultValue(12).min(1).sliderMax(100).build());
    private final Setting<Integer> minTypes = sg.add(new IntSetting.Builder()
        .name("min-storage-types").description("Different kinds of storage needed (chest, barrel, hopper...). Blocks lone natural chests.")
        .defaultValue(2).min(1).sliderMax(6).build());
    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage-blocks").description("Minimum storage blocks (chunk + neighbours).")
        .defaultValue(3).min(0).sliderMax(50).build());
    private final Setting<Integer> sectionWeight = sg.add(new IntSetting.Builder()
        .name("player-section-weight").defaultValue(2).min(0).sliderMax(10).build());
    private final Setting<Boolean> useTunnels = sg.add(new BoolSetting.Builder()
        .name("use-tunnels").description("Count player-dug tunnels and shafts as base evidence.")
        .defaultValue(true).build());
    private final Setting<Double> tunnelWeight = sg.add(new DoubleSetting.Builder()
        .name("tunnel-weight").description("Score per tunnel block (capped at 12 per chunk).")
        .defaultValue(0.3).min(0).max(2).sliderMax(2).visible(useTunnels::get).build());
    private final Setting<Integer> highScore = sg.add(new IntSetting.Builder()
        .name("high-score").description("Score at which the chunk turns fully red.")
        .defaultValue(35).min(2).sliderMax(200).build());
    private final Setting<Integer> maxMarkers = sg.add(new IntSetting.Builder()
        .name("max-markers").description("Show only the nearest this many bases (keeps the screen clean).")
        .defaultValue(25).min(1).sliderMax(100).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(30).min(10).sliderMax(200).build());

    private final Setting<Boolean> notify = sgRender.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new base is found.").defaultValue(true).build());
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").description("Line from your crosshair to each base.").defaultValue(false).build());
    private final Setting<Style> style = sgRender.add(new EnumSetting.Builder<Style>()
        .name("style").description("Full box, corner brackets, or a flat floor marker.")
        .defaultValue(Style.Corners).build());
    private final Setting<Integer> yMin = sgRender.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> yMax = sgRender.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(320).sliderRange(-64, 320).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("Used by the Full style.").defaultValue(ShapeMode.Both).build());
    private final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").defaultValue(45).min(0).max(255).sliderMax(255).build());
    private final Setting<Boolean> pulse = sgRender.add(new BoolSetting.Builder()
        .name("pulse").description("Fill gently pulses so it is easier to spot.").defaultValue(true).build());
    private final Setting<SettingColor> lowColor = sgRender.add(new ColorSetting.Builder()
        .name("low-color").defaultValue(new SettingColor(255, 200, 0, 255)).build());
    private final Setting<SettingColor> highColor = sgRender.add(new ColorSetting.Builder()
        .name("high-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Map<ChunkPos, BaseDetector.Result> sus = new HashMap<>();
    private final Set<ChunkPos> notified = new HashSet<>();
    private int timer;

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "donut-chunk-finder", "Chunk finder: bases by storage weight, player blocks and tunnels.");
    }

    @Override
    public void onActivate() { notified.clear(); timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { sus.clear(); }

    private double distSq(ChunkPos p) {
        double dx = p.getCenterX() - mc.player.getX(), dz = p.getCenterZ() - mc.player.getZ();
        return dx * dx + dz * dz;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        int vd = mc.options.getViewDistance().getValue();
        if (useTunnels.get()) TunnelFinder.tick(mc.world, cx, cz, vd, 1, 60 * 20);

        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, BaseDetector.Result> found = BaseDetector.findBases(
            mc.world, cx, cz, vd, undergroundOnly.get() ? depth.get() : -4096, sectionWeight.get(),
            minScore.get(), minTypes.get(), minStorage.get(),
            useTunnels.get() ? tunnelWeight.get() : 0, TUNNEL_MIN_RUN);

        // Keep only the nearest bases.
        List<Map.Entry<ChunkPos, BaseDetector.Result>> list = new ArrayList<>(found.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<ChunkPos, BaseDetector.Result> e) -> distSq(e.getKey())));
        sus.clear();
        for (int i = 0; i < Math.min(list.size(), maxMarkers.get()); i++) {
            sus.put(list.get(i).getKey(), list.get(i).getValue());
        }

        if (notify.get()) {
            for (Map.Entry<ChunkPos, BaseDetector.Result> e : sus.entrySet()) {
                if (!notified.add(e.getKey())) continue;
                BaseDetector.Result r = e.getValue();
                BlockPos c = r.center;
                SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Base found: " + BaseDetector.confidence(r.region, minScore.get())
                        + " (score " + (int) r.region + ")"),
                    Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()));
                break; // one toast per scan so they don't stack
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getEyePos();
        double yaw = Math.toRadians(mc.player.getYaw()), pitch = Math.toRadians(mc.player.getPitch());
        double tx = eye.x - Math.sin(yaw) * Math.cos(pitch);
        double ty = eye.y - Math.sin(pitch);
        double tz = eye.z + Math.cos(yaw) * Math.cos(pitch);

        for (Map.Entry<ChunkPos, BaseDetector.Result> e : sus.entrySet()) {
            ChunkPos p = e.getKey();
            BaseDetector.Result r = e.getValue();
            double t = Math.min(1.0, Math.max(0.0,
                (r.region - minScore.get()) / (double) Math.max(1, highScore.get() - minScore.get())));
            SettingColor a = lowColor.get(), b = highColor.get();
            int cr = (int) (a.r + (b.r - a.r) * t), cg = (int) (a.g + (b.g - a.g) * t), cb = (int) (a.b + (b.b - a.b) * t);
            SettingColor line = new SettingColor(cr, cg, cb, 255);
            SettingColor side = Fx.pulse(new SettingColor(cr, cg, cb, fillAlpha.get()), pulse.get());

            double x1 = p.getStartX(), z1 = p.getStartZ(), x2 = x1 + 16, z2 = z1 + 16;
            switch (style.get()) {
                case Full -> event.renderer.box(x1, yMin.get(), z1, x2, yMax.get(), z2,
                    side, line, shapeMode.get(), 0);
                case Corners -> {
                    event.renderer.box(x1, yMin.get(), z1, x2, yMax.get(), z2, side, line, ShapeMode.Sides, 0);
                    corners(event, x1, z1, x2, z2, yMin.get(), yMax.get(), line);
                }
                case Floor -> {
                    double y = r.center.getY();
                    event.renderer.box(x1, y, z1, x2, y + 0.2, z2, side, line, ShapeMode.Both, 0);
                }
            }

            if (tracers.get()) {
                BlockPos c = r.center;
                event.renderer.line(tx, ty, tz, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, line);
            }
        }
    }

    /** Tall brackets on the 4 chunk corners with short arms at the top and bottom. */
    private void corners(Render3DEvent event, double x1, double z1, double x2, double z2,
                         double y1, double y2, SettingColor c) {
        double[][] pts = {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}};
        double arm = 4;
        for (double[] p : pts) {
            double sx = p[0] == x1 ? 1 : -1, sz = p[1] == z1 ? 1 : -1;
            event.renderer.line(p[0], y1, p[1], p[0], y2, p[1], c);
            for (double y : new double[]{y1, y2}) {
                event.renderer.line(p[0], y, p[1], p[0] + sx * arm, y, p[1], c);
                event.renderer.line(p[0], y, p[1], p[0], y, p[1] + sz * arm, c);
            }
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(sus.size()); }
}
