package com.artagram.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void enviarVerificacion(String correoDestino, String nombreArtista, String enlaceVerificacion) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(properties.getFrom());
        mensaje.setTo(correoDestino);
        mensaje.setSubject("Confirma tu cuenta en Artagram");
        mensaje.setText("Hola " + nombreArtista + ",\n\nConfirma tu cuenta aqui: " + enlaceVerificacion
                + "\n\nEste enlace vence en 24 horas.");
        try {
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de verificacion a {}", correoDestino, e);
        }
    }
}
