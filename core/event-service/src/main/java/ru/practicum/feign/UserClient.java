package ru.practicum.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.user.UserDto;

import java.util.List;

@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {
    @GetMapping("/internal/users/{userId}")
    UserDto getUser(@PathVariable Long userId);

    @GetMapping("/internal/users")
    List<UserDto> getUsers(@RequestParam List<Long> ids);
}
