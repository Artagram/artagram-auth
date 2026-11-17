package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AuthEventPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final AuthEventPublisher publisher = new AuthEventPublisher(rabbit, repo, "artagram.events");
    private final Usuario usuario = new Usuario(UUID.randomUUID(), "lucia@correo.com", "lucia");

    @Test
    @SuppressWarnings("unchecked")
    void publicaRegistroLoginYEliminacion() {
        publisher.publicarUsuarioRegistrado(usuario);
        publisher.publicarLogin(usuario);
        publisher.publicarUsuarioEliminado(usuario.getId(), "lucia");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq("artagram.events"), eq("user.registered"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue())
                .containsEntry("nombreArtista", "lucia")
                .containsEntry("rolGlobal", "USUARIO");
        verify(rabbit).convertAndSend(eq("artagram.events"), eq("user.login"), any(Object.class));
        verify(rabbit).convertAndSend(eq("artagram.events"), eq("user.deleted"), any(Object.class));
    }

    @Test
    void siRabbitFallaNoPropagaElError() {
        doThrow(new AmqpException("caido")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> {
            publisher.publicarUsuarioRegistrado(usuario);
            publisher.publicarLogin(usuario);
            publisher.publicarUsuarioEliminado(usuario.getId(), "lucia");
        }).doesNotThrowAnyException();
    }

    @Test
    void sincronizaElEstadoPremium() {
        when(repo.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        publisher.onSubscriptionUpdated(Map.of("usuarioId", usuario.getId().toString(), "premium", true));

        assertThat(usuario.isPremium()).isTrue();
        verify(repo).save(usuario);
    }

    @Test
    void ignoraEventosSinUsuarioOConIdInvalido() {
        publisher.onSubscriptionUpdated(new HashMap<>());
        publisher.onSubscriptionUpdated(Map.of("usuarioId", "no-uuid"));
        when(repo.findById(any())).thenReturn(Optional.empty());
        publisher.onSubscriptionUpdated(Map.of("usuarioId", UUID.randomUUID().toString(), "premium", true));

        verify(repo, never()).save(any());
    }
}
