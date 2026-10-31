# TecnoMovil Data: procesamiento funcional de datos de transporte

Módulo en Java puro (sin frameworks) que procesa un día de registros del sistema de transporte
urbano de TecnoValle con funciones puras, colecciones inmutables, lambdas y Streams.

Evidencia de aprendizaje 2, Creación de repositorio. Autor: Jorge Daniel Londoño Duque.
Universidad Digital de Antioquia.

## Ejecutar

Requiere JDK 17 o superior.

En Windows:

```bat
.\compilar_y_ejecutar.bat
.\compilar_y_ejecutar.bat 1500000
.\verificar.bat
```

- `compilar_y_ejecutar.bat [usuarios] [parte]` compila y muestra el reporte. Por defecto simula
  450.000 usuarios (cerca de 1,8 millones de registros). Con 1.500.000 usuarios se superan los
  6 millones de registros que menciona el caso. `parte` puede ser `1`, `2` o `todo`.
- `verificar.bat` compara los resultados contra datos calculados a mano y comprueba pureza,
  inmutabilidad y equivalencia entre `stream()` y `parallelStream()`.

En cualquier sistema operativo:

```bash
mkdir -p out
javac -encoding UTF-8 --release 17 -d out -sourcepath src src/tecnomovil/Main.java src/tecnomovil/verificacion/VerificacionFunciones.java
java -cp out tecnomovil.Main
java -cp out tecnomovil.verificacion.VerificacionFunciones
```

## Estructura

```
src/tecnomovil/
  Main.java                          Único punto con efectos secundarios (consola y reloj)
  analisis/AnalisisTransporte.java   Las seis tareas del caso, como funciones puras
  datos/CatalogoRed.java             Rutas, estaciones y capacidad simulada
  datos/GeneradorDatos.java          Datos simulados, reproducibles con una semilla
  modelo/                            Records y enums inmutables
  reporte/Reporte.java               Convierte resultados en texto, sin imprimir
  verificacion/                      Comprobaciones sin frameworks externos
evidencias/                          Capturas de ejecución
entrega/                             Informe en PDF y ZIP del proyecto
```

## Requerimientos del caso

| Tarea | Método de `AnalisisTransporte` |
|---|---|
| a) Afluencia por estación | `afluenciaPorEstacion`, `usuariosUnicosPorEstacion` |
| b) Horas pico | `flujoPorHora`, `horasPico` |
| c) Rutas más utilizadas | `rutasMasUtilizadas` |
| d) Patrones de viaje por usuario | `patronesDeViaje` |
| e) Tiempo promedio entre estaciones | `tiemposPorTramo`, `minutosPromedioGeneral` |
| f) Sobrecarga en rutas | `detectarSobrecarga` |

`AnalisisTransporte.secuencial()` usa `stream()` y `AnalisisTransporte.enParalelo()` usa
`parallelStream()`. El resto del código es el mismo en ambos modos.
