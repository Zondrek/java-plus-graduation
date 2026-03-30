package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.enumeration.ParticipationStatus;

@FeignClient(name = "request-service", fallback = RequestClientFallback.class)
public interface RequestClient {

    @GetMapping("/internal/requests/exists")
    boolean participationExists(@RequestParam Long userId,
                                @RequestParam Long eventId,
                                @RequestParam ParticipationStatus status);
}
