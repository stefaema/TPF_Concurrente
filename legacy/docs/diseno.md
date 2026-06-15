# Diseño del Sistema — Agencia de Viajes

## Arquitectura general

El sistema sigue una arquitectura en capas con responsabilidades bien separadas:

```
┌──────────────────────────────────────────────────┐
│                     Main                         │  ← punto de entrada, orquesta todo
├──────────────────────────────────────────────────┤
│         Hilos (6 tipos, 6 instancias: H1–H6)       │  ← ejecutan los segmentos
├──────────────────────────────────────────────────┤
│   Monitor  ←→  Politica                          │  ← control de concurrencia + decisión
│   Monitor  ←→  RastreadorClientes                │  ← identidad de tokens
│   Monitor  ←→  TiemposTransicion                 │  ← configuración de tiempos
├──────────────────────────────────────────────────┤
│   RedPetri                  Cliente              │  ← modelo de la red / objeto de dominio
├──────────────────────────────────────────────────┤
│   Logger          AnalizadorInvariantes          │  ← observabilidad y verificación
└──────────────────────────────────────────────────┘
```

**Principio central**: solo el Monitor conoce el marcado. Los hilos son ciegos al estado de la red — solo llaman a `fireTransition(t)` y bloquean si no pueden avanzar.

---

## Responsabilidades por clase

### `RedPetri`

**Sabe**: marcado actual, matrices Pre y Post, invariantes de plaza.
**Hace**:
- `estaHabilitada(int t)`: verifica que M - Pre[t] ≥ 0 para todas las plazas.
- `disparar(int t)`: aplica M = M + Post[t] - Pre[t].
- `verificarInvariantesPlaza()`: valida los 6 invariantes de plaza tras cada disparo. Si alguno se viola, lanza `ViolacionInvarianteException` con el nombre del invariante y el marcado actual.
- `getMarcado(): int[]`: retorna una copia defensiva del marcado. Se invoca desde `verificarInvariantesPlaza()` para construir el mensaje de la excepción sin exponer la referencia interna al arreglo.

**No sabe**: hilos, monitor, políticas, logging, identidad de clientes.

> Datos concretos: 15 plazas (P0–P14), 12 transiciones (T0–T11).
> Marcado inicial: P0=5, P1=1, P4=5, P6=1, P7=1, P10=1.

Los 6 invariantes que verifica, con sus ecuaciones y valores esperados:

| Invariante | Ecuación | Valor |
|-----------|----------|-------|
| PI-1 | M(P1) + M(P2) | = 1 |
| PI-2 | M(P10) + M(P11) + M(P12) + M(P13) | = 1 |
| PI-3 | M(P0)+M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14) | = 5 |
| PI-4 | M(P2) + M(P3) + M(P4) | = 5 |
| PI-5 | M(P5) + M(P6) | = 1 |
| PI-6 | M(P7) + M(P8) | = 1 |

---

### `Cliente`

Objeto de dominio que representa un token-cliente dentro de la red. Clase de valor inmutable — solo lleva un identificador único asignado en la creación.

```java
public class Cliente {
    private final int id;

    public Cliente(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
```

Los objetos `Cliente` son creados por `RastreadorClientes` en el momento en que T0 dispara, con IDs secuenciales comenzando en 0. Cada objeto recorre exactamente un T-invariante completo y es descartado cuando T11 dispara — no retorna al sistema. A lo largo de una ejecución completa se crean 186 objetos `Cliente` distintos, con a lo sumo 5 activos simultáneamente: garantía que emerge directamente del marcado inicial de P0 (5 tokens de capacidad).

---

### `RastreadorClientes`

Capa de identidad de tokens. Mantiene una `Queue<Cliente>` por cada plaza de flujo activa (P2, P3, P5, P8, P9, P11, P12, P13, P14) y un contador interno de IDs. Asigna identidad a los tokens en el único punto donde nace un cliente (T0) y la libera en el único punto donde muere (T11).

**Modelo de identidad de tokens**

P0 es una plaza de capacidad anónima: sus tokens representan cupos disponibles, no clientes identificados. Esto es consistente con las plazas recurso (P1, P4, P6, P7, P10), que también son anónimas. La identidad comienza en P2 (primer estado activo) y termina en P14 (último estado antes de salir del sistema).

**Tabla de flujo de clientes por transición:**

| Transición | Operación | Detalle |
|---|---|---|
| T0  | **Crear**    | `new Cliente(idCounter++)` → encolar en P2 |
| T1  | Mover        | P2 → P3 |
| T2  | Mover        | P3 → P5 |
| T3  | Mover        | P3 → P8 |
| T4  | Mover        | P8 → P9 |
| T5  | Mover        | P5 → P9 |
| T6  | Mover        | P9 → P11 |
| T7  | Mover        | P9 → P12 |
| T8  | Mover        | P12 → P14 |
| T9  | Mover        | P11 → P13 |
| T10 | Mover        | P13 → P14 |
| T11 | **Descartar** | Extraer de P14 → retornar para logging → fin de vida del objeto |

**Nota sobre join-points (P9 y P14)**: en estas plazas confluyen tokens de distintos caminos (P9 recibe de T4 y T5; P14 recibe de T8 y T10). Las colas FIFO asignan identidad en orden de llegada, lo que puede resultar en que un cliente que tomó T2/T5 sea asociado a T7 en lugar de T6 (o viceversa). Esto es correcto: cualquier combinación de asignación cliente↔transición post-join produce una secuencia que matchea uno de los 4 T-invariantes válidos. Se trata de una decisión de diseño explícita — la identidad modela la trazabilidad de los tokens, no la historia causal del objeto de dominio.

**Interfaz:**
```java
public class RastreadorClientes {
    private int idCounter = 0;  // dentro del lock del Monitor — no requiere sincronización

    public RastreadorClientes() { ... }
    public Cliente disparar(int transicion) { ... }
}
```

