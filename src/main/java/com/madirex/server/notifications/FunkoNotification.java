package com.madirex.server.notifications;

import com.madirex.models.Notification;
import com.madirex.models.funko.Funko;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Interfaz para la implementación de notificaciones
 */
public interface FunkoNotification {
    Flux<Notification<Funko>> getNotificationAsFlux();

    FluxSink<Notification<Funko>> notify(Notification<Funko> notification);
}
