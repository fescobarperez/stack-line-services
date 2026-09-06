package com.erp_maya;

import io.micronaut.runtime.Micronaut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Application {

    public static void main(String[] args) {
        cargarDotEnv();
        // `dev` por defecto para que el ambiente no dependa de cómo se lance.
        // MICRONAUT_ENVIRONMENTS sigue ganando: en el servidor lo pone el
        // compose desde APP_ENV.
        Micronaut.build(args)
                .mainClass(Application.class)
                .defaultEnvironments("dev")
                .start();
    }

    /**
     * Carga un `.env` del directorio de trabajo a propiedades del sistema.
     *
     * La contraseña de la base no puede vivir en el repositorio, así que llega
     * por variable de entorno. Pero configurarla a mano en cada forma de
     * arrancar —terminal, IntelliJ, depurador— es justo lo que se olvida, y el
     * error que sale entonces («Could not resolve placeholder ${DB_PASSWORD}»)
     * no dice dónde ponerla.
     *
     * Se hace aquí y no en la tarea de Gradle porque IntelliJ ejecuta este
     * main directamente, sin pasar por Gradle.
     *
     * En el servidor no hay `.env` en el directorio de trabajo: las variables
     * las inyecta el compose y este método no hace nada.
     */
    private static void cargarDotEnv() {
        Path archivo = Path.of(".env");
        if (!Files.isReadable(archivo)) return;

        try {
            List<String> lineas = Files.readAllLines(archivo);
            for (String cruda : lineas) {
                String linea = cruda.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;

                int igual = linea.indexOf('=');
                if (igual <= 0) continue;

                String clave = linea.substring(0, igual).trim();
                String valor = linea.substring(igual + 1).trim();

                // El entorno real y lo que ya venga por -D mandan sobre el
                // archivo: permite sobreescribir sin editarlo.
                if (System.getenv(clave) == null && System.getProperty(clave) == null) {
                    System.setProperty(clave, valor);
                }
            }
        } catch (IOException e) {
            // Un .env ilegible no debe impedir arrancar: puede que las
            // variables ya vengan del entorno y el archivo sobre.
            System.err.println("Aviso: no se pudo leer .env — " + e.getMessage());
        }
    }
}
