package tecnomovil.verificacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tecnomovil.analisis.AnalisisTransporte;
import tecnomovil.datos.CatalogoRed;
import tecnomovil.datos.GeneradorDatos;
import tecnomovil.modelo.Accion;
import tecnomovil.modelo.Conteo;
import tecnomovil.modelo.NivelOcupacion;
import tecnomovil.modelo.RegistroTransporte;

/**
 * Comprueba los resultados contra un conjunto pequeño calculado a mano, además de la pureza,
 * la inmutabilidad y la equivalencia entre el modo secuencial y el paralelo. No usa frameworks.
 */
public final class VerificacionFunciones {

    private static final String T1 = "T1";
    private static final String T2 = "T2";
    private static final double TOLERANCIA = 1e-9;

    private record Comprobacion(String nombre, boolean aprobada) {
    }

    private static final List<RegistroTransporte> DATOS = List.of(
            registro("U1", T1, "Portal Norte", Accion.ENTRADA, 7, 0),
            registro("U2", T1, "Portal Norte", Accion.ENTRADA, 7, 10),
            registro("U1", T1, "Centro", Accion.SALIDA, 7, 20),
            registro("U2", T1, "Universidad", Accion.SALIDA, 7, 25),
            registro("U3", T2, "Mercado", Accion.ENTRADA, 7, 40),
            registro("U3", T2, "Centro", Accion.SALIDA, 7, 50),
            registro("U1", T1, "Centro", Accion.ENTRADA, 17, 0),
            registro("U1", T1, "Portal Norte", Accion.SALIDA, 17, 30));

    private static final ToIntFunction<String> CAPACIDAD = ruta -> ruta.equals(T1) ? 2 : 10;

    private VerificacionFunciones() {
    }

    public static void main(String[] args) {
        List<Comprobacion> comprobaciones = comprobaciones();

        System.out.println(comprobaciones.stream()
                .map(c -> (c.aprobada() ? "[ OK ] " : "[FALLA] ") + c.nombre())
                .collect(Collectors.joining("\n")));

        long aprobadas = comprobaciones.stream().filter(Comprobacion::aprobada).count();
        System.out.printf("%nComprobaciones aprobadas: %d de %d%n", aprobadas, comprobaciones.size());

        if (aprobadas != comprobaciones.size()) {
            System.exit(1);
        }
    }

