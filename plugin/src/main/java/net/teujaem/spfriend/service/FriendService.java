package net.teujaem.spfriend.service;

import net.teujaem.spFramework.api.ProxyData;
import net.teujaem.spFramework.api.event.ProxyEvent;
import net.teujaem.spfriend.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 친구 데이터는 직접 DB에 접근하지 않고 SP-Framework ProxyData 프로토콜로만 처리한다.
 */
public final class FriendService implements Listener {
    public static final String CHANNEL = "SP-Friend";
    public static final String REQUEST_EVENT = "friend-command";
    public static final String RESPONSE_EVENT = "friend-response";

    public enum Code {
        SENT, ACCEPTED, DENIED, REMOVED, PLAYER_NOT_FOUND, SELF,
        ALREADY_FRIENDS, REQUEST_EXISTS, INCOMING_REQUEST, REQUEST_NOT_FOUND, NOT_FRIENDS
    }

    public record Result(Code code, String otherUuid, String otherName) {}
    public record FriendView(String uuid, String name) {}

    private record ProxyResponse(Map<String, String> values) {}

    private final Main plugin;
    private final boolean proxyEnabled;
    private final Map<String, CompletableFuture<ProxyResponse>> pending = new ConcurrentHashMap<>();

    public FriendService(Main plugin, boolean proxyEnabled) {
        this.plugin = plugin;
        this.proxyEnabled = proxyEnabled;
    }

    public CompletableFuture<Void> rememberPlayer(UUID uuid, String name) {
        if (!proxyEnabled) return CompletableFuture.completedFuture(null);
        sendOneWay("PLAYER_UPSERT", Map.of("actorUuid", uuid.toString(), "actorName", name));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Result> sendRequest(UUID requesterId, String requesterName, String targetName) {
        return action("ADD", requesterId, requesterName, targetName);
    }

    public CompletableFuture<Result> accept(UUID targetId, String targetName, String requesterName) {
        return action("ACCEPT", targetId, targetName, requesterName);
    }

    public CompletableFuture<Result> deny(UUID targetId, String targetName, String requesterName) {
        return action("DENY", targetId, targetName, requesterName);
    }

    public CompletableFuture<Result> remove(UUID playerId, String playerName, String friendName) {
        return action("REMOVE", playerId, playerName, friendName);
    }

    public CompletableFuture<List<FriendView>> friends(UUID playerId) {
        return list("LIST", playerId);
    }

    public CompletableFuture<List<FriendView>> requests(UUID playerId) {
        return list("REQUESTS", playerId);
    }

    private CompletableFuture<Result> action(String action, UUID actorId, String actorName, String targetName) {
        return request(action, Map.of(
                "actorUuid", actorId.toString(),
                "actorName", actorName,
                "targetName", targetName
        )).thenApply(response -> {
            String rawCode = response.values().getOrDefault("code", "PLAYER_NOT_FOUND");
            Code code;
            try { code = Code.valueOf(rawCode); }
            catch (IllegalArgumentException ignored) { throw new IllegalStateException("알 수 없는 프록시 응답: " + rawCode); }
            return new Result(code, response.values().get("otherUuid"), response.values().getOrDefault("otherName", targetName));
        });
    }

    private CompletableFuture<List<FriendView>> list(String action, UUID playerId) {
        return request(action, Map.of("actorUuid", playerId.toString())).thenApply(response -> {
            String packed = response.values().getOrDefault("items", "");
            if (packed.isBlank()) return List.of();
            List<FriendView> result = new ArrayList<>();
            for (String item : packed.split(";")) {
                String[] fields = item.split(",", 2);
                if (fields.length == 2) result.add(new FriendView(fields[0], fields[1]));
            }
            return result;
        });
    }

    private CompletableFuture<ProxyResponse> request(String action, Map<String, String> values) {
        if (!proxyEnabled) return CompletableFuture.failedFuture(
                new IllegalStateException("SP-Framework config.yaml에서 proxy를 true로 설정하세요."));
        String requestId = UUID.randomUUID().toString();
        Map<String, String> payload = new LinkedHashMap<>(values);
        payload.put("requestId", requestId);
        payload.put("action", action);
        CompletableFuture<ProxyResponse> future = new CompletableFuture<>();
        pending.put(requestId, future);
        future.orTimeout(8, TimeUnit.SECONDS).whenComplete((ignored, error) -> pending.remove(requestId));
        try {
            ProxyData.sendToServer(CHANNEL, REQUEST_EVENT, encode(payload));
        } catch (Exception exception) {
            pending.remove(requestId);
            future.completeExceptionally(exception);
        }
        return future;
    }

    private void sendOneWay(String action, Map<String, String> values) {
        Map<String, String> payload = new LinkedHashMap<>(values);
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("action", action);
        try { ProxyData.sendToServer(CHANNEL, REQUEST_EVENT, encode(payload)); }
        catch (Exception exception) { plugin.getLogger().warning("ProxyData 전송 실패: " + exception.getMessage()); }
    }

    @EventHandler
    public void onProxyResponse(ProxyEvent event) {
        if (!CHANNEL.equalsIgnoreCase(event.getPluginName()) || !RESPONSE_EVENT.equalsIgnoreCase(event.getEventName())) return;
        Map<String, String> values = decode(String.valueOf(event.getValue()));
        CompletableFuture<ProxyResponse> future = pending.remove(values.get("requestId"));
        if (future == null) return;
        String error = values.get("error");
        if (error != null && !error.isBlank()) future.completeExceptionally(new IllegalStateException(error));
        else future.complete(new ProxyResponse(values));
    }

    public static String encode(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (result.length() > 0) result.append('&');
            result.append(key).append('=').append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        });
        return result.toString();
    }

    public static Map<String, String> decode(String payload) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : payload.split("&")) {
            String[] pair = field.split("=", 2);
            if (pair.length != 2) continue;
            try {
                result.put(pair[0], new String(Base64.getUrlDecoder().decode(pair[1]), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    public void shutdown() {
        pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException("플러그인이 종료되었습니다.")));
        pending.clear();
    }
}
