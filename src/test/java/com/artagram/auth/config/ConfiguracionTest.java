package com.artagram.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.artagram.auth.GoogleProperties;
import com.artagram.auth.GoogleTokenVerifier;
import com.artagram.auth.RsaJwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class ConfiguracionTest {

    @Test
    void rabbitDeclaraLaColaDeSuscripcionesConDeadLetter() {
        RabbitMqConfig config = new RabbitMqConfig();
        TopicExchange exchange = config.artagramEventsExchange("artagram.events");
        Queue cola = config.authSubscriptionUpdatedQueue();
        Binding binding = config.authSubscriptionUpdatedBinding(cola, exchange);

        assertThat(exchange.getName()).isEqualTo("artagram.events");
        assertThat(cola.getArguments()).containsEntry("x-dead-letter-exchange", "artagram.dlx");
        assertThat(binding.getRoutingKey()).isEqualTo("subscription.updated");
        assertThat(config.jackson2JsonMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void googlePropertiesNormalizaLosCorreosAdmin() throws Exception {
        GoogleProperties props = new GoogleProperties();
        props.setClientId("cliente");
        props.setJwksUri("https://example.com/certs");
        props.setAdminEmails(" Admin@Correo.com , ,otro@correo.com");

        assertThat(props.getClientId()).isEqualTo("cliente");
        assertThat(props.getJwksUri()).isEqualTo("https://example.com/certs");
        assertThat(props.getAdminEmails()).contains("Admin@Correo.com");
        assertThat(props.correosAdmin()).containsExactlyInAnyOrder("admin@correo.com", "otro@correo.com");
        GoogleTokenVerifier verifier = new GoogleTokenVerifier(props);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> verifier.verificar("no-es-un-jwt"))
                .isInstanceOf(com.artagram.auth.common.ApiException.class)
                .extracting("code").isEqualTo("TOKEN_GOOGLE_INVALIDO");
    }

    @Test
    void rsaPropertiesGuardaLosPem() {
        RsaJwtProperties props = new RsaJwtProperties();
        props.setPrivateKeyPem("priv");
        props.setPublicKeyPem("pub");

        assertThat(props.getPrivateKeyPem()).isEqualTo("priv");
        assertThat(props.getPublicKeyPem()).isEqualTo("pub");
    }
}
