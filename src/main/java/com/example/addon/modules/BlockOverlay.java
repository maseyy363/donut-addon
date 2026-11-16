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
import net.minecraft.util.shape.VoxelShape;

/**
 * Overlay on the block you are looking at: a bracket at each of the 8 corners
 * (three short lines per corner), with an optional faint fill. Render-only.
 */
public class BlockOverlay extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> length = sg.add(new DoubleSetting.Builder()
        .name("corner-length").description("How long each corner bracket arm is (1 = full edge).")
        .defaultValue(0.3).min(0.05).max(0.5).sliderMax(0.5).build());
    private final Setting<Boolean> fill = sg.add(new BoolSetting.Builder()
        .name("fill").description("Faint fill over the block.").defaultValue(true).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 220, 255, 255)).build());
    private final Setting<SettingColor> fillColor = sg.add(new ColorSetting.Builder()
        .name("fill-color").defaultValue(new SettingColor(0, 220, 255, 30)).visible(fill::get).build());

    public BlockOverlay() {
        super(AddonTemplate.CATEGORY, "block-overlay", "Corner-bracket overlay on the block you look at.");
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
    }
}
