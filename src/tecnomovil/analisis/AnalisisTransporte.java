package tecnomovil.analisis;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tecnomovil.modelo.Conteo;
import tecnomovil.modelo.EstadoRuta;
import tecnomovil.modelo.NivelOcupacion;
import tecnomovil.modelo.RegistroTransporte;
import tecnomovil.modelo.ResumenDiario;
import tecnomovil.modelo.TiempoTramo;

/**
 * Módulo funcional de procesamiento y agregación de datos diarios de TecnoMovil Data.
 *
 * <p>Cada método público es una función pura: su resultado depende solo de sus parámetros, no
 * modifica la lista recibida, no toca estado compartido y devuelve colecciones inmutables.
 * El mismo código funciona en secuencial o en paralelo porque ninguna lambda comparte estado.
 */
public record AnalisisTransporte(boolean paralelo) {

    private static final Predicate<RegistroTransporte> TODOS = registro -> true;
    private static final Predicate<RegistroTransporte> ES_ENTRADA = RegistroTransporte::esEntrada;

    /** Desplazamiento de un usuario entre dos estaciones, medido en segundos. */
    private record Desplazamiento(Tramo tramo, long segundos) {
    }

    /** Par origen-destino que identifica un tramo. */
    private record Tramo(String origen, String destino) {
    }

    public static AnalisisTransporte secuencial() {
        return new AnalisisTransporte(false);
    }

    public static AnalisisTransporte enParalelo() {
        return new AnalisisTransporte(true);
    }

    private <T> Stream<T> flujo(Collection<T> datos) {
        return paralelo ? datos.parallelStream() : datos.stream();
    }

    // ---------------------------------------------------------------- a) Afluencia por estación

    /** Número de ingresos registrados en cada estación durante el día. */
    public Map<String, Long> afluenciaPorEstacion(List<RegistroTransporte> registros) {
        return contarPor(registros, ES_ENTRADA, RegistroTransporte::estacion);
    }

