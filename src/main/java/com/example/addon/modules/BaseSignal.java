package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

/**
 * Draws a tracer + a vertical marker at the centre of chunks that look like bases.
 * Render-only: no packets are sent.
 */
public class BaseSignal extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage").description("Storage/utility blocks needed to call a chunk a base.")
        .defaultValue(6).min(1).sliderMax(64).build());
    private final Setting<Boolean> tracers = sg.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(true).build());
    private final Setting<Boolean> pillar = sg.add(new BoolSetting.Builder()
        .name("pillar").description("Vertical marker above each base.").defaultValue(true).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(40).min(10).sliderMax(200).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(0, 200, 255, 255)).build());

    private final List<BlockPos> bases = new ArrayList<>();
    private int timer;

    public BaseSignal() {
        super(AddonTemplate.CATEGORY, "base-signal", "Shows where likely bases are.");
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
                Chunk chunk = mc.world.getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int n = 0;
                long sx = 0, sy = 0, sz = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity || be instanceof FurnaceBlockEntity
                        || be instanceof HopperBlockEntity || be instanceof BedBlockEntity
                        || be instanceof EnderChestBlockEntity) {
                        n++;
                        BlockPos p = be.getPos();
                        sx += p.getX(); sy += p.getY(); sz += p.getZ();
                    }
                }
                if (n >= minStorage.get()) found.add(new BlockPos((int) (sx / n), (int) (sy / n), (int) (sz / n)));
            }
        }
        bases.clear();
        bases.addAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Color c = color.get();
        for (BlockPos p : bases) {
            if (tracers.get()) {
                event.renderer.line(
                    event.offsetX, event.offsetY, event.offsetZ, // crosshair origin handled by renderer offset
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c);
            }
            if (pillar.get()) {
                event.renderer.line(p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                    p.getX() + 0.5, p.getY() + 60, p.getZ() + 0.5, c);
            }
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(bases.size()); }
}
