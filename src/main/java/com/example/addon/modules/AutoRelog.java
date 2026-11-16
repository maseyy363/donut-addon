package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

/**
 * Disconnects when a trigger hits (health or Y level), shows a top-right toast,
 * and turns on Meteor's built-in Auto Reconnect so you rejoin automatically.
 */
public class AutoRelog extends Module {
    public enum Mode { Health, YLevel }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("trigger").description("What makes it relog.").defaultValue(Mode.YLevel).build());
    private final Setting<Double> healthLimit = sg.add(new DoubleSetting.Builder()
        .name("health-at-or-below").description("Health points (2 = 1 heart).")
        .defaultValue(2).min(0).sliderMax(20).visible(() -> mode.get() == Mode.Health).build());
    private final Setting<Double> yLimit = sg.add(new DoubleSetting.Builder()
        .name("y-at-or-below").description("Relog when your Y level drops to this.")
        .defaultValue(-2).sliderRange(-64, 320).visible(() -> mode.get() == Mode.YLevel).build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.").defaultValue("BOMBACLAT RELOGGGG").build());
    private final Setting<Boolean> enableReconnect = sg.add(new BoolSetting.Builder()
        .name("turn-on-auto-reconnect").description("Enable Meteor's Auto Reconnect so you rejoin.")
        .defaultValue(true).build());

    private boolean triggered;

    public AutoRelog() {
        super(AddonTemplate.CATEGORY, "auto-relog", "Relogs when health or Y level hits your limit.");
    }

    @Override
    public void onActivate() { triggered = false; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        boolean hit = switch (mode.get()) {
            case Health -> mc.player.getHealth() <= healthLimit.get();
            case YLevel -> mc.player.getY() <= yLimit.get();
        };

        if (!hit) { triggered = false; return; }
        if (triggered) return;
        triggered = true;

        SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
            Text.literal(message.get()), Text.literal("Disconnecting..."));

        if (enableReconnect.get()) {
            AutoReconnect ar = Modules.get().get(AutoReconnect.class);
            if (ar != null && !ar.isActive()) ar.toggle();
        }

        mc.getNetworkHandler().getConnection().disconnect(Text.literal(message.get()));
    }
}