`disparar(t)` ejecuta la operación de la tabla: crea un `Cliente` nuevo (T0), mueve el primero de la cola origen a la cola destino (T1–T10), o extrae de P14 sin reencolar (T11). En todos los casos retorna el `Cliente` afectado para que el `Monitor` lo pase al `Logger`. Se invoca **dentro del lock del Monitor**, garantizando acceso exclusivo sin sincronización adicional.

---

### `ViolacionInvarianteException` *(extends RuntimeException)*

Excepción de dominio que señaliza la detección de un invariante de plaza violado. Es unchecked para que atraviese el bloque `try/finally` del Monitor sin necesidad de declararse en firmas — el `finally` suelta el lock correctamente antes de que la excepción propague hacia el hilo.

```java
public class ViolacionInvarianteException extends RuntimeException {
    public ViolacionInvarianteException(String invariante, int[] marcado) {
        super("Invariante violado — " + invariante
              + " | marcado actual: " + Arrays.toString(marcado));
    }
}
```

**Comportamiento ante una violación:**

1. `RedPetri.verificarInvariantesPlaza()` lanza `ViolacionInvarianteException`.
2. La excepción es unchecked → atraviesa el `catch (InterruptedException)` del Monitor sin ser capturada → llega al bloque `finally` → el lock se suelta correctamente.
3. La excepción continúa propagando hacia `SegmentoIntermedio.run()` o `SegmentoSalida.run()`, que no la capturan → el hilo muere.
4. La JVM invoca el `UncaughtExceptionHandler` registrado en `Main` → imprime el error y llama a `System.exit(1)`.

Una violación de invariante indica un bug en la implementación de `RedPetri` — no es un error recuperable. El comportamiento correcto es detener la ejecución inmediatamente con un mensaje claro.

---

### `MonitorInterface` *(dada por la consigna)*

```java
public interface MonitorInterface {
    boolean fireTransition(int transition);
}
```

**Es el único contrato que los hilos conocen.** Los hilos dependen de la interfaz, no de la implementación concreta.

---

### `Monitor` *(implements MonitorInterface)*

**Sabe**: `RedPetri`, `Politica`, `RastreadorClientes`, `TiemposTransicion`, `Logger`.
**Tiene**: un `ReentrantLock` global + un `Condition` por transición (12 en total).
**Hace**: implementa `fireTransition(int t)` — único método público.

**Constante de conflictos** — las transiciones que participan en un conflicto estructural de la red son fijas y conocidas en diseño. Se declaran como constante privada del Monitor:

```java
private static final Set<Integer> TRANSICIONES_CONFLICTO = Set.of(2, 3, 6, 7);

private boolean esConflicto(int t) {
    return TRANSICIONES_CONFLICTO.contains(t);
}
```

`esConflicto(t)` se invoca en dos puntos del flujo de `fireTransition`:
- En la condición del `while` (paso 2): para decidir si consultar `politica.debeDisparar(t)`.
- Post-disparo (paso 7): para decidir si llamar `politica.registrarDisparo(t)`.

Llamar a la política fuera de este conjunto corrompería sus contadores; no llamarla dentro de él haría que los conflictos no fueran gobernados.

Flujo interno de `fireTransition(t)`:
1. Adquiere el lock.
2. `while (!listaParaDisparar(t))`: si la transición ya cumple las condiciones estructural y de política pero aún no llegó `tiempoObjetivo[t]`, usa `condiciones[t].awaitUntil(new Date(tiempoObjetivo[t]))` para esperar hasta el instante preciso sin liberar el lock manualmente; en caso contrario usa `condiciones[t].await()`. Un único `catch (InterruptedException)` engloba ambos caminos: restaura el flag de interrupción y retorna `false`; el `finally` garantiza el unlock en todos los casos. (Ver §Semántica temporal para la justificación de `awaitUntil`.)
3. Si `t` es transición temporal: comprueba si el tiempo transcurrido desde `tiempoHabilitacion[t]` superó `tiempos.getBeta(t)`. Si es así, llama a `logger.registrarViolacionBeta(t, elapsed, beta)` (semántica débil — el disparo no se aborta, solo se registra el exceso).
4. `red.disparar(t)`.
5. `Cliente cliente = rastreador.disparar(t)` — mueve el cliente de su plaza origen a su plaza destino y retorna su referencia.
6. `red.verificarInvariantesPlaza()` (requerimiento 11).
7. Si `esConflicto(t)` → `politica.registrarDisparo(t)` (actualiza contadores internos de la política).
8. `logger.registrar(t, cliente.getId())`.
9. Actualiza relojes y señaliza: para cada transición `i`, si recién habilitada registra `tiempoHabilitacion[i] = ahora` y calcula `tiempoObjetivo[i]`; si deshabilitada limpia el reloj (`tiempoHabilitacion[i] = -1`). Luego, si habilitada, señaliza `condiciones[i].signalAll()`. Esto despierta tanto transiciones recién habilitadas como socios de conflicto que deben re-evaluar la política.
10. Suelta el lock via `finally`. Retorna `true`.

**Retorna `false` únicamente** cuando el hilo fue interrumpido (señal de shutdown). En condiciones normales siempre retorna `true`.

**Invariante de `debeDisparar`**: es una consulta pura sin efectos colaterales. Los contadores de la política se actualizan exclusivamente en el paso 7, después del disparo real. Esto garantiza que múltiples evaluaciones en el `while` no corrompan el estado de la política.

**No sabe**: qué hilo lo llama, la semántica del negocio, cuántos invariantes se completaron.

**Decisión de diseño — condiciones por transición**: evita despertar hilos que no pueden avanzar. La condición unificada del `while` (paso 2) garantiza que un hilo vuelve a dormir si la política lo rechaza, sin necesidad de condiciones separadas para bloqueo estructural y bloqueo por política.

