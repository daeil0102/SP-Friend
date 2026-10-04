package net.teujaem.spfriend.proxy;

import net.teujaem.spFramework.api.ProxyData;
import net.teujaem.spFramework.api.event.ProxyEvent;
import net.teujaem.spFramework.SPFramework;
import net.teujaem.spfriend.Main;
import net.teujaem.spfriend.service.FriendService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProxyBridge implements Listener {
    private static final String CHANNEL = "SP-Friend";
    private final Main plugin;
    private final FriendService friendService;
    private final Set<UUID> remoteOnline = ConcurrentHashMap.newKeySet();
    private final boolean enabled;

    public ProxyBridge(Main plugin, FriendService friendService) {
        this.plugin = plugin;
        this.friendService = friendService;
        this.enabled = plugin.getConfig().getBoolean("proxy.enabled", true)
                && SPFramework.getInstance().getConfigManager().isProxy();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOnline(String uuid) {
        UUID id = UUID.fromString(uuid);
        return Bukkit.getPlayer(id) != null || remoteOnline.contains(id);
    }

    public void publishPresence(Player player, boolean online) {
        if (!enabled) return;
        send("presence", player.getUniqueId() + ";" + player.getName() + ";" + (online ? "1" : "0"));
    }

    public void publishShutdown() {
        if (!enabled) return;
        Bukkit.getOnlinePlayers().forEach(player -> publishPresence(player, false));
    }

    public void requestPresenceSnapshot() {
        if (enabled) send("presence-query", "all");
    }

    public void notifyPlayer(String uuid, String message) {
        Player local = Bukkit.getPlayer(UUID.fromString(uuid));
        if (local != null) {
            local.sendMessage(message);
            return;
        }
        if (enabled) {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(message.getBytes(StandardCharsets.UTF_8));
            send("notify", uuid + ";" + encoded);
        }
    }

    private void send(String event, String value) {
        try {
            ProxyData.sendToServer(CHANNEL, event, value);
        } catch (Exception exception) {
            plugin.getLogger().warning("프록시 메시지 전송 실패: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onProxyEvent(ProxyEvent event) {
        if (!enabled || !CHANNEL.equalsIgnoreCase(event.getPluginName())) return;
        String value = String.valueOf(event.getValue());
        if ("presence".equalsIgnoreCase(event.getEventName())) handlePresence(value);
        else if ("notify".equalsIgnoreCase(event.getEventName())) handleNotification(value);
        else if ("presence-query".equalsIgnoreCase(event.getEventName()))
            Bukkit.getOnlinePlayers().forEach(player -> publishPresence(player, true));
    }

    private void handlePresence(String value) {
        String[] parts = value.split(";", 3);
        if (parts.length != 3) return;
        try {
            UUID uuid = UUID.fromString(parts[0]);
            if ("1".equals(parts[2])) {
                remoteOnline.add(uuid);
                friendService.rememberPlayer(uuid, parts[1]);
            } else remoteOnline.remove(uuid);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleNotification(String value) {
        String[] parts = value.split(";", 2);
        if (parts.length != 2) return;
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(parts[0]));
            if (player == null) return;
            String message = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            player.sendMessage(message);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
