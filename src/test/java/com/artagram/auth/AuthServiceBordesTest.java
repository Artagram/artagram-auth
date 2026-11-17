package com.artagram.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.auth.common.ApiException;
import com.artagram.auth.dto.GoogleLoginResponse;
import com.artagram.auth.dto.NombreDisponibleResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuthServiceBordesTest {

    private UsuarioRepository repo;
    private GoogleTokenVerifier verifier;
    private AuthEventPublisher publisher;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        verifier = mock(GoogleTokenVerifier.class);
        publisher = mock(AuthEventPublisher.class);
        RsaJwtProperties props = new RsaJwtProperties();
        props.setPrivateKeyPath("");
        props.setPublicKeyPath("");
        service = new AuthService(repo, new RsaJwtService(props), verifier, new GoogleProperties(),
                new NombreSugeridoGenerator(), publisher, new SimpleMeterRegistry());
        when(repo.findByGoogleSub(anyString())).thenReturn(Optional.empty());
        when(repo.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private void identidad(String nombre, String correo) {
        when(verifier.verificar("tok")).thenReturn(new GoogleIdentidad("sub-1", correo, nombre));
    }

    @Test
    void completarRegistroDeUnaCuentaExistenteEsConflicto() {
        identidad("Ana", "ana@gmail.com");
        when(repo.findByGoogleSub("sub-1")).thenReturn(Optional.of(new Usuario("ana@gmail.com", "ana", "sub-1")));

        assertThatThrownBy(() -> service.completarRegistroGoogle("tok", "ana_art"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CUENTA_YA_EXISTE");
    }

    @Test
    void completarRegistroConCarreraEnLaBaseEsConflicto() {
        identidad("Ana", "ana@gmail.com");
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThatThrownBy(() -> service.completarRegistroGoogle("tok", "ana_art"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REGISTRO_DUPLICADO");
    }

    @Test
    void sugiereUnNombreBasadoEnElCorreoSiNoHayNombreYRecortaGuiones() {
        identidad(null, "__ana.maria__@gmail.com");

        GoogleLoginResponse respuesta = service.ingresarConGoogle("tok");

        assertThat(respuesta.requiereNombre()).isTrue();
        assertThat(respuesta.nombreSugerido()).isEqualTo("ana_maria");
    }

    @Test
    void conUnNombreMuyCortoSugiereArtista() {
        identidad("A", "a@gmail.com");

        assertThat(service.ingresarConGoogle("tok").nombreSugerido()).isEqualTo("artista");
    }

    @Test
    void siElNombreBaseEstaOcupadoSugiereUnaVariante() {
        identidad("Ana Maria", "ana@gmail.com");
        when(repo.existsByNombreArtistaIgnoreCase("ana_maria")).thenReturn(true);

        String sugerido = service.ingresarConGoogle("tok").nombreSugerido();

        assertThat(sugerido).startsWith("ana_maria").isNotEqualTo("ana_maria");
    }

    @Test
    void siNingunaVarianteEstaLibreNoSugiereNada() {
        identidad("Ana Maria", "ana@gmail.com");
        when(repo.existsByNombreArtistaIgnoreCase(anyString())).thenReturn(true);

        assertThat(service.ingresarConGoogle("tok").nombreSugerido()).isEmpty();
    }

    @Test
    void verificarNombreDisponibleDevuelveSugerenciasSiEstaOcupado() {
        when(repo.existsByNombreArtistaIgnoreCase("lucia")).thenReturn(true);

        NombreDisponibleResponse ocupado = service.verificarNombreDisponible("lucia");
        NombreDisponibleResponse libre = service.verificarNombreDisponible("nueva");

        assertThat(ocupado.disponible()).isFalse();
        assertThat(ocupado.sugerencias()).hasSize(3).allMatch(s -> s.startsWith("lucia"));
        assertThat(libre.disponible()).isTrue();
        assertThat(libre.sugerencias()).isEmpty();
    }

    @Test
    void eliminarCuentaBorraYPublicaElEvento() {
        Usuario u = new Usuario(UUID.randomUUID(), "a@b.com", "ana");
        when(repo.findById(u.getId())).thenReturn(Optional.of(u));

        service.eliminarCuenta(u.getId());

        verify(repo).delete(u);
        verify(publisher).publicarUsuarioEliminado(u.getId(), "ana");
    }

    @Test
    void eliminarUnaCuentaInexistenteEs404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarCuenta(id))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("USUARIO_NO_ENCONTRADO");
    }

    @Test
    void elGeneradorTruncaBasesLargas() {
        var sugerencias = new NombreSugeridoGenerator().sugerir("x".repeat(40), n -> true, 7);

        assertThat(sugerencias).hasSize(7).allMatch(s -> s.length() <= 30);
    }
}
