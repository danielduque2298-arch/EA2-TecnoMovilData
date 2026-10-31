package tecnomovil.modelo;

import java.util.List;
import java.util.Map;

/** Agrupa, de forma inmutable, las respuestas a las seis tareas del caso de estudio. */
public record ResumenDiario(
        Map<String, Long> afluenciaPorEstacion,
        Map<String, Long> usuariosUnicosPorEstacion,
        Map<Integer, Long> flujoPorHora,
        List<Conteo<Integer>> horasPico,
        List<Conteo<String>> rutasMasUtilizadas,
        Map<String, List<String>> patronesDeViaje,
        List<TiempoTramo> tiemposPorTramo,
        double minutosPromedioGeneral,
        List<EstadoRuta> estadoDeRutas) {
}
