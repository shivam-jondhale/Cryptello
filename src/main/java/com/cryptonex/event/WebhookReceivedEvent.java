package com.cryptonex.event;

import com.cryptonex.model.WebhookEvent;
import org.springframework.context.ApplicationEvent;

public class WebhookReceivedEvent extends ApplicationEvent {

    private final WebhookEvent webhookEvent;

    public WebhookReceivedEvent(Object source, WebhookEvent webhookEvent) {
        super(source);
        this.webhookEvent = webhookEvent;
    }

    public WebhookEvent getWebhookEvent() {
        return webhookEvent;
    }
}
