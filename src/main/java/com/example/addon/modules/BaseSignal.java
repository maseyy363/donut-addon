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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Cyan beam into the sky above chunks that look like active bases:
 * enough storage blocks AND/OR enough player-made block sections.
 */
public class BaseSignal extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage").description("Storage/utility blocks needed in a chunk.")
        .defaultValue(5).min(0).sliderMax(64).build());
    private final Setting<Integer> minPlayerSections = sg.add(new IntSetting.Builder()
        .name("min-player-sections").description("Sections with player-made blocks needed in a chunk.")
        .defaultValue(2).min(0).sliderMax(20).build());
    private final Setting<Boolean> requireBoth = sg.add(new BoolSetting.Builder()
        .name("require-both").description("Need storage AND player blocks (fewer false positives).")
        .defaultValue(false).build());
    private final Setting<Integer> beamTop = sg.add(new IntSetting.Builder()
        .name("beam-top-y").defaultValue(320).sliderRange(64, 400).build());
    private final Setting<Double> width = sg.add(new DoubleSetting.Builder()
        .name("beam-width").defaultValue(0.4).min(0.05).sliderMax(1).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(40).min(10).sliderMax(200).build());
    private final Setting<SettingColor> side = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 200, 255, 60)).build());
    private final Setting<SettingColor> line = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 200, 255, 255)).build());

    private final List<BlockPos> bases = new ArrayList<>();
    private int timer;

    public BaseSignal() {
        super(AddonTemplate.CATEGORY, "base-signal", "Cyan beam in the sky over likely bases.");
    }

    @Override
    public void onDeactivate() { bases.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
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

                int n = 0;
                long sx = 0, sy = 0, sz = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (PlayerBlocks.isStorage(be)) {
                        n++;
                        BlockPos p = be.getPos();
                        sx += p.getX(); sy += p.getY(); sz += p.getZ();
                    }
                }
                int sections = 0;
                for (ChunkSection s : chunk.getSectionArray()) {
                    if (s != null && !s.isEmpty() && s.hasAny(st -> PlayerBlocks.PLAYER_MADE.contains(st.getBlock()))) sections++;
                }

                boolean storageOk = n >= minStorage.get() && n > 0;
                boolean playerOk = sections >= minPlayerSections.get() && sections > 0;
                boolean hit = requireBoth.get() ? (storageOk && playerOk) : (storageOk || playerOk);
                if (!hit) continue;

                BlockPos pos = n > 0
                    ? new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n))
                    : new BlockPos(chunk.getPos().getCenterX(), 64, chunk.getPos().getCenterZ());
                found.add(pos);
            }
        }
        bases.clear();
        bases.addAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double w = width.get(), o = (1 - w) / 2;
        for (BlockPos p : bases) {
            event.renderer.box(p.getX() + o, p.getY(), p.getZ() + o,
                p.getX() + o + w, beamTop.get(), p.getZ() + o + w,
                side.get(), line.get(), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(bases.size()); }
}
