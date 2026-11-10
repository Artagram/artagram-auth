-- V2__login_google.sql
-- El ingreso pasa a ser exclusivamente con Google: se eliminan las contrasenas
-- y la verificacion por correo. Google garantiza el correo (email_verified).

ALTER TABLE usuarios ADD COLUMN google_sub VARCHAR(255) UNIQUE;
ALTER TABLE usuarios DROP COLUMN password_hash;

DROP TABLE tokens_verificacion;
