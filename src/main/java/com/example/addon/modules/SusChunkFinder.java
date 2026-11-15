package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Finds likely bases: UNDERGROUND storage weighted by rarity + player-made block sections.
 * Chunks pop with a tall box, a sky pillar, and a toast the first time they are found.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> depth = sg.add(new IntSetting.Builder()
        .name("min-depth-below-surface").description("Storage must be this many blocks under the surface to count.")
        .defaultValue(6).min(0).sliderMax(40).build());
    private final Setting<Integer> minScore = sg.add(new IntSetting.Builder()
        .name("min-score").description("Score needed to flag a chunk. Plain chest = 1, shulker = 4, ender chest = 6.")
        .defaultValue(12).min(1).sliderMax(100).build());
    private final Setting<Integer> minTypes = sg.add(new IntSetting.Builder()
        .name("min-storage-types").description("Different kinds of storage needed (chest, barrel, hopper...). Blocks lone natural chests.")
        .defaultValue(2).min(1).sliderMax(6).build());
    private final Setting<Integer> minStorage = sg.add(new IntSetting.Builder()
        .name("min-storage-blocks").description("Minimum underground storage blocks in the chunk.")
        .defaultValue(3).min(0).sliderMax(50).build());
    private final Setting<Integer> sectionWeight = sg.add(new IntSetting.Builder()
        .name("player-section-weight").defaultValue(2).min(0).sliderMax(10).build());
    private final Setting<Integer> highScore = sg.add(new IntSetting.Builder()
        .name("high-score").description("Score at which the chunk turns fully red.")
        .defaultValue(35).min(2).sliderMax(200).build());
    private final Setting<Integer> scanInterval = sg.add(new IntSetting.Builder()
        .name("scan-interval-ticks").defaultValue(30).min(10).sliderMax(200).build());

    private final Setting<Boolean> notify = sgRender.add(new BoolSetting.Builder()
        .name("notification").description("Top-right toast when a new sus chunk is found.").defaultValue(true).build());
    private final Setting<Boolean> pillar = sgRender.add(new BoolSetting.Builder()
        .name("sky-pillar").description("Beam into the sky above sus chunks.").defaultValue(true).build());
    private final Setting<Integer> yMin = sgRender.add(new IntSetting.Builder()
        .name("box-min-y").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> yMax = sgRender.add(new IntSetting.Builder()
        .name("box-max-y").defaultValue(320).sliderRange(-64, 320).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").defaultValue(55).min(0).max(255).sliderMax(255).build());
    private final Setting<SettingColor> lowColor = sgRender.add(new ColorSetting.Builder()
        .name("low-color").defaultValue(new SettingColor(255, 200, 0, 255)).build());
    private final Setting<SettingColor> highColor = sgRender.add(new ColorSetting.Builder()
        .name("high-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private final Map<ChunkPos, BaseDetector.Result> sus = new HashMap<>();
    private final Set<ChunkPos> notified = new HashSet<>();
    private int timer;

    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunk-finder", "Finds underground bases by storage weight + player blocks.");
    }

    @Override
    public void onActivate() { notified.clear(); }

    @Override
    public void onDeactivate() { sus.clear(); }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (++timer < scanInterval.get()) return;
        timer = 0;

        Map<ChunkPos, BaseDetector.Result> found = new HashMap<>();
        int vd = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;

        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                BaseDetector.Result r = BaseDetector.analyze(chunk, depth.get(), sectionWeight.get());
                if (r.score >= minScore.get() && r.types >= minTypes.get() && r.storage >= minStorage.get()) {
                    found.put(chunk.getPos(), r);
                }
            }
        }
        sus.clear();
        sus.putAll(found);

        if (notify.get()) {
            for (Map.Entry<ChunkPos, BaseDetector.Result> e : found.entrySet()) {
                if (notified.add(e.getKey())) {
                    BlockPos c = e.getValue().center;
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                        Text.literal("Sus chunk found (score " + (int) e.getValue().score + ")"),
                        Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()));
                    break; // one toast per scan so they don't stack
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Map.Entry<ChunkPos, BaseDetector.Result> e : sus.entrySet()) {
            ChunkPos p = e.getKey();
            double t = Math.min(1.0, Math.max(0.0,
                (e.getValue().score - minScore.get()) / (double) Math.max(1, highScore.get() - minScore.get())));
            SettingColor a = lowColor.get(), b = highColor.get();
            int r = (int) (a.r + (b.r - a.r) * t), g = (int) (a.g + (b.g - a.g) * t), bl = (int) (a.b + (b.b - a.b) * t);
            SettingColor line = new SettingColor(r, g, bl, 255);
            SettingColor side = new SettingColor(r, g, bl, fillAlpha.get());

            event.renderer.box(p.getStartX(), yMin.get(), p.getStartZ(),
                p.getStartX() + 16, yMax.get(), p.getStartZ() + 16,
                side, line, shapeMode.get(), 0);

            if (pillar.get()) {
                BlockPos c = e.getValue().center;
                event.renderer.box(c.getX() + 0.35, c.getY(), c.getZ() + 0.35,
                    c.getX() + 0.65, 320, c.getZ() + 0.65,
                    new SettingColor(r, g, bl, 90), line, ShapeMode.Both, 0);
            }
        }
    }

    @Override
    public String getInfoString() { return String.valueOf(sus.size()); }
}