    /** Número de usuarios distintos que ingresaron a cada estación (un usuario cuenta una vez). */
    public Map<String, Long> usuariosUnicosPorEstacion(List<RegistroTransporte> registros) {
        return flujo(registros)
                .filter(ES_ENTRADA)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                RegistroTransporte::estacion,
                                TreeMap::new,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(RegistroTransporte::idUsuario, Collectors.toSet()),
                                        usuarios -> (long) usuarios.size())),
                        Collections::unmodifiableMap));
    }

    // ---------------------------------------------------------------- b) Horas pico

    /** Número de registros (entradas y salidas) por cada hora del día. */
    public Map<Integer, Long> flujoPorHora(List<RegistroTransporte> registros) {
        return contarPor(registros, TODOS, RegistroTransporte::hora);
    }

    /** Las {@code cantidad} horas de mayor flujo, de mayor a menor. */
    public List<Conteo<Integer>> horasPico(List<RegistroTransporte> registros, int cantidad) {
        return rankear(flujoPorHora(registros), cantidad);
    }

    // ---------------------------------------------------------------- c) Rutas más utilizadas

    /** Rutas ordenadas de mayor a menor volumen de uso. Cada ingreso cuenta como un uso de la ruta. */
    public List<Conteo<String>> rutasMasUtilizadas(List<RegistroTransporte> registros) {
        return rankear(contarPor(registros, ES_ENTRADA, RegistroTransporte::ruta), Integer.MAX_VALUE);
    }

    // ---------------------------------------------------------------- d) Patrones de viaje

    /** Para cada usuario, las estaciones visitadas en el orden en que aparecen en los registros. */
    public Map<String, List<String>> patronesDeViaje(List<RegistroTransporte> registros) {
        return flujo(registros)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                RegistroTransporte::idUsuario,
                                TreeMap::new,
                                Collectors.mapping(RegistroTransporte::estacion, Collectors.toUnmodifiableList())),
                        Collections::unmodifiableMap));
    }

    // ---------------------------------------------------------------- e) Tiempo entre estaciones

    /** Tiempo promedio de cada tramo origen-destino, del más demorado al más rápido. */
    public List<TiempoTramo> tiemposPorTramo(List<RegistroTransporte> registros) {
        Map<Tramo, LongSummaryStatistics> estadisticas = desplazamientos(registros)
                .collect(Collectors.groupingBy(
                        Desplazamiento::tramo,
                        Collectors.summarizingLong(Desplazamiento::segundos)));

        return estadisticas.entrySet().stream()
                .map(tramo -> new TiempoTramo(
                        tramo.getKey().origen(),
                        tramo.getKey().destino(),
                        tramo.getValue().getCount(),
                        tramo.getValue().getAverage() / 60.0))
                .sorted(Comparator.comparingDouble(TiempoTramo::minutosPromedio).reversed()
                        .thenComparing(TiempoTramo::origen)
                        .thenComparing(TiempoTramo::destino))
                .toList();
    }

    /** Tiempo promedio, en minutos, de todos los desplazamientos del día. */
    public double minutosPromedioGeneral(List<RegistroTransporte> registros) {
        return desplazamientos(registros)
                .mapToLong(Desplazamiento::segundos)
                .average()
                .orElse(0.0) / 60.0;
    }

    private Stream<Desplazamiento> desplazamientos(List<RegistroTransporte> registros) {
        Map<String, List<RegistroTransporte>> porUsuario =
                flujo(registros).collect(Collectors.groupingBy(RegistroTransporte::idUsuario));
        return flujo(porUsuario.values()).flatMap(AnalisisTransporte::desplazamientosDeUsuario);
    }

    /**
     * Recorre los registros consecutivos de un usuario. Solo una entrada seguida de una salida en otra
     * estación es un desplazamiento: una salida seguida de una entrada es tiempo fuera del sistema
     * (por ejemplo, la jornada de trabajo) y falsearía el promedio.
     */
    private static Stream<Desplazamiento> desplazamientosDeUsuario(List<RegistroTransporte> registrosUsuario) {
        List<RegistroTransporte> ordenados = registrosUsuario.stream()
                .sorted(Comparator.comparing(RegistroTransporte::timestamp))
                .toList();

        return IntStream.range(0, ordenados.size() - 1)
                .filter(i -> esDesplazamiento(ordenados.get(i), ordenados.get(i + 1)))
                .mapToObj(i -> crearDesplazamiento(ordenados.get(i), ordenados.get(i + 1)));
    }

    private static boolean esDesplazamiento(RegistroTransporte actual, RegistroTransporte siguiente) {
        return actual.esEntrada()
                && !siguiente.esEntrada()
                && !actual.estacion().equals(siguiente.estacion());
    }

    private static Desplazamiento crearDesplazamiento(RegistroTransporte origen, RegistroTransporte destino) {
        return new Desplazamiento(
                new Tramo(origen.estacion(), destino.estacion()),
                Duration.between(origen.timestamp(), destino.timestamp()).toSeconds());
    }

    // ---------------------------------------------------------------- f) Sobrecarga en rutas

    /**
     * Evalúa cada ruta en su hora de mayor demanda y la marca como crítica si supera el umbral.
     *
     * @param capacidadDe función que entrega la capacidad horaria simulada de una ruta
     * @param umbral      ocupación a partir de la cual la ruta es crítica (0.90 equivale a 90 %)
     */
    public List<EstadoRuta> detectarSobrecarga(
            List<RegistroTransporte> registros,
            ToIntFunction<String> capacidadDe,
            double umbral) {

        Map<String, Map<Integer, Long>> ingresosPorRutaYHora = flujo(registros)
                .filter(ES_ENTRADA)
                .collect(Collectors.groupingBy(
                        RegistroTransporte::ruta,
                        Collectors.groupingBy(RegistroTransporte::hora, Collectors.counting())));

        DoubleFunction<NivelOcupacion> clasificar = clasificador(umbral);

        return ingresosPorRutaYHora.entrySet().stream()
                .map(ruta -> evaluarRuta(
                        ruta.getKey(),
                        ruta.getValue(),
                        capacidadDe.applyAsInt(ruta.getKey()),
                        clasificar))
                .sorted(Comparator.comparingDouble(EstadoRuta::ocupacion).reversed()
                        .thenComparing(EstadoRuta::ruta))
                .toList();
    }

    /** Función de orden superior: recibe un umbral y devuelve la función que clasifica una ocupación. */
    static DoubleFunction<NivelOcupacion> clasificador(double umbral) {
        return ocupacion -> ocupacion > umbral ? NivelOcupacion.CRITICA : NivelOcupacion.NORMAL;
    }

    private static EstadoRuta evaluarRuta(
            String ruta,
            Map<Integer, Long> ingresosPorHora,
            int capacidadHoraria,
            DoubleFunction<NivelOcupacion> clasificar) {

        Map.Entry<Integer, Long> horaMaxima = ingresosPorHora.entrySet().stream()
                .max(Map.Entry.<Integer, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .orElseThrow();

        double ocupacion = (double) horaMaxima.getValue() / capacidadHoraria;

        return new EstadoRuta(
                ruta,
                horaMaxima.getKey(),
                horaMaxima.getValue(),
                capacidadHoraria,
                ocupacion,
                clasificar.apply(ocupacion));
    }

    // ---------------------------------------------------------------- Resumen del día

    /** Ejecuta las seis tareas y devuelve sus respuestas en un solo valor inmutable. */
    public ResumenDiario resumir(
            List<RegistroTransporte> registros,
            int cantidadHorasPico,
            ToIntFunction<String> capacidadDe,
            double umbral) {

        return new ResumenDiario(
                afluenciaPorEstacion(registros),
                usuariosUnicosPorEstacion(registros),
                flujoPorHora(registros),
                horasPico(registros, cantidadHorasPico),
                rutasMasUtilizadas(registros),
                patronesDeViaje(registros),
                tiemposPorTramo(registros),
                minutosPromedioGeneral(registros),
                detectarSobrecarga(registros, capacidadDe, umbral));
    }

    // ---------------------------------------------------------------- Funciones reutilizables

    /** Función de orden superior: el criterio de filtrado y la clave de agrupación llegan como parámetros. */
    private <K extends Comparable<K>> Map<K, Long> contarPor(
            List<RegistroTransporte> registros,
            Predicate<RegistroTransporte> filtro,
            Function<RegistroTransporte, K> clave) {

        return flujo(registros)
                .filter(filtro)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(clave, TreeMap::new, Collectors.counting()),
                        Collections::unmodifiableMap));
    }

    /** Convierte un mapa de totales en una lista ordenada de mayor a menor (desempate por clave). */
    private static <K extends Comparable<K>> List<Conteo<K>> rankear(Map<K, Long> totales, int cantidad) {
        Comparator<Conteo<K>> mayorTotalPrimero =
                Comparator.comparingLong((Conteo<K> conteo) -> conteo.total()).reversed()
                        .thenComparing(Conteo::clave);

        return totales.entrySet().stream()
                .map(total -> new Conteo<>(total.getKey(), total.getValue()))
                .sorted(mayorTotalPrimero)
                .limit(cantidad)
                .toList();
    }
}
