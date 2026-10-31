package tecnomovil;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import tecnomovil.analisis.AnalisisTransporte;
import tecnomovil.datos.CatalogoRed;
import tecnomovil.datos.GeneradorDatos;
import tecnomovil.modelo.RegistroTransporte;
import tecnomovil.modelo.ResumenDiario;
import tecnomovil.reporte.Reporte;

/**
 * Punto de entrada. Toda la lógica está en funciones puras; esta clase es el único lugar con
 * efectos secundarios: lee los argumentos, mide el reloj e imprime en consola.
 *
 * <p>Uso: {@code java tecnomovil.Main [usuarios] [parte]}, donde parte es 1, 2 o todo.
 */
public final class Main {

    private static final int USUARIOS_POR_DEFECTO = CatalogoRed.USUARIOS_DE_REFERENCIA;
    private static final long SEMILLA = 2026L;
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 30);
    private static final int HORAS_PICO = 3;
    private static final double UMBRAL_CRITICO = 0.90;
    private static final int CORRIDAS = 3;

    private record Medicion<T>(T resultado, long milisegundos) {
    }

    private Main() {
    }

    public static void main(String[] args) {
        int usuarios = args.length > 0 ? Integer.parseInt(args[0]) : USUARIOS_POR_DEFECTO;
        String parte = args.length > 1 ? args[1] : "todo";

        List<RegistroTransporte> registros = GeneradorDatos.generar(usuarios, SEMILLA, FECHA);
        ToIntFunction<String> capacidad = CatalogoRed.capacidadPara(usuarios);

        Medicion<ResumenDiario> secuencial = mejorCorrida(
                () -> AnalisisTransporte.secuencial().resumir(registros, HORAS_PICO, capacidad, UMBRAL_CRITICO));
        Medicion<ResumenDiario> paralelo = mejorCorrida(
                () -> AnalisisTransporte.enParalelo().resumir(registros, HORAS_PICO, capacidad, UMBRAL_CRITICO));

        ResumenDiario resumen = secuencial.resultado();
        String primeraParte = Reporte.partes(
                Reporte.encabezado(registros.size(), usuarios),
                Reporte.afluencia(resumen),
                Reporte.horasPico(resumen));
        String segundaParte = Reporte.partes(
                Reporte.rutas(resumen),
                Reporte.patrones(resumen),
                Reporte.tiempos(resumen),
                Reporte.sobrecarga(resumen, UMBRAL_CRITICO),
                Reporte.rendimiento(
                        registros.size(),
                        secuencial.milisegundos(),
                        paralelo.milisegundos(),
                        Runtime.getRuntime().availableProcessors(),
                        resumen.equals(paralelo.resultado())));

        System.out.println(switch (parte) {
            case "1" -> primeraParte;
            case "2" -> segundaParte;
            default -> Reporte.partes(primeraParte, segundaParte);
        });
    }

    private static <T> Medicion<T> mejorCorrida(Supplier<T> tarea) {
        return IntStream.range(0, CORRIDAS)
                .mapToObj(corrida -> medir(tarea))
                .min(Comparator.comparingLong(Medicion::milisegundos))
                .orElseThrow();
    }

    private static <T> Medicion<T> medir(Supplier<T> tarea) {
        long inicio = System.nanoTime();
        T resultado = tarea.get();
        return new Medicion<>(resultado, (System.nanoTime() - inicio) / 1_000_000);
    }
}
