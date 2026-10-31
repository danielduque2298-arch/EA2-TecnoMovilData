package tecnomovil.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable de una validación del sistema de transporte (requerimiento 3.1).
 * Al ser un record, todos sus campos son finales y no existen métodos que los modifiquen.
 */
public record RegistroTransporte(
        String idUsuario,
        String ruta,
        String estacion,
        Accion accion,
        LocalDateTime timestamp) {

    public RegistroTransporte {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(estacion, "estacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public boolean esEntrada() {
        return accion == Accion.ENTRADA;
    }

    public int hora() {
        return timestamp.getHour();
    }
}
