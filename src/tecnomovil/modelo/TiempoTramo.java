package tecnomovil.modelo;

/** Tiempo promedio que toman los usuarios en moverse de una estación a otra. */
public record TiempoTramo(String origen, String destino, long viajes, double minutosPromedio) {
}
