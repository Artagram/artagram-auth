# ArtaGram Auth (`artagram-auth`)

Microservicio de identidad y autenticación de ArtaGram (`:8081`, base de datos `db_auth`).

## Descripción del servicio

`artagram-auth` es el microservicio responsable de la identidad digital dentro de ArtaGram: registra artistas, verifica su correo, valida credenciales en el login y emite los tokens JWT que el resto del ecosistema (gateway, lienzo, etc.) usa para reconocer a un usuario autenticado. Existe como servicio independiente porque la emisión y verificación de identidad es una responsabilidad transversal que *todos* los demás microservicios necesitan, pero que ninguno debería implementar por su cuenta: al concentrar aquí la única llave privada RSA y el `PasswordEncoder`, el resto de los servicios solo necesita la llave pública (vía JWKS) para confiar en un token, sin tener que conocer contraseñas, hashes ni la lógica de registro/verificación. Esto permite que `artagram-auth` evolucione sus reglas de negocio de cuentas (verificación de correo, disponibilidad de nombre de artista, eliminación de cuenta, sincronización de estado `premium`) de forma aislada, con su propia base de datos (`db_auth`) y su propio ciclo de despliegue, sin acoplarse al resto de los dominios de ArtaGram.

## Responsabilidades
- Registro de artistas (`POST /api/auth/registro`), verificación de correo (`GET /api/auth/verificar`) y disponibilidad de nombre (`GET /api/auth/nombre-disponible`).
- Inicio de sesión (`POST /api/auth/login`) con emisión de JWT asimétricos **RS256** (llave privada RSA 2048).
- Exposición del conjunto de llaves públicas en `GET /.well-known/jwks.json` para que `artagram-gateway` y `artagram-lienzo` verifiquen firmas sin compartir secretos simétricos.
- Publicación de eventos de ciclo de vida en RabbitMQ (`artagram.events`):
  - `user.registered`
  - `user.deleted`
- Sincronización del estado `premium` consumiendo `subscription.updated` en la cola `auth.subscription.updated`.

## Diagrama de clases

```mermaid
classDiagram
    class AuthController {
        +registrar(RegistroRequest) RegistroResponse
        +verificar(String token)
        +login(LoginRequest) LoginResponse
        +nombreDisponible(String) NombreDisponibleResponse
        +eliminarCuenta(String xUserId, HttpServletRequest)
    }
    class JwksController {
        +jwks() Map~String,Object~
    }
    class AuthService {
        +registrar(RegistroRequest) RegistroResponse
        +verificar(String token)
        +login(LoginRequest) LoginResponse
        +verificarNombreDisponible(String) NombreDisponibleResponse
        +eliminarCuenta(UUID)
    }
    class RsaJwtService {
        -RSAPrivateKey privateKey
        -RSAPublicKey publicKey
        +generarToken(Usuario) String
        +validar(String) Optional~TokenPrincipal~
        +obtenerJwks() Map
    }
    class RsaJwtProperties {
        +keyId String
        +privateKeyPath String
        +privateKeyPem String
        +accessTokenMinutes int
    }
    class TokenPrincipal {
        <<record>>
        +usuarioId UUID
        +rolGlobal String
        +premium boolean
    }
    class EmailService {
        <<interface>>
        +enviarVerificacion(String, String, String)
    }
    class LogEmailService
    class SmtpEmailService {
        -JavaMailSender mailSender
    }
    class EmailProperties {
        +modo String
        +verificacionRequerida boolean
    }
    class AuthEventPublisher {
        +publicarUsuarioRegistrado(Usuario)
        +publicarUsuarioEliminado(UUID, String)
        +onSubscriptionUpdated(Map)
    }
    class NombreSugeridoGenerator {
        +sugerir(String, Predicate, int) List~String~
    }
    class Usuario {
        +id UUID
        +correo String
        +passwordHash String
        +rolGlobal RolGlobal
        +premium boolean
        +marcarVerificado()
    }
    class TokenVerificacion {
        +token String
        +expiraEn Instant
        +estaExpirado() boolean
        +marcarUsado()
    }
    class RolGlobal {
        <<enumeration>>
        USUARIO
        MODERADOR
        ADMIN
    }
    class UsuarioRepository {
        <<interface>>
    }
    class TokenVerificacionRepository {
        <<interface>>
    }

    AuthController --> AuthService
    AuthController --> RsaJwtService : valida token manual (DELETE cuenta)
    JwksController --> RsaJwtService
    AuthService --> UsuarioRepository
    AuthService --> TokenVerificacionRepository
    AuthService --> RsaJwtService
    AuthService --> EmailService : usa interfaz
    AuthService --> EmailProperties
    AuthService --> NombreSugeridoGenerator
    AuthService --> AuthEventPublisher
    EmailService <|.. LogEmailService
    EmailService <|.. SmtpEmailService
    SmtpEmailService --> EmailProperties
    RsaJwtService --> RsaJwtProperties
    RsaJwtService ..> TokenPrincipal : crea
    UsuarioRepository --> Usuario
    TokenVerificacionRepository --> TokenVerificacion
    TokenVerificacion --> Usuario
    Usuario --> RolGlobal
    AuthEventPublisher --> UsuarioRepository
```

