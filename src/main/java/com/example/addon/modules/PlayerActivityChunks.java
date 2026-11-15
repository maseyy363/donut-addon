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
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Highlights chunks where there are blocks players place (glass, redstone parts, crafting tables...).
 * Rough signal of "someone has been here". Villages/mineshafts can false-positive, so tune the minimum.
 */
public class PlayerActivityChunks extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> minSections = sg.add(new IntSetting.Builder()
        .name("min-sections").description("Sections with player-made blocks needed to flag the chunk.")
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
        .name("side-color").defaultValue(new SettingColor(0, 255, 100, 35)).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 255, 100, 255)).build());

    private final Set<ChunkPos> active = new HashSet<>();
    private int timer;

    public PlayerActivityChunks() {
        super(AddonTemplate.CATEGORY, "player-activity-chunks", "Highlights chunks with signs of player activity.");
    }

    @Override
    public void onDeactivate() { active.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
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
                int sections = 0;
                for (ChunkSection s : chunk.getSectionArray()) {
                    if (s != null && !s.isEmpty() && s.hasAny(st -> PlayerBlocks.PLAYER_MADE.contains(st.getBlock()))) sections++;
                }
                if (sections >= minSections.get()) found.add(chunk.getPos());
            }
        }
        active.clear();
        active.addAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (ChunkPos p : active) {
            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(active.size()); }
}
