package ru.practicum.util;

import java.util.List;
import java.util.stream.Collectors;

public final class UriUtils {
    private UriUtils() {
    }

    public static String makeEventUri(Long eventId) {
        return "/events/" + eventId;
    }

    public static List<String> makeEventUris(List<Long> eventIds) {
        return eventIds.stream()
                .map(UriUtils::makeEventUri)
                .collect(Collectors.toList());
    }
}
