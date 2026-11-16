package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Overlay on the block you look at:
 *  - a bracket at each of the 8 corners
 *  - a gapped ring + centre dot on the face you are aiming at
 *  - optional faint fill
 * Render-only.
 */
public class BlockOverlay extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgCircle = settings.createGroup("Circle");

    private final Setting<Double> length = sg.add(new DoubleSetting.Builder()
        .name("corner-length").description("How long each corner bracket arm is (1 = full edge).")
        .defaultValue(0.3).min(0.05).max(0.5).sliderMax(0.5).build());
    private final Setting<Boolean> fill = sg.add(new BoolSetting.Builder()
        .name("fill").description("Faint fill over the block.").defaultValue(true).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 220, 255, 255)).build());
    private final Setting<SettingColor> fillColor = sg.add(new ColorSetting.Builder()
        .name("fill-color").defaultValue(new SettingColor(0, 220, 255, 30)).visible(fill::get).build());

    private final Setting<Boolean> circle = sgCircle.add(new BoolSetting.Builder()
        .name("circle").description("Ring + dot in the middle of the face you aim at.").defaultValue(true).build());
    private final Setting<Double> radius = sgCircle.add(new DoubleSetting.Builder()
        .name("radius").defaultValue(0.28).min(0.05).max(0.6).sliderMax(0.6).visible(circle::get).build());
    private final Setting<Integer> arcs = sgCircle.add(new IntSetting.Builder()
        .name("arcs").description("Number of ring pieces (gaps between them).")
        .defaultValue(4).min(1).max(8).sliderMax(8).visible(circle::get).build());
    private final Setting<Integer> gapDegrees = sgCircle.add(new IntSetting.Builder()
        .name("gap-degrees").defaultValue(25).min(0).max(80).sliderMax(80).visible(circle::get).build());
    private final Setting<Boolean> dot = sgCircle.add(new BoolSetting.Builder()
        .name("center-dot").defaultValue(true).visible(circle::get).build());
    private final Setting<SettingColor> circleColor = sgCircle.add(new ColorSetting.Builder()
        .name("circle-color").defaultValue(new SettingColor(255, 255, 255, 255)).visible(circle::get).build());

    public BlockOverlay() {
        super(AddonTemplate.CATEGORY, "block-overlay", "Corner brackets + ring overlay on the block you look at.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        Box b = shape.getBoundingBox().offset(pos);
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        double lx = (x2 - x1) * length.get(), ly = (y2 - y1) * length.get(), lz = (z2 - z1) * length.get();
        SettingColor c = lineColor.get();

        if (fill.get()) {
            event.renderer.box(x1, y1, z1, x2, y2, z2, fillColor.get(), c, ShapeMode.Sides, 0);
        }

        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    double cx = xi == 0 ? x1 : x2, cy = yi == 0 ? y1 : y2, cz = zi == 0 ? z1 : z2;
                    double dx = xi == 0 ? lx : -lx, dy = yi == 0 ? ly : -ly, dz = zi == 0 ? lz : -lz;
                    event.renderer.line(cx, cy, cz, cx + dx, cy, cz, c);
                    event.renderer.line(cx, cy, cz, cx, cy + dy, cz, c);
                    event.renderer.line(cx, cy, cz, cx, cy, cz + dz, c);
                }
            }
        }

        if (circle.get()) drawCircle(event, hit.getSide(), b);
    }

    private void drawCircle(Render3DEvent event, Direction side, Box b) {
        double eps = 0.004;
        double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;

        // Centre of the aimed face, nudged out so it does not z-fight with the block.
        double ux, uy, uz, vx, vy, vz; // two axes lying in the face plane
        switch (side.getAxis()) {
            case X -> {
                cx = side.getOffsetX() > 0 ? b.maxX + eps : b.minX - eps;
                ux = 0; uy = 1; uz = 0; vx = 0; vy = 0; vz = 1;
            }
            case Y -> {
                cy = side.getOffsetY() > 0 ? b.maxY + eps : b.minY - eps;
                ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1;
            }
            default -> {
                cz = side.getOffsetZ() > 0 ? b.maxZ + eps : b.minZ - eps;
                ux = 1; uy = 0; uz = 0; vx = 0; vy = 1; vz = 0;
            }
        }

        SettingColor col = circleColor.get();
        double r = radius.get();
        int n = arcs.get();
        double slice = 360.0 / n;
        double gap = Math.min(gapDegrees.get(), slice - 1);
        double step = 8; // degrees per line piece

        for (int i = 0; i < n; i++) {
            double start = i * slice + gap / 2, end = (i + 1) * slice - gap / 2;
            double prevA = Math.toRadians(start);
            double px = cx + (Math.cos(prevA) * ux + Math.sin(prevA) * vx) * r;
            double py = cy + (Math.cos(prevA) * uy + Math.sin(prevA) * vy) * r;
            double pz = cz + (Math.cos(prevA) * uz + Math.sin(prevA) * vz) * r;
            for (double a = start + step; a < end + step; a += step) {
                double aa = Math.toRadians(Math.min(a, end));
                double nx = cx + (Math.cos(aa) * ux + Math.sin(aa) * vx) * r;
                double ny = cy + (Math.cos(aa) * uy + Math.sin(aa) * vy) * r;
                double nz = cz + (Math.cos(aa) * uz + Math.sin(aa) * vz) * r;
                event.renderer.line(px, py, pz, nx, ny, nz, col);
                px = nx; py = ny; pz = nz;
            }
        }

        if (dot.get()) {
            double d = 0.025;
            event.renderer.line(cx - ux * d, cy - uy * d, cz - uz * d, cx + ux * d, cy + uy * d, cz + uz * d, col);
            event.renderer.line(cx - vx * d, cy - vy * d, cz - vz * d, cx + vx * d, cy + vy * d, cz + vz * d, col);
        }
    }
}
