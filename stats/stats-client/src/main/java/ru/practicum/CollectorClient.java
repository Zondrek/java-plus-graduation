package ru.practicum;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import stats.service.collector.ActionTypeProto;
import stats.service.collector.UserActionControllerGrpc;
import stats.service.collector.UserActionProto;

import java.time.Instant;

@Component
@Slf4j
public class CollectorClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub collectorStub;

    public void collectUserAction(long userId, long eventId, ActionTypeProto actionType) {
        try {
            Instant now = Instant.now();
            UserActionProto request = UserActionProto.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(actionType)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .build();

            collectorStub.collectUserAction(request);
            log.info("Sent user action: userId={}, eventId={}, type={}", userId, eventId, actionType);
        } catch (Exception e) {
            log.warn("Failed to send user action to collector: {}", e.getMessage());
        }
    }
}