**Por qué no puede haber múltiples hilos apilados en una misma transición de conflicto**: cada transición de conflicto (T2, T3, T6, T7) pertenece a un único segmento con exactamente un hilo asignado. Esta exclusividad emerge directamente del Algoritmo 4.3: el máximo de tokens simultáneos en las plazas de acción de cada segmento es 1, por lo que no se justifica más de un hilo por segmento. Como consecuencia, cuando H3 duerme esperando T2, no existe ningún otro hilo que intente disparar T2 — el lock queda libre y el sistema avanza. H3 se despertará cuando T2 sea habilitada nuevamente (vía señalización del paso 8), re-evaluará la política, y procederá si corresponde.

---

### `EstadisticasPolitica`

Objeto de valor inmutable que transporta los contadores finales de una ejecución. Desacopla la representación de los datos (qué se contó) de quién los produce (`Politica`) y de quién los persiste (`Logger`).

```java
public class EstadisticasPolitica {
    private final int disparosT2, disparosT3;   // agente superior / inferior
    private final int disparosT6, disparosT7;   // aprobación / rechazo

    public EstadisticasPolitica(int t2, int t3, int t6, int t7) {
        this.disparosT2 = t2; this.disparosT3 = t3;
        this.disparosT6 = t6; this.disparosT7 = t7;
    }

    // getters para disparosT2, T3, T6, T7

    public String formatear() {
        int totalAgente = disparosT2 + disparosT3;
        int totalDecision = disparosT6 + disparosT7;
        return String.format(
            "--- Estadísticas de política ---%n" +
            "Agente superior (T2): %d  Agente inferior (T3): %d  " +
            "(%.1f%% / %.1f%%)%n" +
            "Confirmadas     (T6): %d  Canceladas      (T7): %d  " +
            "(%.1f%% / %.1f%%)",
            disparosT2, disparosT3,
            100.0 * disparosT2 / totalAgente, 100.0 * disparosT3 / totalAgente,
            disparosT6, disparosT7,
            100.0 * disparosT6 / totalDecision, 100.0 * disparosT7 / totalDecision
        );
    }
}
```

---

### `Politica` *(interfaz)*

```java
public interface Politica {
    boolean debeDisparar(int transicion);
    void registrarDisparo(int transicion);
    EstadisticasPolitica getEstadisticas();
}
```

`debeDisparar(t)` es una **consulta pura**: responde si la transición `t` puede disparar según el estado actual de los contadores, sin modificarlos. Se llama dentro del `while` del Monitor, potencialmente múltiples veces por cada disparo real.

`registrarDisparo(t)` **actualiza los contadores** de la política. Se llama una única vez por disparo, después de que `red.disparar(t)` ya ejecutó.

`getEstadisticas()` devuelve un snapshot inmutable de los contadores al momento de la llamada. Se invoca desde `Main` una única vez, después de que todos los hilos terminaron — fuera del lock, sin riesgo de concurrencia.

`debeDisparar` y `registrarDisparo` se invocan **dentro del lock del Monitor**, garantizando que las decisiones son atómicas respecto al marcado y que los contadores no se actualizan concurrentemente.

Puntos de conflicto donde se aplica:

| Plaza de conflicto | Transiciones | Política aplicada           |
|:-------------------|:-------------|:----------------------------|
| P3                 | T2 vs T3     | Agente superior vs inferior |
| P9 + P10           | T6 vs T7     | Aprobación vs rechazo       |

---

### `PoliticaBalanceada` *(implements Politica)*

**Objetivo**: mantener los contadores de T2/T3 y T6/T7 equilibrados (50/50).

#### Algoritmo

Para cada par de conflicto `(tA, tB)`:

```
debeDisparar(tA): c(tA) ≤ c(tB)
debeDisparar(tB): c(tB) ≤ c(tA)
```

`registrarDisparo(t)`: incrementa `c(t)` en 1.

#### Comportamiento en empate

Cuando `c(tA) == c(tB)` ambas retornan `true`. El Monitor señaliza ambas condiciones y los dos hilos compiten por el lock. El primero que lo adquiere dispara su transición, rompiendo el empate: el contador del ganador supera al del perdedor, haciendo que `debeDisparar` del ganador retorne `false` en la siguiente evaluación. El perdedor pasa a ser el único habilitado por política.

Este mecanismo de autocorrección garantiza que la diferencia `|c(tA) - c(tB)| ≤ 1` se mantiene en todo momento de la ejecución, sin necesidad de coordinación explícita entre hilos.

#### Prueba de liveness

Nunca ambas transiciones de un par retornan `false` simultáneamente:
- Si `c(tA) < c(tB)` → `debeDisparar(tA)` es `true`.
- Si `c(tA) > c(tB)` → `debeDisparar(tB)` es `true`.
- Si `c(tA) == c(tB)` → ambas son `true`.

Siempre hay al menos una transición habilitada por política. ∎

#### Resultado esperado para 186 invariantes

186 es par → la convergencia es exacta:

| Par | tA | tB | c(tA) final | c(tB) final | Diferencia |
|-----|----|----|-------------|-------------|------------|
| Agente | T2 | T3 | **93** | **93** | 0 |
| Aprobación | T6 | T7 | **93** | **93** | 0 |

---

### `PoliticaPriorizada` *(implements Politica)*

**Objetivo**: T2 = 75% del total T2+T3; T6 = 80% del total T6+T7.

#### Algoritmo — bloqueo bidireccional

La implementación unidireccional (solo bloquear la no-preferida) produce proporciones muy alejadas del objetivo: en la práctica el hilo de T2, al nunca bloquearse por política, siempre gana la carrera al lock sobre el token de P3, resultando en ~91% en lugar de 75%.

La solución correcta es el **bloqueo bidireccional**: la transición preferida también se bloquea cuando ya supera el ratio objetivo, obligando a su hilo a `await()` y cediendo el token a la no-preferida. El ratio emerge del algoritmo, no del azar del scheduler.

