package com.artagram.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica eventos de ciclo de vida de identidad hacia RabbitMQ (artagram.events)
 * y sincroniza cambios de suscripción Premium en db_auth.
 */
@Component
public class AuthEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final UsuarioRepository usuarioRepository;
    private final String exchange;

    public AuthEventPublisher(RabbitTemplate rabbitTemplate,
                              UsuarioRepository usuarioRepository,
                              @Value("${app.rabbitmq.exchange:artagram.events}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.usuarioRepository = usuarioRepository;
        this.exchange = exchange;
    }

    public void publicarUsuarioRegistrado(Usuario usuario) {
        Map<String, Object> payload = Map.of(
                "usuarioId", usuario.getId().toString(),
                "correo", usuario.getCorreo(),
                "nombreArtista", usuario.getNombreArtista(),
                "rolGlobal", usuario.getRolGlobal().name(),
                "premium", usuario.isPremium(),
                "creadoEn", Instant.now().toString()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "user.registered", payload);
            log.info("Evento user.registered publicado: usuarioId={}, nombreArtista={}",
                    usuario.getId(), usuario.getNombreArtista());
        } catch (Exception e) {
            log.warn("No se pudo publicar user.registered en RabbitMQ para usuarioId={}: {}",
                    usuario.getId(), e.getMessage());
        }
    }

    public void publicarUsuarioEliminado(UUID usuarioId, String nombreArtista) {
        Map<String, Object> payload = Map.of(
                "usuarioId", usuarioId.toString(),
                "nombreArtista", nombreArtista,
                "eliminadoEn", Instant.now().toString()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, "user.deleted", payload);
            log.info("Evento user.deleted publicado: usuarioId={}", usuarioId);
        } catch (Exception e) {
            log.warn("No se pudo publicar user.deleted en RabbitMQ para usuarioId={}: {}",
                    usuarioId, e.getMessage());
        }
    }

    @RabbitListener(queues = "auth.subscription.updated")
    @Transactional
    public void onSubscriptionUpdated(Map<String, Object> payload) {
        try {
            Object rawId = payload.get("usuarioId");
            if (rawId == null) {
                return;
            }
            UUID usuarioId = UUID.fromString(rawId.toString());
            boolean premium = Boolean.TRUE.equals(payload.get("premium"));
            usuarioRepository.findById(usuarioId).ifPresent(u -> {
                u.setPremium(premium);
                usuarioRepository.save(u);
                log.info("Estado premium sincronizado en artagram-auth: usuarioId={}, premium={}", usuarioId, premium);
            });
        } catch (Exception e) {
            log.error("Error procesando subscription.updated en artagram-auth: {}", e.getMessage(), e);
        }
    }
}
