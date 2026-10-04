package net.teujaem.spfriend.listener;

import net.teujaem.spfriend.Main;
import net.teujaem.spfriend.proxy.ProxyBridge;
import net.teujaem.spfriend.service.FriendService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final Main plugin;
    private final FriendService friendService;
    private final ProxyBridge proxyBridge;

    public PlayerListener(Main plugin, FriendService friendService, ProxyBridge proxyBridge) {
        this.plugin = plugin;
        this.friendService = friendService;
        this.proxyBridge = proxyBridge;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        friendService.rememberPlayer(player.getUniqueId(), player.getName());
        proxyBridge.publishPresence(player, true);
        friendService.requests(player.getUniqueId()).thenAccept(requests -> plugin.runSync(() -> {
            if (!requests.isEmpty() && player.isOnline())
                player.sendMessage("§e[친구] 대기 중인 친구 요청이 §f" + requests.size() + "개§e 있습니다. §7/친구 요청");
        }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        proxyBridge.publishPresence(event.getPlayer(), false);
    }
}
