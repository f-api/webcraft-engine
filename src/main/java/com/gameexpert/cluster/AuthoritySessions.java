package com.gameexpert.cluster;

import com.gameexpert.api.SessionRegistry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** Engine-only directory. Student WorldBroadcaster still sees local physical connections only. */
@Component
@Primary
public final class AuthoritySessions implements SessionRegistry {
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    private static class Key {
        long world;
        String nickname;
        @com.fasterxml.jackson.annotation.JsonCreator
        private Key(@com.fasterxml.jackson.annotation.JsonProperty("world") long world, @com.fasterxml.jackson.annotation.JsonProperty("nickname") String nickname) {
            this.world = world;
            this.nickname = nickname;
        }
    }
    private final Map<Key,Entry> owned = new ConcurrentHashMap<>();
    private final SessionRegistry local;
    private final WorldAuthority authority;
    public AuthoritySessions(@Qualifier("worldSessionRegistry") SessionRegistry local, WorldAuthority authority) {
        this.local = local; this.authority = authority;
    }
    public SessionRegistry local() { return local; }
    public boolean enabled() { return authority.enabled(); }
    private static Key key(Long world, String nickname) { return new Key(world, nickname.toLowerCase(Locale.ROOT)); }
    @Override public Entry register(Long world, String nickname, WebSocketSession session) {
        if (!enabled()) return local.register(world, nickname, session);
        Entry entry = new Entry(session);
        return owned.putIfAbsent(key(world, nickname), entry) == null ? entry : null;
    }
    @Override public Entry get(Long world, String nickname) {
        return enabled() ? owned.get(key(world, nickname)) : local.get(world, nickname);
    }
    @Override public Entry remove(Long world, String nickname, WebSocketSession session) {
        if (!enabled()) return local.remove(world, nickname, session);
        Key key = key(world, nickname); Entry prior = owned.get(key);
        return prior != null && prior.session() == session && owned.remove(key, prior) ? prior : null;
    }
    @Override public Collection<Entry> entries(Long world) {
        return enabled() ? owned.entrySet().stream().filter(e -> e.getKey().world() == world)
                .map(Map.Entry::getValue).toList() : local.entries(world);
    }
    @Override public Set<Long> worldIds() {
        return enabled() ? owned.keySet().stream().map(Key::world)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()) : local.worldIds();
    }
}