La capa de dominio (`Usuario`, `TokenVerificacion`) es delgada y persistente vía JPA; la lógica de negocio vive en `AuthService`, que orquesta validación, hashing de contraseña (`PasswordEncoder`), emisión de token (`RsaJwtService`) y notificación de eventos (`AuthEventPublisher`) sin conocer los detalles de implementación de ninguno.

## Diseño y Arquitectura

`artagram-auth` es el emisor único de identidad del sistema: registra artistas, valida credenciales y firma JWT **RS256** (asimétrico) que el resto de los microservicios verifica localmente contra la llave pública expuesta en `/.well-known/jwks.json` — nadie más que este servicio necesita conocer la llave privada. Ver el [Diagrama de clases](#diagrama-de-clases) arriba.

### Principios SOLID

- **SRP** — Parcialmente cumplido. `AuthService` agrupa 5 casos de uso del ciclo de vida de la cuenta (registro, verificación, login, disponibilidad de nombre, eliminación); es cohesivo pero ya es candidato a dividirse en comandos si crece. `RsaJwtService` es el caso más claro de mezcla de responsabilidades: carga/generación de llaves, emisión, validación y serialización JWKS conviven en una sola clase. `AuthEventPublisher` mezcla el rol de *publisher* (`publicarUsuarioRegistrado/Eliminado`) con el de *listener* (`onSubscriptionUpdated`) — el nombre de la clase ya no describe todo lo que hace.
- **OCP** — Bien resuelto con `EmailService`: `LogEmailService` y `SmtpEmailService` implementan la misma interfaz y se activan por `@ConditionalOnProperty(app.email.mode)`; agregar un proveedor nuevo (p. ej. Amazon SES) no toca `AuthService` ni ninguna otra clase existente.
- **LSP** — Sin violaciones: ambas implementaciones de `EmailService` cumplen el mismo contrato (mismo método, mismas precondiciones) y son intercambiables sin sorpresas.
- **ISP** — `EmailService` es una interfaz de un solo método; no hay interfaces infladas forzando implementaciones vacías.
- **DIP** — `AuthService` depende de abstracciones inyectadas (`EmailService`, `PasswordEncoder`) vía constructor, no de implementaciones concretas — buen desacople. La excepción es `RsaJwtService`: `AuthService` y `AuthController` dependen de la clase concreta (no existe una interfaz `JwtService`), así que cambiar el algoritmo de firma exigiría tocar la clase en uso, no solo añadir una implementación nueva. Es una decisión razonable dado que RS256 + JWKS es una decisión arquitectónica estable, pero vale la nota.

### Puntos de extensibilidad

- **Proveedor de correo** (`EmailService`): agregar SES/SendGrid/etc. es implementar la interfaz + anotar con `@ConditionalOnProperty`, sin tocar código existente.
- **Disponibilidad de nombre de artista** (`NombreSugeridoGenerator.sugerir`): recibe un `Predicate<String>` como criterio de disponibilidad — el generador no sabe nada de JPA ni de `UsuarioRepository`, lo que lo hace testeable en aislamiento y reutilizable si otro servicio necesita sugerencias de nombre.
- **Origen de las llaves RSA** (`RsaJwtProperties`/`RsaJwtService`): soporta PEM inline por variable de entorno, archivo en disco, o generación efímera en memoria como última red de seguridad en desarrollo — las tres rutas ya están pensadas para no depender de un archivo montado en producción.
- Nota de diseño: `SecurityConfig` registra una `SecurityFilterChain` que permite todas las rutas (`anyRequest().permitAll()`) — Spring Security aquí solo aporta el `PasswordEncoder` (BCrypt) y la política de sesión *stateless*; la única autorización real (`DELETE /api/auth/cuenta`) se resuelve a mano en `AuthController.resolverUsuarioAutenticado`. Es intencional (el filtrado de rutas públicas/privadas vive en el gateway), pero es una dependencia que hoy no hace su trabajo habitual de autorización.

### Preparación para AWS

- **Base de datos**: `db_auth` migra directo a una instancia (o esquema) de **Amazon RDS for PostgreSQL**; no hay SQL propietario, Flyway corre igual contra RDS.
- **Llaves RSA — el cambio más importante**: hoy se montan como archivos (`jwt-private.pem`/`jwt-public.pem`) vía un volumen de Docker. En AWS, usar **AWS Secrets Manager** (o Parameter Store con `SecureString`) para inyectar `JWT_PRIVATE_KEY_PEM`/`JWT_PUBLIC_KEY_PEM` como variables de entorno en el *task definition* de ECS — `RsaJwtService.resolverPem` ya prioriza el valor inline sobre el archivo, así que no se necesita tocar código, solo dejar de montar el volumen. Planificar rotación: el `kid` en el JWKS permite tener más de una llave activa durante una rotación si se extiende `obtenerJwks()` a una lista.
- **RabbitMQ**: cambiar a **Amazon MQ (RabbitMQ)** es solo variables de conexión (`RABBITMQ_HOST`, credenciales desde Secrets Manager); el broker lógico (`artagram.events`, colas `auth.subscription.updated`) no cambia.
- **Stateless por diseño**: `SessionCreationPolicy.STATELESS` + JWT sin estado en servidor significa que `artagram-auth` escala horizontalmente sin *sticky sessions* — ideal para un Auto Scaling Group de ECS/Fargate detrás de un ALB.
- **Secretos adicionales a externalizar**: `DB_PASSWORD`, credenciales SMTP si se activa `app.email.mode=smtp` — todos ya se leen de variables de entorno, listos para Secrets Manager.

## Atributos de calidad

### Seguridad

La verificación de identidad no depende de un secreto compartido entre servicios: `RsaJwtService` firma con una llave **privada** RSA 2048 que nunca sale de `artagram-auth`, y cualquier otro servicio valida la firma con la llave **pública** publicada en `/.well-known/jwks.json` (`JwksController`). Un token alterado o firmado con otra llave no pasa `Jwts.parser().verifyWith(publicKey)` en `RsaJwtService.validar(String)`.

- **Fuente**: un cliente (o un atacante) que envía una petición con un header `Authorization: Bearer <token>` cuya firma no corresponde a la llave privada de `artagram-auth` (token falsificado, expirado o manipulado).
- **Estímulo**: la petición llega a un endpoint protegido, por ejemplo `DELETE /api/auth/cuenta`, que internamente llama a `RsaJwtService.validar(...)`.
- **Artefacto**: `RsaJwtService.validar(String)` y las llaves RSA cargadas en `cargarOGenerarParRsa`.
- **Ambiente**: operación normal en producción, con la llave privada montada solo en `artagram-auth` (archivo o variable `JWT_PRIVATE_KEY_PEM`) y nunca compartida con otros servicios.
- **Respuesta**: `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)` lanza `JwtException`/`IllegalArgumentException`; el método la captura y retorna `Optional.empty()`, por lo que `AuthController.resolverUsuarioAutenticado` lanza `ApiException.unauthorized("NO_AUTENTICADO", ...)`.
- **Medida de respuesta**: el 100% de los tokens con firma inválida, formato corrupto o vencidos son rechazados en la validación local (sin red), sin exponer nunca la llave privada ni detalles internos de la causa del rechazo al cliente.

### Escalabilidad / Disponibilidad

El diseño es explícitamente *stateless*: `SecurityConfig` fija `SessionCreationPolicy.STATELESS` y la validez de un JWT se verifica solo con la llave pública distribuida vía JWKS, no contra una sesión ni una base de datos de tokens activos.

- **Fuente**: un pico de tráfico (por ejemplo, una campaña que dispara registros y logins simultáneos).
- **Estímulo**: aumenta el número de peticiones de login (`POST /api/auth/login`) y de validaciones de JWT que hacen otros microservicios (gateway, lienzo) contra tokens ya emitidos.
- **Artefacto**: `SecurityConfig` (política `STATELESS`) junto con `RsaJwtService`/`JwksController` (validación local vía JWKS).
- **Ambiente**: producción, con varias instancias de `artagram-auth` y de los servicios consumidores detrás de un balanceador de carga (ALB/ECS Fargate).
- **Respuesta**: como no hay sesión ni estado de autenticación guardado en el servidor, cualquier instancia de `artagram-auth` puede atender el login, y cualquier instancia de cualquier otro servicio puede validar el token localmente (RS256 + JWKS) sin volver a llamar a `artagram-auth` ni requerir *sticky sessions*.
- **Medida de respuesta**: se pueden agregar instancias adicionales de `artagram-auth` en caliente sin reconfigurar sesiones, y la validación de un JWT ya emitido no genera ninguna llamada de red adicional hacia `artagram-auth` (0 peticiones por validación, solo el fetch inicial de `/.well-known/jwks.json`).

### Modificabilidad

El envío de correos de verificación está detrás de la interfaz `EmailService`, con dos implementaciones (`LogEmailService`, `SmtpEmailService`) seleccionadas en tiempo de arranque por `@ConditionalOnProperty(prefix = "app.email", name = "mode", ...)`, sin que `AuthService` conozca cuál está activa.

- **Fuente**: el equipo de desarrollo/operaciones, que necesita pasar de correos simulados en log (ambiente de desarrollo) a envío real por SMTP en producción, o agregar un nuevo proveedor (p. ej. Amazon SES) en el futuro.
- **Estímulo**: se solicita cambiar el proveedor de envío de correo de verificación sin afectar el resto del servicio.
- **Artefacto**: la interfaz `EmailService` y sus implementaciones `LogEmailService`/`SmtpEmailService`, resueltas por la propiedad `app.email.mode` (`EmailProperties`).
- **Ambiente**: tiempo de configuración/despliegue, no de desarrollo activo.
- **Respuesta**: basta con cambiar la variable de entorno `EMAIL_MODE` (de `log` a `smtp`, o a un nuevo valor si se agrega un tercer proveedor); Spring resuelve automáticamente cuál `@Service` se instancia vía `@ConditionalOnProperty`, y `AuthService` sigue dependiendo únicamente de la interfaz `EmailService`.
- **Medida de respuesta**: el cambio de proveedor de correo requiere modificar una sola variable de configuración y cero líneas de código en `AuthService` o en cualquier otra clase que no sea la nueva implementación de `EmailService`.
