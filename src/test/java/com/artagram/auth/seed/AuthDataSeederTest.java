package com.artagram.auth.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.auth.AuthEventPublisher;
import com.artagram.auth.Usuario;
import com.artagram.auth.UsuarioRepository;
import org.junit.jupiter.api.Test;

class AuthDataSeederTest {

    @Test
    void creaSoloLosUsuariosDemoQueNoExisten() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        AuthEventPublisher publisher = mock(AuthEventPublisher.class);
        when(repo.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        when(repo.existsByCorreoIgnoreCase("lucia.mora@correo.com")).thenReturn(true);

        new AuthDataSeeder(repo, publisher).run();

        verify(repo, times(11)).save(any(Usuario.class));
        verify(publisher, times(11)).publicarUsuarioRegistrado(any(Usuario.class));
    }
}
