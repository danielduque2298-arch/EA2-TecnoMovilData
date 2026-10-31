package tecnomovil.modelo;

/** Par inmutable clave-total usado en los rankings (horas pico y rutas más utilizadas). */
public record Conteo<K>(K clave, long total) {
}
