package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Donut");

    @Override
    public void onInitialize() {
        Modules.get().add(new SusChunkFinder());
        Modules.get().add(new PlayerActivityChunks());
        Modules.get().add(new BaseSignal());
        Modules.get().add(new SpawnerBeam());
    }

    @Override
    public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }

    @Override
    public String getPackage() { return "com.example.addon"; }

    @Override
    public GithubRepo getRepo() { return new GithubRepo("yourname", "donut-addon"); }
}