Para el par T2/T3 (ratio objetivo 3:1):

```
debeDisparar(T2): (c2 + 1) ≤ 3 * (c3 + 1)    →  T2 se bloquea cuando ya va 3:1 adelante
debeDisparar(T3): (c3 + 1) * 4 ≤ (c2 + c3 + 1) →  T3 se bloquea cuando superaría el 25%
```

Para el par T6/T7 (ratio objetivo 4:1):

```
debeDisparar(T6): (c6 + 1) ≤ 4 * (c7 + 1)    →  T6 se bloquea cuando ya va 4:1 adelante
debeDisparar(T7): (c7 + 1) * 5 ≤ (c6 + c7 + 1) →  T7 se bloquea cuando superaría el 20%
```

`registrarDisparo(t)`: incrementa `c(t)` en 1.

#### Prueba de liveness

Para T2/T3 — ambas no pueden estar simultáneamente bloqueadas:

- Si T2 bloqueada: `c2+1 > 3*(c3+1)` → `c2 ≥ 3*c3+3` → `(c3+1)*4 = 4*c3+4 ≤ c2+c3+1` → T3 habilitada ✓
- Si T3 bloqueada: `(c3+1)*4 > c2+c3+1` → `c2 ≤ 3*c3+2` → `c2+1 ≤ 3*(c3+1)` → T2 habilitada ✓

**Propiedad más fuerte**: en todo instante exactamente *una* del par está habilitada — ambas condiciones son mutuamente excluyentes. Esto garantiza que el hilo de la "otra" transición esté en `await()` cuando la primera puede disparar: no hay carrera entre H2 y H3 por el token de P3.

Mismo razonamiento aplica a T6/T7. ∎

#### Resultado esperado para 186 invariantes

Verificación aritmética con la inecuación entera:

**Par T2/T3 (factor = 4):**

El patrón de disparos que impone la política es 3 T2 por cada T3 (ratio 3:1 = 75%:25%). Para 186 total: `186 = 46 × 4 + 2`, por lo tanto T2=140, T3=46.

| Transición | Disparos | Porcentaje |
|-----------|----------|------------|
| T2 (agente superior) | **140** | 75.27% ≥ 75% ✓ |
| T3 (agente inferior) | **46** | 24.73% |

**Par T6/T7 (factor = 5):**

El patrón es 4 T6 por cada T7 (ratio 4:1 = 80%:20%). Para 186 total: `186 = 37 × 5 + 1`, por lo tanto T6=149, T7=37.

| Transición | Disparos | Porcentaje |
|-----------|----------|------------|
| T6 (aprobación) | **149** | 80.11% ≥ 80% ✓ |
| T7 (rechazo) | **37** | 19.89% |

#### Verificación de consistencia con T-invariantes

Los disparos de T2/T3 y T6/T7 determinan la distribución de los 4 T-invariantes:

| T-invariante | Camino | Cantidad esperada |
|---|---|---|
| I1 | inferior + rechazado (T3 ∩ T7) | 46 × (37/186) × ... |
| I2 | inferior + aprobado (T3 ∩ T6) | |
| I3 | superior + rechazado (T2 ∩ T7) | |
| I4 | superior + aprobado (T2 ∩ T6) | |

Los conflictos T2/T3 y T6/T7 son **estadísticamente independientes** (ocurren en plazas distintas y no comparten recursos durante la decisión). Por lo tanto:

```
I1 = T3 ∩ T7 = 46 × 37/186 ≈  9
I2 = T3 ∩ T6 = 46 × 149/186 ≈ 37
I3 = T2 ∩ T7 = 140 × 37/186 ≈ 28
I4 = T2 ∩ T6 = 140 × 149/186 ≈ 112
Total = 9 + 37 + 28 + 112 = 186 ✓
```

> Nota: los valores de I1–I4 son aproximaciones de la distribución esperada. La independencia real depende de la dinámica de scheduling en la ejecución concreta; los valores finales pueden diferir ligeramente pero la suma siempre será 186 y los totales por T2/T3 y T6/T7 cumplirán los umbrales.

---

### `Segmento` *(abstract, implements Runnable)*

Clase base abstracta que unifica el contrato de todos los hilos de la red. Encapsula el único campo compartido: la referencia al monitor, que es el único punto de acceso al estado de la red.

```java
abstract class Segmento implements Runnable {
    protected final MonitorInterface monitor;

    protected Segmento(MonitorInterface monitor) {
        this.monitor = monitor;
    }
}
```

Las dos subclases concretas modelan los dos roles distintos que emergen del Algoritmo 4.3:

```
Segmento (abstract)
  │
  ├── SegmentoIntermedio   → H1–H5: operan en el cuerpo del pipeline
  └── SegmentoSalida       → H6: opera sobre P14, controla la terminación (1 hilo; T11 inmediata)
```

---

### `SegmentoIntermedio` *(extends Segmento)*

Representa los hilos que ejecutan un tramo fijo del pipeline de forma indefinida. No tienen condición de parada propia — son detenidos externamente vía interrupción cuando el sistema completa los 186 invariantes.

```java
class SegmentoIntermedio extends Segmento {
    private final int[] transiciones;

    SegmentoIntermedio(MonitorInterface monitor, int[] transiciones) {
        super(monitor);
        this.transiciones = transiciones.clone();  // copia defensiva
    }

    @Override
    public void run() {
        while (true) {
            for (int t : transiciones) {
                if (!monitor.fireTransition(t)) return;  // shutdown
            }
        }
    }
}
```

**Terminación**: cuando `Main` interrumpe estos hilos, el Monitor detecta la `InterruptedException` dentro de `await()` o `sleep()`, retorna `false`, y el hilo sale del loop limpiamente.

**Instancias**:

