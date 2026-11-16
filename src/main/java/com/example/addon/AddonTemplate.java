package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Donut");

    @Override
    public void onInitialize() {
        register("chunk-finder", SusChunkFinder::new);
        register("player-activity-chunks", PlayerActivityChunks::new);
        register("auto-relog", AutoRelog::new);
        register("block-overlay", BlockOverlay::new);
        register("base-signal", BaseSignal::new);
        register("spawner-beam", SpawnerBeam::new);
        register("blackstone-base", BlackstoneBase::new);
        register("block-esp", BlockEsp::new);
        register("stash-notifier", StashNotifier::new);
        register("anti-vanish", AntiVanish::new);
        register("dug-out-esp", DugOutEsp::new);
        register("cross-esp", CrossEsp::new);
    }

    /** One module failing must not stop the others; the reason goes to latest.log. */
    private void register(String name, Supplier<Module> module) {
        try {
            Modules.get().add(module.get());
        } catch (Throwable t) {
            LOG.error("[Donut addon] FAILED to register module " + name, t);
        }
    }

    @Override
    public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }

    @Override
    public String getPackage() { return "com.example.addon"; }

    @Override
    public GithubRepo getRepo() { return new GithubRepo("yourname", "donut-addon"); }
}
