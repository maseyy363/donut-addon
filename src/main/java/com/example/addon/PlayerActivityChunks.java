package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Highlights chunks with underground player-made blocks (glass, redstone parts, crafting tables...). */
public class PlayerActivityChunks extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> depth = sg.add(new IntSetting.Builder()
        .name("min-depth-below-surface").defaultValue(6).min(0).sliderMax(40).build());
    private final Setting<Integer> minSections = sg.add(new IntSetting.Builder()
        .name("min-sections").description("Underground sections with player-made blocks needed.")
        .defaultValue(2).min(1).sliderMax(20).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(40).min(10).sliderMax(200).build());
    private final Setting<Integer> yMin = sg.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> yMax = sg.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(100).sliderRange(-64, 320).build());
    private final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 255, 100, 40)).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 255, 100, 255)).build());

    private final Set<ChunkPos> active = new HashSet<>();
    private int timer;

    public PlayerActivityChunks() {
        super(AddonTemplate.CATEGORY, "player-activity-chunks", "Highlights chunks with underground player activity.");
    }

    @Override
    public void onActivate() { timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { active.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Set<ChunkPos> found = new HashSet<>();
        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;

        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                if (BaseDetector.analyze(chunk, depth.get(), 0).playerSections >= minSections.get()) {
                    found.add(chunk.getPos());
                }
            }
        }
        active.clear();
        active.addAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        for (ChunkPos p : active) {
            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(active.size()); }
}
