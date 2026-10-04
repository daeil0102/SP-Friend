package net.teujaem.spfriend;

import net.teujaem.spFramework.SPFramework;
import net.teujaem.spfriend.command.FriendCommand;
import net.teujaem.spfriend.listener.PlayerListener;
import net.teujaem.spfriend.proxy.ProxyBridge;
import net.teujaem.spfriend.service.FriendService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private static Main instance;
    private FriendService friendService;
    private ProxyBridge proxyBridge;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            SPFramework framework = SPFramework.getInstance();
            friendService = new FriendService(this, framework.getConfigManager().isProxy());
            proxyBridge = new ProxyBridge(this, friendService);

            FriendCommand friendCommand = new FriendCommand(this, friendService, proxyBridge);
            PluginCommand command = getCommand("friend");
            if (command == null) throw new IllegalStateException("plugin.yml에 friend 명령어가 없습니다.");
            command.setExecutor(friendCommand);
            command.setTabCompleter(friendCommand);

            Bukkit.getPluginManager().registerEvents(new PlayerListener(this, friendService, proxyBridge), this);
            Bukkit.getPluginManager().registerEvents(proxyBridge, this);
            Bukkit.getPluginManager().registerEvents(friendService, this);
            Bukkit.getScheduler().runTaskLater(this, proxyBridge::requestPresenceSnapshot, 20L);
            Bukkit.getOnlinePlayers().forEach(player -> {
                friendService.rememberPlayer(player.getUniqueId(), player.getName());
                proxyBridge.publishPresence(player, true);
            });
            getLogger().info("SP-Friend enabled (proxy=" + proxyBridge.isEnabled() + ")");
        } catch (Exception exception) {
            getLogger().severe("SP-Friend 초기화 실패: " + exception.getMessage());
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (proxyBridge != null) proxyBridge.publishShutdown();
        if (friendService != null) friendService.shutdown();
        friendService = null;
        proxyBridge = null;
        instance = null;
    }

    public void runSync(Runnable task) {
        if (isEnabled()) Bukkit.getScheduler().runTask(this, task);
    }

    public static Main getInstance() {
        return instance;
    }
}
