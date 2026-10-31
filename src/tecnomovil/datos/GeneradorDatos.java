package tecnomovil.datos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tecnomovil.datos.CatalogoRed.Ruta;
import tecnomovil.modelo.Accion;
import tecnomovil.modelo.RegistroTransporte;

/**
 * Genera un día simulado de registros.
 *
 * <p>Es una función pura: con los mismos parámetros siempre produce los mismos datos. Cada viaje
 * crea su propio generador pseudoaleatorio a partir de una semilla derivada, así ningún hilo
 * comparte estado y la generación se puede paralelizar sin alterar el resultado.
 */
public final class GeneradorDatos {

    private static final List<Integer> HORAS_MANANA = repetirHoras(5, 2, 6, 6, 7, 9, 8, 6, 9, 2);
    private static final List<Integer> HORAS_MEDIODIA = repetirHoras(11, 3, 12, 5, 13, 4, 14, 2);
    private static final List<Integer> HORAS_TARDE = repetirHoras(16, 3, 17, 8, 18, 9, 19, 4, 20, 1);

    /** Jornadas de cada viaje según cuántos viajes hace el usuario en el día. */
    private static final List<List<List<Integer>>> JORNADAS = List.of(
            List.of(HORAS_MANANA),
            List.of(HORAS_MANANA, HORAS_TARDE),
            List.of(HORAS_MANANA, HORAS_MEDIODIA, HORAS_TARDE));

    /** Viajes diarios posibles: la mayoría de usuarios hace ida y regreso. */
    private static final List<Integer> VIAJES_POR_USUARIO = List.of(1, 2, 2, 2, 2, 3);

    private GeneradorDatos() {
    }

    public static List<RegistroTransporte> generar(int usuarios, long semilla, LocalDate fecha) {
        return IntStream.range(0, usuarios)
                .parallel()
                .boxed()
                .flatMap(usuario -> registrosDeUsuario(usuario, semilla, fecha))
                .sorted(Comparator.comparing(RegistroTransporte::timestamp))
                .toList();
    }

    private static Stream<RegistroTransporte> registrosDeUsuario(int usuario, long semilla, LocalDate fecha) {
        SplittableRandom azar = new SplittableRandom(semillaDerivada(semilla, usuario, 0));
        Ruta ruta = elegir(CatalogoRed.RUTAS_PONDERADAS, azar);
        int viajes = elegir(VIAJES_POR_USUARIO, azar);
        int casa = azar.nextInt(ruta.estaciones().size());
        int trabajo = paradaDistinta(casa, ruta, azar);
        int diligencia = paradaDistinta(trabajo, ruta, azar);
        List<Integer> paradas = recorrido(viajes, casa, trabajo, diligencia);
        List<List<Integer>> jornadas = JORNADAS.get(viajes - 1);

        return IntStream.range(0, viajes)
                .boxed()
                .flatMap(viaje -> registrosDeViaje(
                        "U" + String.format("%07d", usuario),
                        ruta,
                        paradas.get(viaje),
                        paradas.get(viaje + 1),
                        jornadas.get(viaje),
                        fecha,
                        new SplittableRandom(semillaDerivada(semilla, usuario, viaje + 1))));
    }

    private static List<Integer> recorrido(int viajes, int casa, int trabajo, int diligencia) {
        return viajes == 1 ? List.of(casa, trabajo)
                : viajes == 2 ? List.of(casa, trabajo, casa)
                : diligencia == casa ? List.of(casa, trabajo, casa, trabajo)
                : List.of(casa, trabajo, diligencia, casa);
    }

    private static Stream<RegistroTransporte> registrosDeViaje(
            String idUsuario,
            Ruta ruta,
            int origen,
            int destino,
            List<Integer> horas,
            LocalDate fecha,
            SplittableRandom azar) {

        LocalDateTime entrada = fecha.atTime(elegir(horas, azar), azar.nextInt(60), azar.nextInt(60));
        int paradasRecorridas = Math.abs(destino - origen);
        long segundos = paradasRecorridas * (ruta.minutosPorParada() * 60L + azar.nextInt(-45, 91));

        return Stream.of(
                new RegistroTransporte(idUsuario, ruta.codigo(), ruta.estaciones().get(origen), Accion.ENTRADA, entrada),
                new RegistroTransporte(idUsuario, ruta.codigo(), ruta.estaciones().get(destino), Accion.SALIDA,
                        entrada.plusSeconds(segundos)));
    }

    private static int paradaDistinta(int parada, Ruta ruta, SplittableRandom azar) {
        int total = ruta.estaciones().size();
        return (parada + 1 + azar.nextInt(total - 1)) % total;
    }

    private static <T> T elegir(List<T> opciones, SplittableRandom azar) {
        return opciones.get(azar.nextInt(opciones.size()));
    }

    private static long semillaDerivada(long semilla, int usuario, int viaje) {
        return semilla + usuario * 31L + (long) viaje * 1_000_003L * (usuario + 1L);
    }

    /** Recibe pares hora-peso y devuelve cada hora repetida tantas veces como indique su peso. */
    private static List<Integer> repetirHoras(int... horasYPesos) {
        return IntStream.iterate(0, i -> i < horasYPesos.length, i -> i + 2)
                .boxed()
                .flatMap(i -> IntStream.range(0, horasYPesos[i + 1]).mapToObj(n -> horasYPesos[i]))
                .toList();
    }
}
