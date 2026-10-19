-- V1__auth.sql
-- Esquema del microservicio de identidad (artagram-auth / db_auth)

CREATE TABLE IF NOT EXISTS usuarios (
    id              UUID PRIMARY KEY,
    correo          VARCHAR(255) NOT NULL UNIQUE,
    nombre_artista  VARCHAR(60)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    verificado      BOOLEAN      NOT NULL DEFAULT FALSE,
    rol_global      VARCHAR(30)  NOT NULL DEFAULT 'USUARIO',
    premium         BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_usuarios_correo_lower ON usuarios (LOWER(correo));
CREATE INDEX IF NOT EXISTS idx_usuarios_nombre_lower ON usuarios (LOWER(nombre_artista));

CREATE TABLE IF NOT EXISTS tokens_verificacion (
    id          UUID PRIMARY KEY,
    usuario_id  UUID         NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token       VARCHAR(128) NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    usado_en    TIMESTAMPTZ,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tokens_verificacion_usuario ON tokens_verificacion(usuario_id);

-- Los 12 usuarios de demostración con UUIDs fijos (contraseña: clavesegura123)
INSERT INTO usuarios (id, correo, nombre_artista, password_hash, verificado, rol_global, premium, creado_en, actualizado_en)
VALUES
  ('a1b2c3d4-0000-4000-8000-000000000001', 'lucia.mora@correo.com',       'lucia_acuarela',   '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000002', 'sam.rivera@correo.com',       'sam_pixel',        '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', TRUE,  now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000003', 'noa.delgado@correo.com',      'noa_tinta',        '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000004', 'ivan.rueda@correo.com',       'ivan_urbano',      '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000005', 'maria.paz@correo.com',        'maria_paz_art',    '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000006', 'diego.leon@correo.com',       'diego_grabado',    '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0000-4000-8000-000000000007', 'prueba.artagram@correo.com',  'pincel_artagram',  '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'ADMIN',   FALSE, now(), now()),
  ('a1b2c3d4-0001-4000-8000-000000000001', 'camila.oleo@correo.com',      'camila_oleo',      '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0002-4000-8000-000000000002', 'mateo.3d@correo.com',         'mateo_3d',         '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0003-4000-8000-000000000003', 'valeria.botanica@correo.com', 'valeria_botanica', '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0004-4000-8000-000000000004', 'andres.concept@correo.com',   'andres_concept',   '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now()),
  ('a1b2c3d4-0005-4000-8000-000000000005', 'elena.collage@correo.com',    'elena_collage',    '$2a$10$S9rlsaNry9vtuKjJlDWXY.Mi0ge1JFY9i.m6LwbYFRoJGxoi9VDie', TRUE, 'USUARIO', FALSE, now(), now())
ON CONFLICT (correo) DO NOTHING;
