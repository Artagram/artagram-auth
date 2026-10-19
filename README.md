# ArtaGram Auth (`artagram-auth`)

Microservicio de identidad y autenticación de ArtaGram (`:8081`, base de datos `db_auth`).

## Responsabilidades
- Registro de artistas (`POST /api/auth/registro`), verificación de correo (`GET /api/auth/verificar`) y disponibilidad de nombre (`GET /api/auth/nombre-disponible`).
- Inicio de sesión (`POST /api/auth/login`) con emisión de JWT asimétricos **RS256** (llave privada RSA 2048).
- Exposición del conjunto de llaves públicas en `GET /.well-known/jwks.json` para que `artagram-gateway` y `artagram-lienzo` verifiquen firmas sin compartir secretos simétricos.
- Publicación de eventos de ciclo de vida en RabbitMQ (`artagram.events`):
  - `user.registered`
  - `user.deleted`
- Sincronización del estado `premium` consumiendo `subscription.updated` en la cola `auth.subscription.updated`.
