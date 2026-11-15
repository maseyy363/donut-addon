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
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/** Red beam into the sky above every loaded spawner, plus a top-right toast the first time one is seen. */
public class SpawnerBeam extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> notify = sg.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new spawner is found.")
        .defaultValue(true).build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.")
        .defaultValue("Spawner found BOMBACLAT").build());
    private final Setting<Integer> beamTop = sg.add(new IntSetting.Builder()
        .name("beam-top-y").defaultValue(320).sliderRange(64, 400).build());
    private final Setting<Double> width = sg.add(new DoubleSetting.Builder()
        .name("beam-width").defaultValue(0.3).min(0.05).sliderMax(1).build());
    private final Setting<SettingColor> side = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 0, 0, 70)).build());
    private final Setting<SettingColor> line = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(20).min(5).sliderMax(100).build());

    private final List<BlockPos> spawners = new ArrayList<>();
    private final Set<BlockPos> notified = new HashSet<>();
    private int timer;

    public SpawnerBeam() {
        super(AddonTemplate.CATEGORY, "spawner-beam", "Red beam to the sky above spawners + notification.");
    }

    @Override
    public void onActivate() { notified.clear(); }

    @Override
    public void onDeactivate() { spawners.clear(); }

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
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity) found.add(be.getPos().toImmutable());
                }
            }
        }
        spawners.clear();
        spawners.addAll(found);

        if (notify.get()) {
            for (BlockPos p : found) {
                if (notified.add(p)) {
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                        Text.literal(message.get()),
                        Text.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()));
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double w = width.get(), o = (1 - w) / 2;
        for (BlockPos p : spawners) {
            event.renderer.box(p.getX() + o, p.getY(), p.getZ() + o,
                p.getX() + o + w, beamTop.get(), p.getZ() + o + w,
                side.get(), line.get(), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(spawners.size()); }
}
