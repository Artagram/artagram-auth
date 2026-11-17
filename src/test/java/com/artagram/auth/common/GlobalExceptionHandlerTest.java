package com.artagram.auth.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    @Test
    void rutaInexistenteDevuelve404() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(registry);

        var respuesta = handler.handleRecursoNoEncontrado(mock(NoResourceFoundException.class));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().code()).isEqualTo("RECURSO_NO_ENCONTRADO");
        assertThat(registry.counter("http_errores_5xx_total").count()).isZero();
    }

    @Test
    void lasFabricasDeApiExceptionUsanElEstadoCorrecto() {
        assertThat(ApiException.badRequest("A", "a").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiException.conflict("B", "b").getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ApiException.notFound("C", "c").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiException.forbidden("D", "d").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiException e = ApiException.unauthorized("E", "e");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(e.getCode()).isEqualTo("E");
        assertThat(e.getMessage()).isEqualTo("e");
    }
}
