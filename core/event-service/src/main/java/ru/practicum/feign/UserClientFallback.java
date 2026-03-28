package ru.practicum.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.dto.user.UserDto;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class UserClientFallback implements UserClient {
    @Override
    public UserDto getUser(Long userId) {
        log.warn("user-service unavailable, returning placeholder for userId={}", userId);
        return UserDto.builder().id(userId).name("Unknown").email("unknown@unknown.com").build();
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids) {
        log.warn("user-service unavailable, returning empty list");
        return Collections.emptyList();
    }
}
