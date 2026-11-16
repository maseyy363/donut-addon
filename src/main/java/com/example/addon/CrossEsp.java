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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class CrossEsp extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgPlayers = settings.createGroup("Players");

    private final Setting<Boolean> notify = sg.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new spawner is found.").defaultValue(true).build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.").defaultValue("ECHO DEBUG SPAWNER").build());
    private final Setting<Boolean> chat = sg.add(new BoolSetting.Builder()
        .name("chat-message").description("Also print the coordinates in chat.").defaultValue(false).build());
    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("cross-size").description("How big the cross is.")
        .defaultValue(1.5).min(0.3).max(6).sliderMax(5).build());
    private final Setting<Double> height = sg.add(new DoubleSetting.Builder()
        .name("height-above-spawner").description("How high above the spawner the cross floats.")
        .defaultValue(15).min(0).max(200).sliderMax(60).build());
    private final Setting<Boolean> stem = sg.add(new BoolSetting.Builder()
        .name("line-to-spawner").description("Thin line from the spawner up to the cross.").defaultValue(true).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(255, 220, 0, 255)).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(10).min(5).sliderMax(100).build());

    private final Setting<Boolean> players = sgPlayers.add(new BoolSetting.Builder()
        .name("mark-players").description("Also put a small cross over other players.").defaultValue(false).build());
    private final Setting<Double> playerSize = sgPlayers.add(new DoubleSetting.Builder()
        .name("player-cross-size").defaultValue(0.5).min(0.2).max(3).sliderMax(2).visible(players::get).build());
    private final Setting<SettingColor> playerColor = sgPlayers.add(new ColorSetting.Builder()
        .name("player-color").defaultValue(new SettingColor(255, 220, 0, 255)).visible(players::get).build());

    private final List<BlockPos> spawnerList = new ArrayList<>();
    private final Set<BlockPos> notified = new HashSet<>();
    private int timer;

    public CrossEsp() {
        super(AddonTemplate.CATEGORY, "donut-cross-esp", "Floating yellow cross over spawners + notification.");
    }

    @Override
    public void onActivate() { notified.clear(); timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { spawnerList.clear(); }

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
        spawnerList.clear();
        spawnerList.addAll(found);

        for (BlockPos p : found) {
            if (!notified.add(p)) continue;
            if (notify.get()) {
                SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal(message.get()),
                    Text.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()));
            }
            if (chat.get()) info("Spawner at %d %d %d", p.getX(), p.getY(), p.getZ());
            break; // one toast per scan so they never stack
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (BlockPos p : spawnerList) {
            double cx = p.getX() + 0.5, cz = p.getZ() + 0.5;
            double cy = p.getY() + 1 + height.get() + size.get();
            cross(event, cx, cy, cz, size.get(), color.get());
            if (stem.get()) {
                event.renderer.line(cx, p.getY() + 1, cz, cx, cy - size.get(), cz, color.get());
            }
        }

        if (players.get()) {
            for (PlayerEntity pl : mc.world.getPlayers()) {
                if (pl == mc.player) continue;
                double s = playerSize.get();
                cross(event, pl.getX(), pl.getY() + pl.getHeight() + 0.6 + s, pl.getZ(), s, playerColor.get());
            }
        }
    }

    private void cross(Render3DEvent event, double x, double y, double z, double s, SettingColor c) {
        double hw = Math.max(0.06, s * 0.07);
        SettingColor fill = new SettingColor(c.r, c.g, c.b, 140);
        double barY = y + s;
        event.renderer.box(x - hw, y - s, z - hw, x + hw, y + 2 * s, z + hw, fill, c, ShapeMode.Both, 0);
        event.renderer.box(x - s, barY - hw, z - hw, x + s, barY + hw, z + hw, fill, c, ShapeMode.Both, 0);
        event.renderer.box(x - hw, barY - hw, z - s, x + hw, barY + hw, z + s, fill, c, ShapeMode.Both, 0);
    }

    @Override
    public String getInfoString() { return String.valueOf(spawnerList.size()); }
}
