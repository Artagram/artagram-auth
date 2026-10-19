package com.artagram.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class NombreSugeridoGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] SUFIJOS = {"_arte", "_studio", "_creativo", "_oficial", "_art"};

    public List<String> sugerir(String base, Predicate<String> estaDisponible, int cantidad) {
        List<String> sugerencias = new ArrayList<>();
        for (String sufijo : SUFIJOS) {
            if (sugerencias.size() >= cantidad) {
                break;
            }
            String candidato = truncar(base, 30 - sufijo.length()) + sufijo;
            if (estaDisponible.test(candidato)) {
                sugerencias.add(candidato);
            }
        }
        int intentos = 0;
        while (sugerencias.size() < cantidad && intentos < 20) {
            int numero = 10 + RANDOM.nextInt(990);
            String candidato = truncar(base, 26) + "_" + numero;
            if (estaDisponible.test(candidato) && !sugerencias.contains(candidato)) {
                sugerencias.add(candidato);
            }
            intentos++;
        }
        return sugerencias;
    }

    private String truncar(String base, int maxLen) {
        return base.length() <= maxLen ? base : base.substring(0, maxLen);
    }
}
