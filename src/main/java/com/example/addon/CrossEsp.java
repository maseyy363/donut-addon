package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks spawners and players with a big X (a cross that always faces you, visible through walls).
 * Only marks what the server has actually sent you: spawners in loaded chunks and players that are
 * loaded around you. Render-only; it does not reveal anything the server is not sending.
 */
public class CrossEsp extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> spawners = sg.add(new BoolSetting.Builder()
        .name("spawners").description("Mark spawners with a cross.").defaultValue(true).build());
    private final Setting<Boolean> players = sg.add(new BoolSetting.Builder()
        .name("players").description("Mark other players with a cross.").defaultValue(true).build());
    private final Setting<Double> spawnerSize = sg.add(new DoubleSetting.Builder()
        .name("spawner-cross-size").defaultValue(0.7).min(0.2).sliderMax(3).visible(spawners::get).build());
    private final Setting<Double> playerSize = sg.add(new DoubleSetting.Builder()
        .name("player-cross-size").defaultValue(0.6).min(0.2).sliderMax(3).visible(players::get).build());
    private final Setting<SettingColor> spawnerColor = sg.add(new ColorSetting.Builder()
        .name("spawner-color").defaultValue(new SettingColor(255, 0, 0, 255)).visible(spawners::get).build());
    private final Setting<SettingColor> playerColor = sg.add(new ColorSetting.Builder()
        .name("player-color").defaultValue(new SettingColor(255, 255, 255, 255)).visible(players::get).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(10).min(5).sliderMax(100).build());

    private final List<BlockPos> spawnerList = new ArrayList<>();
    private int timer;

    public CrossEsp() {
        super(AddonTemplate.CATEGORY, "donut-cross-esp", "Marks spawners and players with a cross.");
    }

    @Override
    public void onActivate() { timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { spawnerList.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (!spawners.get()) { spawnerList.clear(); return; }
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
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (spawners.get()) {
            for (BlockPos p : spawnerList) {
                cross(event, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, spawnerSize.get(), spawnerColor.get());
            }
        }
        if (players.get()) {
            for (PlayerEntity pl : mc.world.getPlayers()) {
                if (pl == mc.player) continue;
                cross(event, pl.getX(), pl.getY() + pl.getHeight() / 2.0, pl.getZ(), playerSize.get(), playerColor.get());
            }
        }
    }

    /** Draws an X centred on (x, y, z) in a plane that faces the player. */
    private void cross(Render3DEvent event, double x, double y, double z, double size, SettingColor c) {
        double dx = mc.player.getX() - x, dz = mc.player.getZ() - z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double rx, rz;
        if (len < 1.0E-4) { rx = 1; rz = 0; } else { rx = -dz / len; rz = dx / len; }
        rx *= size; rz *= size;
        event.renderer.line(x - rx, y - size, z - rz, x + rx, y + size, z + rz, c);
        event.renderer.line(x - rx, y + size, z - rz, x + rx, y - size, z + rz, c);
    }
}
