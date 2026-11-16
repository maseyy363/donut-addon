package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Block ESP that only shows spawners: an outlined box on each loaded spawner. Render-only. */
public class BlockEsp extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 50)).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());
    private final Setting<Boolean> tracers = sg.add(new BoolSetting.Builder()
        .name("tracers").description("Line from your crosshair to each spawner.").defaultValue(false).build());
    private final Setting<SettingColor> tracerColor = sg.add(new ColorSetting.Builder()
        .name("tracer-color").defaultValue(new SettingColor(255, 80, 80, 255)).visible(tracers::get).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(10).min(5).sliderMax(100).build());

    private final List<BlockPos> spawners = new ArrayList<>();
    private int timer;

    public BlockEsp() {
        super(AddonTemplate.CATEGORY, "donut-block-esp", "Block ESP that only highlights spawners.");
    }

    @Override
    public void onActivate() { timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { spawners.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        List<BlockPos> found = new ArrayList<>();
        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity) found.add(be.getPos().toImmutable());
                }
            }
        }
        spawners.clear();
        spawners.addAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d eye = mc.player.getEyePos();
        double yaw = Math.toRadians(mc.player.getYaw()), pitch = Math.toRadians(mc.player.getPitch());
        double sx = eye.x - Math.sin(yaw) * Math.cos(pitch);
        double sy = eye.y - Math.sin(pitch);
        double sz = eye.z + Math.cos(yaw) * Math.cos(pitch);

        for (BlockPos p : spawners) {
            event.renderer.box(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            if (tracers.get()) {
                event.renderer.line(sx, sy, sz, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, tracerColor.get());
            }
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(spawners.size()); }
}