| Hilo | Transiciones | Segmento de red |
|------|-------------|-----------------|
| H1   | T0, T1      | S_A (ingreso)   |
| H2   | T3, T4      | S_inferior      |
| H3   | T2, T5      | S_superior      |
| H4   | T6, T9, T10 | S_aprobacion    |
| H5   | T7, T8      | S_rechazo       |

> Los hilos H1–H5 son `SegmentoIntermedio`; el hilo H6 es `SegmentoSalida`. Total implementado: **6 hilos**.

---

### `SegmentoSalida` *(extends Segmento)*

Representa el hilo que opera sobre la plaza P14 (post-join final) disparando T11. Controla la terminación del sistema mediante un `AtomicInteger` compartido: el hilo reclama atómicamente un slot antes de disparar, y sale cuando no quedan slots disponibles.

> **Nota sobre cantidad de hilos:** el Algoritmo 4.3 del artículo prescribe 5 hilos para este segmento porque M(P14) puede llegar a 5. Sin embargo, T11 es una transición **inmediata** (no temporal): no hay `Thread.sleep()` fuera del lock, de modo que cada disparo de T11 ocurre íntegramente dentro de la sección crítica del Monitor. Múltiples hilos compitiendo por T11 se serializarían igual que 1 solo hilo — el throughput sería idéntico. La implementación instancia un **único hilo** (H6) para S_salida, lo cual es funcionalmente equivalente a los 5 prescritos. El beneficio de múltiples hilos en un segmento solo existe cuando sus transiciones son temporales: en ese caso, los sleeps ocurren fuera del lock y el paralelismo es real. Ver preguntas-respuestas.md §3.

```java
class SegmentoSalida extends Segmento {
    private final int transicion;
    private final AtomicInteger contador;
    private final int objetivo;

    SegmentoSalida(MonitorInterface monitor, int transicion,
                   AtomicInteger contador, int objetivo) {
        super(monitor);
        this.transicion = transicion;
        this.contador   = contador;
        this.objetivo   = objetivo;
    }

    @Override
    public void run() {
        while (contador.getAndIncrement() < objetivo) {
            if (!monitor.fireTransition(transicion)) return;
        }
    }
}
```

`getAndIncrement()` retorna el valor anterior al incremento. El hilo que obtenga un slot `< objetivo` tiene permiso para disparar; el primero que obtenga `≥ objetivo` sale sin disparar. Como la operación es atómica, no existe race condition entre el check y el incremento — exactamente 186 disparos de T11 ocurren, sin overshoot posible.

**Quién controla el contador**: solo los hilos `SegmentoSalida`, y lo hacen antes del disparo (pre-claim), no después.

**Terminación**: H6 termina solo al agotar los 186 slots. `Main` detecta esto mediante `join()` y recién entonces interrumpe los H1–H5.

---

### `Logger`

Recibe disparos del Monitor y los escribe a un archivo de log incluyendo el ID del cliente involucrado.

**Campos:** `PrintWriter writer`, `long startTime` (milisegundos desde epoch, capturado en construcción), `int violaciones` (contador de disparos que superaron su límite β).

**Interfaz pública:**
- `registrar(int t, int clienteId)`: escribe una línea de disparo al archivo.
- `registrarViolacionBeta(int t, long elapsed, long beta)`: incrementa el contador de violaciones y escribe una línea `[WARN]` con la transición, el tiempo transcurrido, el límite β y el exceso en ms.
- `getViolaciones()`: retorna el número de violaciones β registradas hasta el momento. Se llama desde `Main` tras la ejecución para incluir el conteo en el resumen.
- `escribirResumen(String texto)`: escribe un bloque de texto libre al final del log (usado por `Main` para el reporte de política).
- `cerrar()`: flush explícito y cierre del stream.

**Ciclo de vida del archivo:**
- Al construirse crea el directorio padre si no existe.
- Si el archivo ya existe lo rota: lo renombra a `<archivo>.1`, desplazando backups anteriores hasta `.5` y descartando el más viejo (`MAX_BACKUPS = 5`). Esto preserva las últimas cinco ejecuciones.
- Un shutdown hook registrado en el constructor garantiza que el buffer se flusheará incluso si el programa termina abruptamente (p.ej. `System.exit(1)` por `ViolacionInvarianteException`).

**Modelo de sincronización**: `Logger` no necesita sincronización propia.
- `registrar`: se invoca siempre **dentro del lock del Monitor**. Las llamadas son completamente seriales por construcción.
- `escribirResumen` y `cerrar`: se invocan **una única vez** desde `Main`, después del `join()` de todos los hilos. En ese punto ningún hilo está activo.

`Logger` no conoce `Politica` ni `EstadisticasPolitica`. Solo recibe texto ya formateado. Quién produce ese texto y qué significa es responsabilidad del llamador.

Etiquetar la clase como "Thread-safe" sería engañoso: sugeriría que tiene su propio mecanismo de exclusión cuando en realidad delega esa garantía al lock del Monitor.

Formato de cada entrada de disparo:
```
[HH:mm:ss.SSS] T<n> (cliente=<id>)
```
El timestamp es relativo al inicio del programa (diferencia con `startTime`), lo que lo hace útil para el análisis temporal de la consigna.

Ejemplo:
```
[00:00:00.123] T0 (cliente=2)
[00:00:00.131] T0 (cliente=4)
[00:00:00.145] T1 (cliente=2)
[00:00:00.152] T3 (cliente=2)
[00:00:00.160] T1 (cliente=4)
[00:00:00.167] T2 (cliente=4)
```

---

### `Main`

**Instancia todo, arranca los hilos, coordina el shutdown.**

#### Modos de ejecución

`Main` soporta dos modos seleccionados por los argumentos de línea de comandos:

