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
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Stash notifier: when a chunk holds lots of chests / barrels / shulker boxes,
 * shows a top-right toast (once per chunk) and optionally outlines the chunk. Render-only.
 */
public class StashNotifier extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage").description("Chests + barrels + shulker boxes needed in one chunk.")
        .defaultValue(10).min(1).sliderMax(100).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(40).min(10).sliderMax(200).build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.").defaultValue("STASH FOUND BOMBACLAT").build());
    private final Setting<Boolean> chat = sg.add(new BoolSetting.Builder()
        .name("chat-message").description("Also print coordinates in chat.").defaultValue(true).build());

    private final Setting<Boolean> outline = sgRender.add(new BoolSetting.Builder()
        .name("outline-chunk").description("Draw a box around stash chunks.").defaultValue(true).build());
    private final Setting<Integer> yMin = sgRender.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).visible(outline::get).build());
    private final Setting<Integer> yMax = sgRender.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(100).sliderRange(-64, 320).visible(outline::get).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 200, 0, 40)).visible(outline::get).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 200, 0, 255)).visible(outline::get).build());

    private final Map<ChunkPos, Integer> stashes = new HashMap<>();
    private final Set<ChunkPos> notified = new HashSet<>();
    private int timer;

    public StashNotifier() {
        super(AddonTemplate.CATEGORY, "donut-stash-notifier", "Notifies when a chunk has a big pile of chests/barrels/shulkers.");
    }

    @Override
    public void onActivate() { notified.clear(); timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { stashes.clear(); }

    private static boolean isStashBlock(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, Integer> found = new HashMap<>();
        Map<ChunkPos, BlockPos> samples = new HashMap<>();
        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;

        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                int n = 0;
                BlockPos sample = null;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isStashBlock(be)) { n++; sample = be.getPos().toImmutable(); }
                }
                if (n >= minStorage.get()) {
                    found.put(chunk.getPos(), n);
                    samples.put(chunk.getPos(), sample);
                }
            }
        }
        stashes.clear();
        stashes.putAll(found);

        for (Map.Entry<ChunkPos, Integer> e : found.entrySet()) {
            if (!notified.add(e.getKey())) continue;
            BlockPos p = samples.get(e.getKey());
            SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(message.get()),
                Text.literal(e.getValue() + " storage blocks near " + p.getX() + ", " + p.getY() + ", " + p.getZ()));
            if (chat.get()) info("Stash (%d storage blocks) near %d %d %d", e.getValue(), p.getX(), p.getY(), p.getZ());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!outline.get()) return;
        for (ChunkPos p : stashes.keySet()) {
            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(stashes.size()); }
}
