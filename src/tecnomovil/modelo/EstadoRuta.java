package tecnomovil.modelo;

/**
 * Resultado de evaluar la ocupación de una ruta.
 * La ocupación es la razón entre los pasajeros de la hora de mayor demanda y la capacidad horaria.
 */
public record EstadoRuta(
        String ruta,
        int horaMaxima,
        long pasajerosHoraMaxima,
        int capacidadHoraria,
        double ocupacion,
        NivelOcupacion nivel) {
}
