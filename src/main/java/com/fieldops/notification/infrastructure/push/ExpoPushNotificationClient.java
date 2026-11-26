package com.fieldops.notification.infrastructure.push;

import com.fieldops.notification.application.PushDeliveryResult;
import com.fieldops.notification.application.PushNotificationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ExpoPushNotificationClient implements PushNotificationClient {

    private final RestClient restClient;
    private final String accessToken;

    public ExpoPushNotificationClient(
            @Value("${app.push.expo-url:https://exp.host/--/api/v2/push/send}") String endpoint,
            @Value("${app.push.expo-access-token:}") String accessToken
    ) {
        this.restClient = RestClient.create(endpoint);
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    @Override
    public PushDeliveryResult send(String pushToken, UUID inspectionId) {
        ExpoPushMessage message = new ExpoPushMessage(
                pushToken,
                "Nova inspeção",
                "Uma nova inspeção foi atribuída a você.",
                Map.of("inspectionId", inspectionId.toString())
        );
        RestClient.RequestBodySpec request = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(message));
        if (!accessToken.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        ExpoPushResponse response = request.retrieve().body(ExpoPushResponse.class);
        boolean invalidToken = response != null
                && response.data() != null
                && response.data().stream().anyMatch(ExpoPushNotificationClient::isInvalidToken);
        return new PushDeliveryResult(invalidToken);
    }

    private static boolean isInvalidToken(ExpoPushTicket ticket) {
        return ticket != null
                && "error".equalsIgnoreCase(ticket.status())
                && ticket.details() != null
                && "DeviceNotRegistered".equalsIgnoreCase(ticket.details().error());
    }

    private record ExpoPushMessage(String to, String title, String body, Map<String, String> data) {
    }

    private record ExpoPushResponse(List<ExpoPushTicket> data) {
    }

    private record ExpoPushTicket(String status, ExpoPushTicketDetails details) {
    }

    private record ExpoPushTicketDetails(String error) {
    }
}