- **Sin argumentos → modo interactivo**: presenta un menú por consola para configurar el número de ejecuciones (1–50), la distribución de políticas (N× BALANCEADA + M× PRIORIZADA) y los tiempos de transición (default o personalizable). Ejecuta todas las ejecuciones en secuencia con una barra de progreso ASCII, y al finalizar muestra una tabla resumen con porcentajes y permite ver el detalle de cada ejecución individualmente.

- **Con un argumento → modo batch**: `java Main balanceada` o `java Main priorizada`. Ejecuta una única ejecución con los tiempos por defecto, imprime las estadísticas de política y el análisis de invariantes por consola, escribe el log en `logs/log.txt`.

#### Tipos internos

**`enum TipoPolitica`**: centraliza los valores válidos (`BALANCEADA`, `PRIORIZADA`) y encapsula el parseo desde string mediante `parsear(String arg)`. Garantiza que agregar una tercera política solo requiere un nuevo valor en el enum y un caso en el `switch` — sin lógica de strings dispersa.

**`ResultadoEjecucion`** (inner class): transporta los resultados de una ejecución completada: número de ejecución, política usada, duración en ms, `EstadisticasPolitica` (contadores internos de la política), `AnalizadorInvariantes.Resultado` (conteos de T-invariantes) y `violacionesBeta` (número de disparos que superaron su límite β). Permite mostrar la tabla resumen y el drill-down sin re-procesar los logs.

#### Barra de progreso

El método `iniciarProgreso()` lanza un hilo daemon que refresca una barra ASCII por `\r` cada 150 ms, leyendo el `AtomicInteger` compartido con los `SegmentoSalida`. Al finalizar la ejecución, `Main` interrumpe este hilo y sobreescribe la línea con el resultado final (duración, ✓).

#### Constantes

| Constante | Valor | Descripción |
|---|---|---|
| `OBJETIVO` | 186 | Invariantes a completar por ejecución |
| `DIR_LOGS` | `"logs"` | Directorio de archivos de log |
| `LOG_BATCH` | `"logs/log.txt"` | Ruta del log en modo batch |
| `TIEMPOS_DEFAULT` | — | Tiempos por defecto de las transiciones temporales |

Tiempos por defecto:

| Transición | Tiempo (ms) | Acción modelada |
|---|---|---|
| T1  | 100 | Ingreso del cliente a la sala de espera |
| T4  | 200 | Atención finalizada por agente de reservas inferior |
| T5  | 200 | Atención finalizada por agente de reservas superior |
| T8  | 100 | Procesamiento de cancelación |
| T9  | 150 | Procesamiento de confirmación |
| T10 | 150 | Procesamiento de pago |

#### Flujo de una ejecución (método `ejecutar`)

```java
// Para cada ejecución (modo interactivo: N veces; modo batch: 1 vez):
RedPetri  red      = new RedPetri();
Logger    logger   = new Logger(archivoLog);   // crea directorio, rota log anterior
Politica  politica = switch (tipo) { ... };
RastreadorClientes rastreador = new RastreadorClientes();
Monitor   monitor  = new Monitor(red, politica, rastreador, tiempos, logger);
AtomicInteger contador = new AtomicInteger(0);

// H1–H5: pipeline, terminación por interrupción externa
Thread[] hilosIntermedios = {
    new Thread(new SegmentoIntermedio(monitor, new int[]{0, 1}),     "H1-ingreso"),
    new Thread(new SegmentoIntermedio(monitor, new int[]{3, 4}),     "H2-inferior"),
    new Thread(new SegmentoIntermedio(monitor, new int[]{2, 5}),     "H3-superior"),
    new Thread(new SegmentoIntermedio(monitor, new int[]{6, 9, 10}), "H4-aprobacion"),
    new Thread(new SegmentoIntermedio(monitor, new int[]{7, 8}),     "H5-rechazo"),
};
// H6: salida, auto-terminación al agotar 186 slots (1 hilo; T11 es inmediata)
Thread[] hilosSalida = new Thread[1];
hilosSalida[0] = new Thread(
    new SegmentoSalida(monitor, 11, contador, OBJETIVO), "H6-salida");

for (Thread t : hilosIntermedios) t.start();
for (Thread t : hilosSalida)      t.start();

// Esperar terminación natural de H6
for (Thread t : hilosSalida)      t.join();
// Interrumpir H1–H5 y esperar que salgan limpiamente
for (Thread t : hilosIntermedios) t.interrupt();
for (Thread t : hilosIntermedios) t.join();

// Cerrar log, calcular resultado y retornar
logger.escribirResumen(politica.getEstadisticas().formatear());
int violaciones = logger.getViolaciones();
logger.cerrar();
AnalizadorInvariantes.Resultado analisis =
    new AnalizadorInvariantes(archivoLog, OBJETIVO).calcular();
return new ResultadoEjecucion(num, tipo, duracion, politica.getEstadisticas(), analisis, archivoLog, violaciones);
```

Los logs van a `logs/ejecucion-N-<politica>.txt` en modo interactivo y a `logs/log.txt` en modo batch.

---

## Semántica temporal

Las transiciones {T1, T4, T5, T8, T9, T10} son **temporizadas** según la semántica de Redes de Petri Temporizadas (Time Petri Nets, Merlin 1974). Cada transición temporizada tiene asociada una **ventana de disparo `[alfa, beta]` ms** que se mide desde el momento en que la transición **se habilita** — no desde que el hilo decide dispararla.

### Ventana de disparo `[alfa, beta]`

| Parámetro | Significado |
|-----------|-------------|
| `alfa` (EFT — Earliest Firing Time) | La transición **no puede** disparar antes de que transcurran `alfa` ms desde su habilitación |
| `beta` (LFT — Latest Firing Time)  | La transición **debería** disparar antes de que transcurran `beta` ms (semántica débil: se registra pero no se aborta si se excede por scheduling del SO) |

### Reloj de habilitación

El `Monitor` mantiene dos arreglos internos por transición:

