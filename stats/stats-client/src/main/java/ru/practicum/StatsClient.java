package ru.practicum;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import ru.practicum.DTO.RequestStatisticDto;
import ru.practicum.DTO.ResponseStatisticDto;

import java.net.URI;
import java.util.List;

@Component
public class StatsClient {
    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    private static final String STATS_SERVICE_ID = "stats-server";

    public StatsClient(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.restClient = RestClient.builder().build();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(3000L);

        MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy();
        retryPolicy.setMaxAttempts(3);

        this.retryTemplate = new RetryTemplate();
        this.retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        this.retryTemplate.setRetryPolicy(retryPolicy);
    }

    public void saveHit(RequestStatisticDto requestStatisticDto) {
        URI uri = makeUri("/hit");
        restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestStatisticDto)
                .retrieve()
                .toBodilessEntity();
    }

    public List<ResponseStatisticDto> getStats(String start, String end, List<String> uris, boolean unique) {
        URI baseUri = makeUri("/stats");
        return restClient
                .get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder
                            .scheme(baseUri.getScheme())
                            .host(baseUri.getHost())
                            .port(baseUri.getPort())
                            .path(baseUri.getPath())
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("unique", unique);

                    if (uris != null && !uris.isEmpty()) {
                        uris.forEach(u -> builder.queryParam("uris", u));
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<ResponseStatisticDto>>() {
                });
    }

    private ServiceInstance getInstance() {
        try {
            return discoveryClient
                    .getInstances(STATS_SERVICE_ID)
                    .getFirst();
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Ошибка обнаружения адреса сервиса статистики с id: " + STATS_SERVICE_ID,
                    exception
            );
        }
    }

    private URI makeUri(String path) {
        ServiceInstance instance = retryTemplate.execute(cxt -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }
}
