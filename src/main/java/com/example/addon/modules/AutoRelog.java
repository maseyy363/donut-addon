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
 * When a trigger hits (Y level or health): shows a top-right toast, waits a short delay,
 * then disconnects. Turns on Meteor's built-in Auto Reconnect so you rejoin automatically.
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
    private final Setting<Double> delaySeconds = sg.add(new DoubleSetting.Builder()
        .name("delay-seconds").description("Wait this long after the toast before disconnecting.")
        .defaultValue(0.5).min(0).max(10).sliderMax(3).build());
    private final Setting<Double> cooldownSeconds = sg.add(new DoubleSetting.Builder()
        .name("cooldown-seconds").description("After a relog, wait this long in-game before it can trigger again (stops relog loops).")
        .defaultValue(6).min(0).max(60).sliderMax(20).build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.").defaultValue("BOMBACLAT RELOGGGG").build());
    private final Setting<String> subtitle = sg.add(new StringSetting.Builder()
        .name("subtitle").description("Second line of the toast.").defaultValue("bombaclat relog time").build());
    private final Setting<Boolean> enableReconnect = sg.add(new BoolSetting.Builder()
        .name("turn-on-auto-reconnect").description("Enable Meteor's Auto Reconnect so you rejoin.")
        .defaultValue(true).build());

    private boolean pending;   // trigger hit, counting down to the disconnect
    private boolean done;      // already disconnected for this trigger
    private int countdown;
    private int cooldown;      // in-game ticks left before it may trigger again

    public AutoRelog() {
        super(AddonTemplate.CATEGORY, "donut-auto-relog", "Relogs (after a short delay) when Y level or health hits your limit.");
    }

    @Override
    public void onActivate() { pending = false; done = false; countdown = 0; cooldown = 0; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // Cooldown after a relog. These ticks only run while you are in a world, so the
        // wait is counted from the moment you are back in the game.
        if (cooldown > 0) { cooldown--; return; }

        // Counting down to the disconnect.
        if (pending) {
            if (--countdown <= 0) {
                pending = false;
                done = true;
                cooldown = (int) Math.round(cooldownSeconds.get() * 20);
                mc.getNetworkHandler().getConnection().disconnect(Text.literal(message.get()));
            }
            return;
        }

        boolean hit = switch (mode.get()) {
            case Health -> mc.player.getHealth() <= healthLimit.get();
            case YLevel -> mc.player.getY() <= yLimit.get();
        };

        if (!hit) { done = false; return; }
        if (done) return;

        // Trigger: toast now, make sure reconnect is on, then disconnect after the delay.
        SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
            Text.literal(message.get()), Text.literal(subtitle.get()));

        if (enableReconnect.get()) {
            AutoReconnect ar = Modules.get().get(AutoReconnect.class);
            if (ar != null && !ar.isActive()) ar.toggle();
        }

        countdown = Math.max(1, (int) Math.round(delaySeconds.get() * 20));
        pending = true;
    }
}
