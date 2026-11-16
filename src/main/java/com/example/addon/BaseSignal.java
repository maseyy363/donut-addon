package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Cyan beam into the sky above the strongest underground base candidates.
 * Same cluster-aware detector as Chunk Finder but stricter by default (fewer, surer hits).
 */
public class BaseSignal extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> depth = sg.add(new IntSetting.Builder()
        .name("min-depth-below-surface").defaultValue(6).min(0).sliderMax(40).build());
    private final Setting<Integer> minScore = sg.add(new IntSetting.Builder()
        .name("min-score").defaultValue(18).min(1).sliderMax(100).build());
    private final Setting<Integer> minTypes = sg.add(new IntSetting.Builder()
        .name("min-storage-types").defaultValue(2).min(1).sliderMax(6).build());
    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage-blocks").defaultValue(4).min(0).sliderMax(50).build());
    private final Setting<Integer> sectionWeight = sg.add(new IntSetting.Builder()
        .name("player-section-weight").defaultValue(2).min(0).sliderMax(10).build());
    private final Setting<Integer> beamTop = sg.add(new IntSetting.Builder()
        .name("beam-top-y").defaultValue(320).sliderRange(64, 400).build());
    private final Setting<Double> width = sg.add(new DoubleSetting.Builder()
        .name("beam-width").defaultValue(0.4).min(0.05).sliderMax(1).build());
    private final Setting<Boolean> pulse = sg.add(new BoolSetting.Builder()
        .name("pulse").description("Beam gently pulses so it is easier to spot.").defaultValue(true).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(30).min(10).sliderMax(200).build());
    private final Setting<SettingColor> side = sg.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 200, 255, 70)).build());
    private final Setting<SettingColor> line = sg.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 200, 255, 255)).build());

    private final List<BlockPos> bases = new ArrayList<>();
    private int timer;

    public BaseSignal() {
        super(AddonTemplate.CATEGORY, "base-signal", "Cyan beam in the sky over likely underground bases.");
    }

    @Override
    public void onActivate() { timer = scanInterval.get(); }

    @Override
    public void onDeactivate() { bases.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Safe.run(getClass().getSimpleName(), () -> onTickImpl(event));
    }

    private void onTickImpl(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, BaseDetector.Result> found = BaseDetector.findBases(
            mc.world, mc.player.getChunkPos().x, mc.player.getChunkPos().z,
            mc.options.getViewDistance().getValue(), depth.get(), sectionWeight.get(),
            minScore.get(), minTypes.get(), minStorage.get());

        bases.clear();
        for (BaseDetector.Result r : found.values()) bases.add(r.center);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Safe.run(getClass().getSimpleName(), () -> onRenderImpl(event));
    }

    private void onRenderImpl(Render3DEvent event) {
        double w = width.get(), o = (1 - w) / 2;
        SettingColor sideC = Fx.pulse(side.get(), pulse.get());
        for (BlockPos p : bases) {
            event.renderer.box(p.getX() + o, p.getY(), p.getZ() + o,
                p.getX() + o + w, beamTop.get(), p.getZ() + o + w,
                sideC, line.get(), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(bases.size()); }
}
