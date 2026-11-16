package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Passive anti-vanish: a player that disappears from the tab list WITHOUT a leave message in chat
 * has probably vanished (staff vanish) rather than logged out. Only reads packets the server already
 * sent you; it never sends anything.
 */
public class AntiVanish extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> onlyNearby = sg.add(new BoolSetting.Builder()
        .name("only-nearby").description("Only alert for players that were loaded near you (far fewer false alarms).")
        .defaultValue(true).build());
    private final Setting<Double> waitSeconds = sg.add(new DoubleSetting.Builder()
        .name("wait-seconds").description("How long to wait for a leave message before calling it a vanish.")
        .defaultValue(2.0).min(0.5).max(10).sliderMax(5).build());
    private final Setting<String> leaveWords = sg.add(new StringSetting.Builder()
        .name("leave-words").description("Comma-separated words that mean a normal logout in chat.")
        .defaultValue("left the game,left,quit,disconnected").build());
    private final Setting<String> message = sg.add(new StringSetting.Builder()
        .name("message").description("Toast title.").defaultValue("ANTI VANISH BOMBACLAT").build());
    private final Setting<Boolean> chat = sg.add(new BoolSetting.Builder()
        .name("chat-message").defaultValue(true).build());

    private static final class Pending {
        final String display;
        int ticks;
        Pending(String display, int ticks) { this.display = display; this.ticks = ticks; }
    }

    /** key = last word of the name, lowercase (what a leave message would contain) */
    private final Map<String, Pending> pending = new HashMap<>();

    public AntiVanish() {
        super(AddonTemplate.CATEGORY, "donut-anti-vanish", "Alerts when a player vanishes instead of logging out.");
    }

    @Override
    public void onActivate() { pending.clear(); }

    @Override
    public void onDeactivate() { pending.clear(); }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof PlayerRemoveS2CPacket p) {
            ClientPlayNetworkHandler handler = mc.getNetworkHandler();
            if (handler == null) return;
            for (UUID id : p.profileIds()) {
                if (id.equals(mc.player.getUuid())) continue;
                PlayerListEntry entry = handler.getPlayerListEntry(id);
                if (entry == null) continue;
                if (onlyNearby.get() && mc.world.getPlayerByUuid(id) == null) continue;

                String display = mc.inGameHud.getPlayerListHud().getPlayerName(entry).getString().trim();
                if (display.isEmpty()) continue;
                String[] parts = display.split("\\s+");
                String key = parts[parts.length - 1].toLowerCase();
                pending.put(key, new Pending(display, Math.max(1, (int) Math.round(waitSeconds.get() * 20))));
            }
        } else if (event.packet instanceof GameMessageS2CPacket m) {
            if (pending.isEmpty()) return;
            String text = m.content().getString().toLowerCase();
            boolean isLeave = false;
            for (String w : leaveWords.get().split(",")) {
                String word = w.trim().toLowerCase();
                if (!word.isEmpty() && text.contains(word)) { isLeave = true; break; }
            }
            if (isLeave) pending.keySet().removeIf(text::contains);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<String, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Pending p = it.next().getValue();
            if (--p.ticks > 0) continue;
            it.remove();

            SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(message.get()), Text.literal(p.display + " vanished?"));
            if (chat.get()) info("%s vanished (left the tab list without a leave message).", p.display);
        }
    }
}