- `tiempoHabilitacion[t]`: instante en que la transición fue habilitada por última vez (−1 si no está habilitada actualmente). Se registra en el constructor para las transiciones habilitadas en el marcado inicial y se actualiza tras cada disparo.
- `tiempoObjetivo[t]`: instante objetivo de disparo, calculado cuando la transición se habilita como un valor aleatorio dentro de la ventana:

```
tiempoObjetivo[t] = tiempoHabilitacion[t] + alfa + random(0, beta − alfa)
```

Si `alfa == beta`, el disparo es determinista exactamente en `alfa`. La aleatorización dentro de la ventana modela que el proceso no siempre tarda el mínimo posible — aporta variabilidad real para el análisis temporal.

**Reset del reloj**: si una transición se deshabilita (otra transición consumió alguno de sus tokens de entrada) y luego se re-habilita, `tiempoHabilitacion[t]` se reinicia al nuevo instante de habilitación y `tiempoObjetivo[t]` se recalcula.

### Implementación — `Condition.awaitUntil`

La espera temporal se implementa dentro del marco de sincronización del Monitor usando `Condition.awaitUntil(Date)`, que **libera el lock atomicamente y espera hasta la fecha objetivo o hasta recibir un signal**:

```java
// En fireTransition(t):
lock.lock();
try {
    while (!listaParaDisparar(t)) {
        if (tiempos.esTemporal(t) && habilitadaPorEstadoYPolitica(t)) {
            // Estructuralmente lista y política OK, pero ventana aún no alcanzada.
            // awaitUntil libera el lock — los otros hilos pueden progresar.
            condiciones[t].awaitUntil(new Date(tiempoObjetivo[t]));
        } else {
            condiciones[t].await();
        }
    }
    // Dispara, rastrea, verifica, registra, actualiza relojes, señaliza...
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return false;
} finally {
    lock.unlock();
}
```

Donde `listaParaDisparar(t)` verifica las tres condiciones simultáneamente:
1. `red.estaHabilitada(t)` — condición estructural
2. `!esConflicto(t) || politica.debeDisparar(t)` — condición de política
3. `!tiempos.esTemporal(t) || now >= tiempoObjetivo[t]` — condición temporal

**Por qué `awaitUntil` es equivalente a sleep-fuera-del-lock**: `Condition.awaitUntil` libera el lock internamente (igual que `await`), lo que permite que los otros 5 hilos progresen durante la espera. El comportamiento de concurrencia es idéntico al patrón unlock/sleep/lock, pero la implementación es más limpia: no se necesita la variable `lockHeld`, no hay doble bloque `try/catch`, y un único `finally` garantiza el unlock.

### Condición unificada de espera

La fusión de las tres condiciones en un único `while` con `awaitUntil` o `await` garantiza que el hilo re-evalúa todo el estado al despertar, independientemente de si fue despertado por un signal (otra transición disparó), por el timer (llegó `tiempoObjetivo`) o por interrupción (shutdown). No se requiere lógica de coordinación adicional entre condiciones.

### Semántica modelada

La transición produce sus tokens de salida cuando **dispara** (al alcanzar `tiempoObjetivo`), no cuando se habilita. Esto es coherente con la semántica formal de TPN: el marcado se actualiza al completarse el disparo. El período `[habilitación, tiempoObjetivo]` representa que el proceso está en curso — el cliente está siendo atendido, realizando el pago, etc. — pero todavía no completó esa etapa.

### Clase `TiemposTransicion`

```java
public class TiemposTransicion {

    public record VentanaTemporal(long alfa, long beta) { ... }

    private final Map<Integer, VentanaTemporal> ventanas;

    public TiemposTransicion(Map<Integer, VentanaTemporal> ventanas) {
        this.ventanas = Map.copyOf(ventanas);
    }

    public boolean esTemporal(int t)  { return ventanas.containsKey(t); }
    public long    getAlfa(int t)     { return ventanas.get(t).alfa(); }
    public long    getBeta(int t)     { return ventanas.get(t).beta(); }
}
```

Las transiciones no presentes en el mapa (T0, T2, T3, T6, T7, T11) son **inmediatas**: disparan sin condición temporal. `Main` construye la instancia con las ventanas del experimento en curso y la pasa al Monitor.

### Ventanas por defecto

| Transición | Acción | alfa (ms) | beta (ms) |
|---|---|---|---|
| T1  | Ingreso a sala de espera     |  80 | 120 |
| T4  | Atención agente inferior     | 150 | 250 |
| T5  | Atención agente superior     | 150 | 250 |
| T8  | Procesamiento de cancelación |  50 | 150 |
| T9  | Procesamiento de confirmación| 100 | 200 |
| T10 | Procesamiento de pago        | 100 | 200 |

---

## Verificación de T-invariantes por regex

La verificación se realiza mediante la clase `AnalizadorInvariantes`, que procesa el log al finalizar la ejecución.

### Proceso de análisis

Como cada `Cliente` tiene un ID único por ciclo, la estructura del log es 1:1 con los T-invariantes: cada ID aparece en exactamente una secuencia (completa o incompleta en caso de shutdown).

**Paso 1 — Agrupación por ID**: el analizador recorre el log y agrupa las transiciones por `(cliente=<id>)`. El resultado son hasta 186 secuencias, una por ID:

```
cliente=0:   T0 T1 T3 T4 T7 T8 T11
cliente=1:   T0 T1 T2 T5 T6 T9 T10 T11
cliente=2:   T0 T1 T3 T4 T6 T9 T10 T11
...
cliente=185: T0 T1 T2 T5 T7 T8 T11
```

Cada secuencia no tiene interleaving — las transiciones aparecen en el orden real en que ese cliente las atravesó.

**Paso 2 — Clasificación por regex**: cada secuencia se compara directamente contra los 4 patrones exactos (sin `.*`, sin segmentación interna):