    private static List<Comprobacion> comprobaciones() {
        AnalisisTransporte analisis = AnalisisTransporte.secuencial();
        List<RegistroTransporte> grande = GeneradorDatos.generar(20_000, 7L, LocalDate.of(2026, 10, 30));
        ToIntFunction<String> capacidadGrande = CatalogoRed.capacidadPara(20_000);

        return Stream.of(
                comprobar("a) Afluencia: Portal Norte 2, Centro 1, Mercado 1", () ->
                        analisis.afluenciaPorEstacion(DATOS)
                                .equals(Map.of("Portal Norte", 2L, "Centro", 1L, "Mercado", 1L))),
                comprobar("a) Un usuario que entra dos veces cuenta una sola vez como único", () ->
                        analisis.usuariosUnicosPorEstacion(duplicarDatos())
                                .equals(Map.of("Portal Norte", 2L, "Centro", 1L, "Mercado", 1L))),
                comprobar("b) Flujo: 6 registros a las 7:00 y 2 a las 17:00", () ->
                        analisis.flujoPorHora(DATOS).equals(Map.of(7, 6L, 17, 2L))),
                comprobar("b) La hora pico es las 7:00", () ->
                        analisis.horasPico(DATOS, 1).equals(List.of(new Conteo<>(7, 6L)))),
                comprobar("c) Rutas: T1 con 3 ingresos antes que T2 con 1", () ->
                        analisis.rutasMasUtilizadas(DATOS)
                                .equals(List.of(new Conteo<>(T1, 3L), new Conteo<>(T2, 1L)))),
                comprobar("d) Patrón de U1 en el orden de los registros", () ->
                        analisis.patronesDeViaje(DATOS).get("U1")
                                .equals(List.of("Portal Norte", "Centro", "Centro", "Portal Norte"))),
                comprobar("e) Promedio general: (20 + 15 + 10 + 30) / 4 = 18,75 min", () ->
                        Math.abs(analisis.minutosPromedioGeneral(DATOS) - 18.75) < TOLERANCIA),
                comprobar("e) Se excluye la estadía de U1 entre las 7:20 y las 17:00", () ->
                        analisis.tiemposPorTramo(DATOS).size() == 4
                                && analisis.tiemposPorTramo(DATOS).stream().allMatch(t -> t.minutosPromedio() <= 30)),
                comprobar("f) T1 (2 de 2 = 100 %) es crítica", () ->
                        analisis.detectarSobrecarga(DATOS, CAPACIDAD, 0.90).get(0).nivel() == NivelOcupacion.CRITICA),
                comprobar("f) T2 (1 de 10 = 10 %) es normal", () ->
                        analisis.detectarSobrecarga(DATOS, CAPACIDAD, 0.90).get(1).nivel() == NivelOcupacion.NORMAL),
                comprobar("Pureza: dos llamadas con la misma entrada dan el mismo resultado", () ->
                        analisis.resumir(DATOS, 3, CAPACIDAD, 0.90).equals(analisis.resumir(DATOS, 3, CAPACIDAD, 0.90))),
                comprobar("Inmutabilidad: la lista original queda igual tras procesarla", () -> {
                    List<RegistroTransporte> copia = List.copyOf(DATOS);
                    analisis.resumir(DATOS, 3, CAPACIDAD, 0.90);
                    return DATOS.equals(copia);
                }),
                comprobar("Inmutabilidad: una lista mutable de entrada tampoco se altera", () -> {
                    List<RegistroTransporte> mutable = new ArrayList<>(DATOS);
                    analisis.resumir(mutable, 3, CAPACIDAD, 0.90);
                    return mutable.equals(DATOS);
                }),
                comprobar("Inmutabilidad: el mapa de afluencia rechaza modificaciones", () ->
                        rechazaCambios(() -> analisis.afluenciaPorEstacion(DATOS).put("X", 1L))),
                comprobar("Inmutabilidad: el mapa de patrones y sus listas rechazan modificaciones", () ->
                        rechazaCambios(() -> analisis.patronesDeViaje(DATOS).clear())
                                && rechazaCambios(() -> analisis.patronesDeViaje(DATOS).get("U1").add("X"))),
                comprobar("Inmutabilidad: el ranking de rutas rechaza modificaciones", () ->
                        rechazaCambios(() -> analisis.rutasMasUtilizadas(DATOS).clear())),
                comprobar("Lista vacía: no falla y devuelve resultados vacíos", () ->
                        analisis.resumir(List.of(), 3, CAPACIDAD, 0.90).afluenciaPorEstacion().isEmpty()
                                && analisis.minutosPromedioGeneral(List.of()) == 0.0),
                comprobar("Generador: con la misma semilla produce los mismos datos", () ->
                        grande.equals(GeneradorDatos.generar(20_000, 7L, LocalDate.of(2026, 10, 30)))),
                comprobar("Paralelismo: parallelStream() da el mismo resultado que stream()", () ->
                        AnalisisTransporte.secuencial().resumir(grande, 3, capacidadGrande, 0.90)
                                .equals(AnalisisTransporte.enParalelo().resumir(grande, 3, capacidadGrande, 0.90))))
                .toList();
    }

    private static List<RegistroTransporte> duplicarDatos() {
        return Stream.concat(DATOS.stream(), DATOS.stream()).toList();
    }

    private static Comprobacion comprobar(String nombre, BooleanSupplier condicion) {
        return new Comprobacion(nombre, condicion.getAsBoolean());
    }

    private static boolean rechazaCambios(Runnable modificacion) {
        try {
            modificacion.run();
            return false;
        } catch (UnsupportedOperationException esperada) {
            return true;
        }
    }

    private static RegistroTransporte registro(
            String usuario, String ruta, String estacion, Accion accion, int hora, int minuto) {
        return new RegistroTransporte(usuario, ruta, estacion, accion, LocalDateTime.of(2026, 10, 30, hora, minuto));
    }
}
