package net.teujaem.spfriend.command;

import net.teujaem.spfriend.Main;
import net.teujaem.spfriend.proxy.ProxyBridge;
import net.teujaem.spfriend.service.FriendService;
import net.teujaem.spfriend.service.FriendService.FriendView;
import net.teujaem.spfriend.service.FriendService.Result;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class FriendCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§d[친구] §f";
    private final Main plugin;
    private final FriendService service;
    private final ProxyBridge proxy;

    public FriendCommand(Main plugin, FriendService service, ProxyBridge proxy) {
        this.plugin = plugin;
        this.service = service;
        this.proxy = proxy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("도움말")) {
            help(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list") || action.equals("목록")) {
            displayFriends(player);
            return true;
        }
        if (action.equals("requests") || action.equals("요청")) {
            displayRequests(player);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + "플레이어 이름을 입력하세요.");
            return true;
        }

        CompletableFuture<Result> operation = switch (action) {
            case "add", "추가" -> service.sendRequest(player.getUniqueId(), player.getName(), args[1]);
            case "accept", "수락" -> service.accept(player.getUniqueId(), player.getName(), args[1]);
            case "deny", "거절" -> service.deny(player.getUniqueId(), player.getName(), args[1]);
            case "remove", "삭제" -> service.remove(player.getUniqueId(), player.getName(), args[1]);
            default -> null;
        };
        if (operation == null) {
            help(player);
            return true;
        }
        operation.whenComplete((result, error) -> plugin.runSync(() -> {
            if (error != null) {
                plugin.getLogger().warning("친구 명령 처리 실패: " + error.getMessage());
                player.sendMessage(PREFIX + "처리 중 오류가 발생했습니다.");
                return;
            }
            handleResult(player, result);
        }));
        return true;
    }

    private void handleResult(Player player, Result result) {
        switch (result.code()) {
            case SENT -> {
                player.sendMessage(PREFIX + "§e" + result.otherName() + "§f님에게 친구 요청을 보냈습니다.");
                proxy.notifyPlayer(result.otherUuid(), PREFIX + "§e" + player.getName() + "§f님이 친구 요청을 보냈습니다. §7/친구 수락 " + player.getName());
            }
            case ACCEPTED -> {
                player.sendMessage(PREFIX + "§e" + result.otherName() + "§f님과 친구가 되었습니다.");
                proxy.notifyPlayer(result.otherUuid(), PREFIX + "§e" + player.getName() + "§f님이 친구 요청을 수락했습니다.");
            }
            case DENIED -> player.sendMessage(PREFIX + result.otherName() + "님의 친구 요청을 거절했습니다.");
            case REMOVED -> {
                player.sendMessage(PREFIX + result.otherName() + "님을 친구 목록에서 삭제했습니다.");
                proxy.notifyPlayer(result.otherUuid(), PREFIX + player.getName() + "님과의 친구 관계가 해제되었습니다.");
            }
            case PLAYER_NOT_FOUND -> player.sendMessage(PREFIX + "해당 플레이어를 찾을 수 없습니다. 한 번 이상 접속한 플레이어만 추가할 수 있습니다.");
            case SELF -> player.sendMessage(PREFIX + "자기 자신은 친구로 추가할 수 없습니다.");
            case ALREADY_FRIENDS -> player.sendMessage(PREFIX + "이미 친구인 플레이어입니다.");
            case REQUEST_EXISTS -> player.sendMessage(PREFIX + "이미 친구 요청을 보냈습니다.");
            case INCOMING_REQUEST -> player.sendMessage(PREFIX + "상대방이 먼저 요청했습니다. §7/친구 수락 " + result.otherName());
            case REQUEST_NOT_FOUND -> player.sendMessage(PREFIX + "해당 친구 요청을 찾을 수 없습니다.");
            case NOT_FRIENDS -> player.sendMessage(PREFIX + "친구 목록에 없는 플레이어입니다.");
        }
    }

    private void displayFriends(Player player) {
        service.friends(player.getUniqueId()).whenComplete((friends, error) -> plugin.runSync(() -> {
            if (error != null) {
                player.sendMessage(PREFIX + "친구 목록을 불러오지 못했습니다.");
                return;
            }
            player.sendMessage("§8§m---------------- §d친구 목록 §8§m----------------");
            if (friends.isEmpty()) player.sendMessage("§7등록된 친구가 없습니다.");
            for (FriendView friend : friends) {
                boolean online = proxy.isOnline(friend.uuid());
                player.sendMessage((online ? "§a● " : "§8● ") + "§f" + friend.name() + (online ? " §7(온라인)" : " §7(오프라인)"));
            }
            player.sendMessage("§8§m-------------------------------------------");
        }));
    }

    private void displayRequests(Player player) {
        service.requests(player.getUniqueId()).whenComplete((requests, error) -> plugin.runSync(() -> {
            if (error != null) {
                player.sendMessage(PREFIX + "친구 요청을 불러오지 못했습니다.");
                return;
            }
            player.sendMessage("§8§m---------------- §d받은 요청 §8§m----------------");
            if (requests.isEmpty()) player.sendMessage("§7대기 중인 요청이 없습니다.");
            for (FriendView request : requests)
                player.sendMessage("§e" + request.name() + " §7- /친구 수락 " + request.name() + " §8| §7/친구 거절 " + request.name());
            player.sendMessage("§8§m-------------------------------------------");
        }));
    }

    private void help(Player player) {
        player.sendMessage("§8§m---------------- §dSP-Friend §8§m----------------");
        player.sendMessage("§f/친구 추가 <닉네임> §7- 친구 요청 보내기");
        player.sendMessage("§f/친구 수락 <닉네임> §7- 요청 수락");
        player.sendMessage("§f/친구 거절 <닉네임> §7- 요청 거절");
        player.sendMessage("§f/친구 삭제 <닉네임> §7- 친구 삭제");
        player.sendMessage("§f/친구 목록 §7- 전체 친구와 접속 상태");
        player.sendMessage("§f/친구 요청 §7- 받은 요청 목록");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(Arrays.asList("추가", "수락", "거절", "삭제", "목록", "요청"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("추가"))
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value);
        return result;
    }
}
