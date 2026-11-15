package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

/**
 * Highlights chunks with an unusual amount of player-made block entities
 * (chests, barrels, shulkers, furnaces, hoppers, beds...). Purely client-side rendering.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sg.add(new IntSetting.Builder()
        .name("min-block-entities").description("Block entities in a chunk before it is flagged.")
        .defaultValue(8).min(1).sliderMax(64).build());

    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").description("How often to rescan loaded chunks.")
        .defaultValue(40).min(10).sliderMax(200).build());

    private final Setting<Integer> yMin = sg.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> yMax = sg.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(64).sliderRange(-64, 320).build());

    private final Setting<ShapeMode> shapeMode = sg.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 170, 0, 40)).build());
    private final Setting<SettingColor> lineColor = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 170, 0, 255)).build());

    private final Map<ChunkPos, Integer> sus = new HashMap<>();
    private int timer;

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunk-finder", "Highlights chunks with lots of player-made blocks.");
    }

    @Override
    public void onDeactivate() { sus.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, Integer> found = new HashMap<>();
        int viewDist = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;

        for (int x = cx - viewDist; x <= cx + viewDist; x++) {
            for (int z = cz - viewDist; z <= cz + viewDist; z++) {
                Chunk chunk = mc.world.getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int count = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity || be instanceof FurnaceBlockEntity
                        || be instanceof BlastFurnaceBlockEntity || be instanceof SmokerBlockEntity
                        || be instanceof HopperBlockEntity || be instanceof BedBlockEntity
                        || be instanceof EnderChestBlockEntity || be instanceof DispenserBlockEntity
                        || be instanceof CrafterBlockEntity) count++;
                }
                if (count >= threshold.get()) found.put(chunk.getPos(), count);
            }
        }
        sus.clear();
        sus.putAll(found);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (ChunkPos p : sus.keySet()) {
            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(sus.size()); }
}
