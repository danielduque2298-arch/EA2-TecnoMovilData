package tecnomovil.datos;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Red simulada de TecnoValle: rutas, estaciones, popularidad y capacidad horaria. */
public final class CatalogoRed {

    /** Pasajeros diarios que menciona el caso de estudio; la capacidad base está dimensionada para ellos. */
    public static final int USUARIOS_DE_REFERENCIA = 450_000;

    private static final int CAPACIDAD_RUTA_DESCONOCIDA = 10_000;

    /** Ruta de la red. {@code peso} indica qué tan popular es frente a las demás. */
    public record Ruta(String codigo, List<String> estaciones, int peso, int capacidadHoraria, int minutosPorParada) {
    }

    public static final List<Ruta> RUTAS = List.of(
            new Ruta("T1 Norte-Sur",
                    List.of("Portal Norte", "Alameda", "Universidad", "Centro", "Hospital", "Parque Sur", "Portal Sur"),
                    34, 50_000, 5),
            new Ruta("T2 Oriente-Occidente",
                    List.of("Portal Oriente", "La Ceiba", "Centro", "Mercado", "Estadio", "Portal Occidente"),
                    26, 44_000, 6),
            new Ruta("C1 Circular Centro",
                    List.of("Centro", "Alcaldía", "Terminal", "Universidad"),
                    18, 36_000, 4),
            new Ruta("A1 Alimentadora Norte",
                    List.of("Portal Norte", "Los Pinos", "Villa Nueva", "El Mirador"),
                    12, 26_000, 7),
            new Ruta("A2 Alimentadora Sur",
                    List.of("Portal Sur", "El Prado", "San José", "La Esperanza"),
                    10, 24_000, 8));

    /** Rutas repetidas según su peso: elegir una posición al azar respeta la popularidad. */
    static final List<Ruta> RUTAS_PONDERADAS = RUTAS.stream()
            .flatMap(ruta -> IntStream.range(0, ruta.peso()).mapToObj(i -> ruta))
            .toList();

    private static final Map<String, Integer> CAPACIDAD_BASE = RUTAS.stream()
            .collect(Collectors.toUnmodifiableMap(Ruta::codigo, Ruta::capacidadHoraria));

    private CatalogoRed() {
    }

    /**
     * Devuelve la función de capacidad para un volumen de usuarios, de modo que la ocupación
     * simulada no cambie al procesar más o menos datos que los del día de referencia.
     */
    public static ToIntFunction<String> capacidadPara(int usuarios) {
        IntUnaryOperator escalar = capacidad ->
                Math.max(1, (int) Math.round((double) capacidad * usuarios / USUARIOS_DE_REFERENCIA));
        return ruta -> escalar.applyAsInt(CAPACIDAD_BASE.getOrDefault(ruta, CAPACIDAD_RUTA_DESCONOCIDA));
    }
}
