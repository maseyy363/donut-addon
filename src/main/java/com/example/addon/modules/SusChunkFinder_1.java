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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Scores every loaded chunk: storage block entities + sections containing player-made blocks.
 * Higher score = more suspicious. Colour goes orange -> red as the score rises.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> minScore = sg.add(new IntSetting.Builder()
        .name("min-score").description("Score needed before a chunk is highlighted.")
        .defaultValue(10).min(1).sliderMax(100).build());
    private final Setting<Integer> highScore = sg.add(new IntSetting.Builder()
        .name("high-score").description("Score at which the chunk turns red.")
        .defaultValue(30).min(2).sliderMax(200).build());
    private final Setting<Integer> sectionWeight = sg.add(new IntSetting.Builder()
        .name("player-section-weight").description("Points for each 16x16x16 section with player-made blocks.")
        .defaultValue(3).min(0).sliderMax(10).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(40).min(10).sliderMax(200).build());
    private final Setting<Integer> yMin = sg.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> yMax = sg.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(100).sliderRange(-64, 320).build());
    private final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> lowColor = sg.add(new ColorSetting.Builder()
        .name("low-color").defaultValue(new SettingColor(255, 170, 0, 255)).build());
    private final Setting<SettingColor> highColor = sg.add(new ColorSetting.Builder()
        .name("high-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Map<ChunkPos, Integer> sus = new HashMap<>();
    private int timer;

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunk-finder", "Scores chunks by storage and player-made blocks.");
    }

    @Override
    public void onDeactivate() { sus.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, Integer> found = new HashMap<>();
        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;

        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;

                int score = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (PlayerBlocks.isStorage(be)) score++;
                }
                for (ChunkSection s : chunk.getSectionArray()) {
                    if (s != null && !s.isEmpty() && s.hasAny(st -> PlayerBlocks.PLAYER_MADE.contains(st.getBlock()))) {
                        score += sectionWeight.get();
                    }
                }
                if (score >= minScore.get()) found.put(chunk.getPos(), score);
            }
        }
        sus.clear();
        sus.putAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Map.Entry<ChunkPos, Integer> e : sus.entrySet()) {
            ChunkPos p = e.getKey();
            double t = Math.min(1.0, Math.max(0.0,
                (e.getValue() - minScore.get()) / (double) Math.max(1, highScore.get() - minScore.get())));
            SettingColor a = lowColor.get(), b = highColor.get();
            int r = (int) (a.r + (b.r - a.r) * t), g = (int) (a.g + (b.g - a.g) * t), bl = (int) (a.b + (b.b - a.b) * t);
            SettingColor line = new SettingColor(r, g, bl, 255);
            SettingColor side = new SettingColor(r, g, bl, 40);
            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                side, line, shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(sus.size()); }
}
