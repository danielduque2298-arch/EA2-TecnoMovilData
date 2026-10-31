package tecnomovil.reporte;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tecnomovil.modelo.EstadoRuta;
import tecnomovil.modelo.NivelOcupacion;
import tecnomovil.modelo.ResumenDiario;
import tecnomovil.modelo.TiempoTramo;

/** Convierte resultados en texto. Son funciones puras: devuelven cadenas y no imprimen nada. */
public final class Reporte {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final String LINEA = "=".repeat(86);
    private static final int ANCHO_BARRA = 40;
    private static final int TRAMOS_A_MOSTRAR = 5;
    private static final int USUARIOS_DE_EJEMPLO = 5;

    private Reporte() {
    }

    public static String titulo(String texto) {
        return String.join("\n", LINEA, "  " + texto, LINEA);
    }

    public static String encabezado(long registros, long usuarios) {
        return titulo("TECNOMOVIL DATA - Procesamiento funcional de un día de transporte")
                + "\n" + String.format(COLOMBIA, "Registros procesados: %,d | Usuarios simulados: %,d", registros, usuarios);
    }

    public static String afluencia(ResumenDiario resumen) {
        String filas = resumen.afluenciaPorEstacion().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Collections.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(estacion -> String.format(COLOMBIA, "  %-20s %,12d %,16d",
                        estacion.getKey(),
                        estacion.getValue(),
                        resumen.usuariosUnicosPorEstacion().get(estacion.getKey())))
                .collect(Collectors.joining("\n"));

        return seccion("a) Afluencia por estación",
                String.format("  %-20s %12s %16s", "Estación", "Ingresos", "Usuarios únicos"), filas);
    }

    public static String horasPico(ResumenDiario resumen) {
        long maximo = resumen.flujoPorHora().values().stream().mapToLong(Long::longValue).max().orElse(1);

        String histograma = resumen.flujoPorHora().entrySet().stream()
                .map(hora -> String.format(COLOMBIA, "  %02d:00 %,10d %s",
                        hora.getKey(),
                        hora.getValue(),
                        "█".repeat((int) (hora.getValue() * ANCHO_BARRA / maximo))))
                .collect(Collectors.joining("\n"));

        String pico = resumen.horasPico().stream()
                .map(hora -> String.format(COLOMBIA, "%02d:00 (%,d)", hora.clave(), hora.total()))
                .collect(Collectors.joining(", "));

        return seccion("b) Horas pico (registros por hora)", histograma, "  Horas pico: " + pico);
    }

    public static String rutas(ResumenDiario resumen) {
        long total = resumen.rutasMasUtilizadas().stream().mapToLong(ruta -> ruta.total()).sum();

        String filas = resumen.rutasMasUtilizadas().stream()
                .map(ruta -> String.format(COLOMBIA, "  %-24s %,12d %8.1f %%",
                        ruta.clave(), ruta.total(), 100.0 * ruta.total() / Math.max(total, 1)))
                .collect(Collectors.joining("\n"));

        return seccion("c) Rutas más utilizadas",
                String.format("  %-24s %12s %10s", "Ruta", "Ingresos", "Part."), filas);
    }

    public static String patrones(ResumenDiario resumen) {
        String ejemplos = resumen.patronesDeViaje().entrySet().stream()
                .limit(USUARIOS_DE_EJEMPLO)
                .map(usuario -> "  " + usuario.getKey() + ": " + String.join(" > ", usuario.getValue()))
                .collect(Collectors.joining("\n"));

        return seccion("d) Patrones de viaje por usuario",
                String.format(COLOMBIA, "  Usuarios con patrón calculado: %,d. Primeros %d:",
                        resumen.patronesDeViaje().size(), USUARIOS_DE_EJEMPLO),
                ejemplos);
    }

    public static String tiempos(ResumenDiario resumen) {
        List<TiempoTramo> tramos = resumen.tiemposPorTramo();

        String filas = tramos.stream()
                .limit(TRAMOS_A_MOSTRAR)
                .map(tramo -> String.format(COLOMBIA, "  %-40s %,10d %9.1f min",
                        tramo.origen() + " > " + tramo.destino(), tramo.viajes(), tramo.minutosPromedio()))
                .collect(Collectors.joining("\n"));

        return seccion("e) Tiempo promedio entre estaciones",
                String.format(COLOMBIA, "  Promedio general: %.1f min | Tramos distintos: %,d. Los %d más demorados:",
                        resumen.minutosPromedioGeneral(), tramos.size(), TRAMOS_A_MOSTRAR),
                String.format("  %-40s %10s %13s", "Tramo", "Viajes", "Promedio"),
                filas);
    }

    public static String sobrecarga(ResumenDiario resumen, double umbral) {
        String filas = resumen.estadoDeRutas().stream()
                .map(Reporte::filaEstado)
                .collect(Collectors.joining("\n"));

        return seccion(String.format(COLOMBIA, "f) Sobrecarga en rutas (crítica si supera %.0f %% de ocupación)", umbral * 100),
                String.format("  %-24s %6s %10s %10s %7s  %s", "Ruta", "Hora", "Pasajeros", "Capacidad", "Ocup.", "Estado"),
                filas);
    }

    private static String filaEstado(EstadoRuta ruta) {
        return String.format(COLOMBIA, "  %-24s %02d:00 %,10d %,10d %6.1f%%  %s",
                ruta.ruta(), ruta.horaMaxima(), ruta.pasajerosHoraMaxima(), ruta.capacidadHoraria(),
                ruta.ocupacion() * 100,
                ruta.nivel() == NivelOcupacion.CRITICA ? "CRÍTICA" : "Normal");
    }

    public static String rendimiento(
            long registros, long milisSecuencial, long milisParalelo, int nucleos, boolean mismosResultados) {
        return seccion("Secuencial frente a parallelStream() (mejor de 3 corridas)",
                String.format(COLOMBIA, "  Registros procesados: %,d", registros),
                String.format(COLOMBIA, "  Secuencial: %,d ms | Paralelo: %,d ms | Núcleos: %d | Aceleración: %.2fx",
                        milisSecuencial, milisParalelo, nucleos, (double) milisSecuencial / Math.max(milisParalelo, 1)),
                "  ¿Ambos modos dieron exactamente el mismo resultado? " + (mismosResultados ? "SÍ" : "NO"));
    }

    public static String partes(String... textos) {
        return Stream.of(textos).collect(Collectors.joining("\n\n"));
    }

    private static String seccion(String titulo, String... lineas) {
        return Stream.concat(Stream.of(titulo, "-".repeat(86)), Stream.of(lineas))
                .collect(Collectors.joining("\n"));
    }
}
