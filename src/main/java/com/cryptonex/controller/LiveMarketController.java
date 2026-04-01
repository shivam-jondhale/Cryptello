package com.cryptonex.controller;

import com.cryptonex.market.model.PriceTick;
import com.cryptonex.market.model.TradeEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/market")
public class LiveMarketController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    @GetMapping("/events")
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    @GetMapping("/user-events")
    public SseEmitter streamUserEvents(java.security.Principal principal) {
        if (principal == null)
            return null;
        String userId = principal.getName(); // Or get ID from principal if possible

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeUserEmitter(userId, emitter));
        emitter.onTimeout(() -> removeUserEmitter(userId, emitter));
        return emitter;
    }

    private void removeUserEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = userEmitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    public void broadcast(PriceTick tick) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(tick);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public void sendToUser(TradeEvent event) {
        List<SseEmitter> list = userEmitters.get(event.userId());
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(event);
                } catch (Exception e) {
                    removeUserEmitter(event.userId(), emitter);
                }
            }
        }
    }
}
