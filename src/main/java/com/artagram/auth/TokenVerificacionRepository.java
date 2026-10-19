package com.artagram.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, UUID> {

    Optional<TokenVerificacion> findByToken(String token);
}