| T-invariante | Regex exacta |
|---|---|
| I1 | `T0 T1 T3 T4 T7 T8 T11` |
| I2 | `T0 T1 T3 T4 T6 T9 T10 T11` |
| I3 | `T0 T1 T2 T5 T7 T8 T11` |
| I4 | `T0 T1 T2 T5 T6 T9 T10 T11` |

Cada ID es ya un ciclo atómico: no hay segmentación por T11 ni ambigüedad de cross-client matching. Una secuencia que no matchea ningún patrón indica un bug en la implementación.

**Paso 3 — Verificación**: se comprueba que el total de secuencias clasificadas sea exactamente 186 y que la distribución entre I1/I2/I3/I4 sea consistente con la política ejecutada.

### `AnalizadorInvariantes`

Se ejecuta desde `Main` **después de `logger.cerrar()`**, garantizando que el archivo de log está completo y cerrado antes de procesarlo. No requiere sincronización: es un proceso secuencial post-ejecución.

Constructor: `AnalizadorInvariantes(String archivo, int objetivo)`. El parámetro `objetivo` (186) valida que el total de invariantes completados coincide con el esperado y sirve para calcular porcentajes en el reporte.

**API pública:**
- `calcular(): Resultado` — lee el log, agrupa por cliente y clasifica. Retorna un `Resultado` sin imprimir nada. Usado en modo interactivo para poblar la `ResultadoEjecucion`.
- `analizar(): void` — llama a `calcular()` e imprime el reporte completo por consola. Usado en modo batch.

**`Resultado` (inner class estática):** transporta los conteos sin acoplarse a la presentación.
- Campos: `total`, `conteos[4]` (conteos de I1–I4), `incompletos`, `invalidos`.
- Métodos de conveniencia: `superior()` (I3+I4), `inferior()` (I1+I2), `aprobados()` (I2+I4), `cancelados()` (I1+I3), `pctSuperior()`, `pctAprobados()`.
- Usado por `Main` para construir la tabla resumen y el detalle de cada ejecución.

#### Manejo de secuencias al shutdown

Cuando los 186 T11 se completan y Main interrumpe H1–H5, puede haber clientes "en vuelo" cuyas transiciones parciales quedaron registradas sin T11 final. El analizador distingue tres casos:

- **Completas (I1–I4)**: secuencia termina en T11 y matchea un patrón conocido → se contabiliza.
- **Incompletas**: secuencia no termina en T11 (cliente en vuelo al momento del shutdown) → se descarta silenciosamente.
- **Inválidas**: secuencia termina en T11 pero no matchea ningún patrón → se reporta como error (indica un bug).

Esto es correcto por diseño: los 186 invariantes completos ya tienen su T11 en el log antes del shutdown.

Ejemplo de reporte producido por `analizar()`:

```
====================================================
  ANÁLISIS DE T-INVARIANTES
====================================================
Invariantes completados : 186 / 186  [OK]

  Desglose por camino:
    I1 (inferior + rechazado) :  47  ( 25.3%)
    I2 (inferior + aprobado)  :  46  ( 24.7%)
    I3 (superior + rechazado) :  47  ( 25.3%)
    I4 (superior + aprobado)  :  46  ( 24.7%)

  Distribución de agentes:
    Agente superior (I3+I4) :  93  ( 50.0%)
    Agente inferior (I1+I2) :  93  ( 50.0%)

  Distribución de decisiones:
    Confirmadas  (I2+I4) :  92  ( 49.5%)
    Canceladas   (I1+I3) :  94  ( 50.5%)
====================================================
```

---

## Resumen de clases

| Clase / Interfaz | Tipo | Responsabilidad principal |
|---|---|---|
| `ViolacionInvarianteException` | Excepción (RuntimeException) | Señaliza bug en la red: invariante de plaza violado post-disparo |
| `MonitorInterface` | Interface | Contrato público del monitor |
| `Politica` | Interface | Contrato de las políticas de conflicto |
| `RedPetri` | Clase | Modelo de la red (marcado, matrices, invariantes); expone copia defensiva del marcado |
| `Cliente` | Clase | Objeto de dominio — token con identidad |
| `RastreadorClientes` | Clase | Identidad de tokens durante el flujo de la red |
| `Monitor` | Clase | Control de concurrencia, único acceso al marcado |
| `EstadisticasPolitica` | Clase (valor inmutable) | Snapshot de contadores de política; sabe formatearse como String |
| `PoliticaBalanceada` | Clase | Política 50/50 en ambos conflictos |
| `PoliticaPriorizada` | Clase | Política 75%/80% en los conflictos |
| `Segmento` | Clase abstracta | Contrato base de todos los hilos de la red (campo `monitor` compartido) |
| `SegmentoIntermedio` | Clase | Hilos H1–H5: pipeline, loop infinito, terminación por interrupción |
| `SegmentoSalida` | Clase | Hilo H6: exit handler, auto-terminación tras 186 disparos de T11 (1 hilo; T11 es inmediata) |
| `TiemposTransicion` | Clase | Ventanas temporales `[alfa, beta]` de transiciones temporizadas; record `VentanaTemporal` |
| `Logger` | Clase | Registro de disparos con ID de cliente; detección y log de violaciones β; rotación de logs (MAX_BACKUPS=5); shutdown hook |
| `AnalizadorInvariantes` | Clase | Verificación post-ejecución de T-invariantes por regex |
| `AnalizadorInvariantes.Resultado` | Clase interna estática | Transporta conteos y métricas del análisis sin acoplarse a la presentación |
| `TipoPolitica` | Enum interno (Main) | Valores válidos de política y parseo desde argumento de línea de comandos |
| `Main.ResultadoEjecucion` | Clase interna (Main) | Resultados de una ejecución: duración, estadísticas de política, análisis de invariantes |
| `Main` | Clase | Punto de entrada; modo interactivo (menú, barra de progreso, tabla resumen) y modo batch |
