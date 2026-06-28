# Preguntas y respuestas — Agencia de Viajes (Red de Petri)

Pequeña y humilde bitácora en la cual se irán anotando algunas preguntas que fueron surgiendo en el desarrollo o entendimiento del TP, junto con sus respuestas, para tener un historial sobre lo mismo y poder repasar conceptos clave a la hora de la defensa.

---

## 1. ¿Las transiciones temporales deberían hacer el sleep con el lock tomado, dado que ese tiempo simula una actividad concreta?

**Contexto:** Las transiciones {T1, T4, T5, T8, T9, T10} son temporales. En `Monitor.fireTransition`, el lock se suelta antes del `Thread.sleep` y se retoma al terminar. Esto es lo más óptimo en términos de velocidad de ejecución (el sistema avanza en paralelo durante los sleeps), pero esos tiempos están simulando actividades concretas del dominio — un agente atendiendo a un cliente, un pago procesándose. ¿No debería esa actividad ser atómica, es decir, ocurrir con el lock tomado?

### Lo que el lock protege

El lock existe para proteger el **marcado** — el único estado compartido de la red. La actividad simulada por el sleep (un agente atendiendo a un cliente, un pago procesándose) no accede ni modifica estado compartido: es tiempo que transcurre en el mundo real. Sostener el lock durante el sleep no agrega ninguna corrección semántica; solo impediría que los otros 9 hilos avancen, degenerando el sistema a ejecución secuencial.

### Los recursos del dominio sí están bloqueados durante el sleep

Esta es la clave: la exclusión del recurso no la da el lock de Java, sino el **marcado de la red de Petri**. Tomando T4 como ejemplo:

- T3 dispara y **consume P7** (agente inferior) → produce P8 (cliente en atención)
- Durante el sleep de T4:
  - **P7 = 0** → el agente inferior sigue ocupado; T3 no puede volver a disparar
  - **P8 = 1** → el cliente está siendo atendido
- T4 dispara al finalizar el sleep: **P7 = 1** (agente libre) + **P9 += 1** (cliente pasa a aprobación)

El recurso relevante queda bloqueado implícitamente en el marcado. El lock de Java es mecanismo de coordinación entre hilos; la exclusión del recurso del dominio la garantiza la estructura de la red.

### ¿Qué debe ser atómico y qué no?

| Qué | Mecanismo |
|-----|-----------|
| Cambio del marcado (disparo) | Lock de Java |
| Consulta + registro en la política | Lock de Java |
| "El agente está ocupado" durante la atención | Invariante PI-6: M(P7)+M(P8)=1 |
| La actividad en sí (duración de la atención) | Nada — no modifica estado compartido |

El disparo de T4 *sí* es atómico: consume P8, produce P7 y P9 en una única sección crítica. Lo que no es atómico (ni debe serlo) es el tiempo que transcurre antes de ese disparo.

### El re-check post-sleep

Después de retomar el lock, el Monitor re-evalúa la condición de habilitación:

```java
while (!red.estaHabilitada(t) || (esConflicto(t) && !politica.debeDisparar(t))) {
    condiciones[t].await();
}
```

En la práctica es redundante para esta red: el Algoritmo 4.3 garantiza que cada segmento tiene como máximo 1 token simultáneo en sus plazas de acción, por lo que ningún otro hilo puede "robar" el token de entrada durante el sleep. Sin embargo, es la forma **formalmente correcta** de implementar el patrón — no asumir que el estado no cambió durante el período sin lock — y hace al código correcto ante cualquier variación futura de la red.

### Resumen

Soltar el lock durante el sleep es correcto, óptimo y semánticamente consistente con la red de Petri. La "atomicidad" de la actividad la garantiza el marcado de la red, no el lock de Java. Sostener el lock destruiría el paralelismo sin agregar ninguna garantía real.

---

## 2. El monitor serializa todos los disparos. ¿No sería más eficiente usar locks granulares por plaza, dado que transiciones como T0 y T6 modifican plazas disjuntas?

**Contexto:** El `Monitor` utiliza un único `ReentrantLock` global: solo un hilo puede estar dentro de la sección crítica de `fireTransition` en un instante dado. Esto implica que T0 y T6, aunque operan sobre conjuntos de plazas completamente disjuntos ({P0, P1, P4} → {P2} y {P9, P10} → {P11} respectivamente), no pueden disparar de forma verdaderamente simultánea. ¿No sería más correcto y eficiente implementar locks granulares, uno por plaza?

### El lock granular es una técnica estándar en concurrencia general

El principio de reducir la granularidad del lock para aumentar el throughput es bien establecido. `ConcurrentHashMap` en Java lo aplica con locks por bucket; los motores de bases de datos relacionales lo hacen con row-level locking. La idea es siempre la misma: proteger exclusivamente lo que se necesita, por el menor tiempo posible.

### Para redes de Petri, la complejidad crece de forma no trivial

El problema específico es que una transición accede a **múltiples plazas simultáneamente**. Disparar T0 requiere leer y modificar {P0, P1, P4, P2} de forma atómica. Con locks por plaza surgen tres problemas:

**1. Deadlock entre hilos concurrentes.** En general, dos hilos que adquieren subconjuntos distintos de plazas en distinto orden pueden bloquearse mutuamente. La solución clásica es imponer un **orden total fijo** sobre las plazas (siempre adquirir P0 < P1 < ... < P14) y que cada transición adquiera sus locks en ese orden. Esto previene el deadlock pero requiere que el código de disparo conozca a priori el conjunto completo de plazas que toca.

**2. La política también es estado compartido.** `PoliticaBalanceada` y `PoliticaPriorizada` mantienen contadores que deben ser leídos y actualizados de forma atómica junto con el marcado. Con locks granulares, la política necesita su propio mecanismo de sincronización coordinado con los locks de las plazas relevantes al conflicto, lo que introduce un segundo nivel de dependencias entre locks.

**3. La verificación de invariantes abarca múltiples plazas.** Comprobar PI-1 (M(P1)+M(P2)=1) requiere sostener simultáneamente los locks de P1 y P2 en un estado consistente. Un hilo que está a mitad de adquisición de esas mismas plazas puede entrar en conflicto, forzando una coordinación adicional.

### En este sistema, la ganancia de performance sería nula

El cuello de botella del sistema no es la contención sobre el lock en las secciones críticas de disparo, sino la duración de los sleeps de las transiciones temporales (100–200 ms). Un disparo inmediato (T0, T6, T11, etc.) tarda del orden de microsegundos dentro de la sección crítica. Que T0 y T6 se serialicen en ese intervalo es imperceptible frente a los 200 ms que H2 pasa durmiendo en T4. La liberación del lock durante los sleeps es precisamente lo que habilita el verdadero paralelismo del sistema: múltiples hilos pueden estar ejecutando sus actividades temporales de forma simultánea aunque los disparos sean secuenciales.

### El monitor de lock único es la solución canónica para redes de Petri

En la literatura académica y en implementaciones prácticas de monitores de redes de Petri, el patrón estándar es exactamente el implementado aquí: un único lock global con condiciones por transición. Es correcto, verificable formalmente, y suficiente porque el paralelismo real no proviene de la concurrencia entre disparos inmediatos, sino de la concurrencia entre actividades temporales con el lock suelto.

### Alternativas que sí se aplican en sistemas de producción con contención real

| Técnica | Cuándo aplica |
|---|---|
| Lock granular por recurso | Contención medida y demostrada; recursos separables sin dependencias cruzadas |
| `ReentrantReadWriteLock` | Lecturas muy frecuentes respecto a escrituras |
| STM (Software Transactional Memory) | Conflictos esporádicos y difíciles de anticipar; utilizado en Clojure |
| Modelo de actores (Akka, Erlang) | Sin estado compartido mutable; cada recurso es un actor con cola de mensajes |
| Operaciones lock-free (CAS) | Estructuras de datos de alto rendimiento; complejidad de implementación muy elevada |

Para la escala y naturaleza de este sistema, incorporar cualquiera de estas alternativas introduciría complejidad innecesaria sin beneficio medible.

---

## 3. El Algoritmo 4.3 prescribe 5 hilos para S_salida. ¿Por qué la implementación usa solo 1?

**Contexto:** El Algoritmo 4.3 determina que S_salida (T11) requiere **5 hilos** porque M(P14) puede alcanzar un máximo de 5 tokens simultáneos. Sin embargo, la implementación instancia **1 solo hilo** (H6) para este segmento. ¿No contradice esto el resultado del algoritmo?

### Por qué el algoritmo prescribe 5

El Algoritmo 4.3 calcula el máximo de tokens simultáneos en la plaza de acción PS_i del segmento. Para S_salida, PS = {P14}, y el estado M(P14) = 5 es alcanzable (demostrado por PI-3 y verificado contra todos los invariantes de plaza). El resultado "5 hilos" es matemáticamente correcto para el caso general.

### Por qué 5 hilos no aportan paralelismo real en este caso

El paralelismo real de múltiples hilos en un segmento proviene del patrón **"sleep fuera del lock"**. Para una transición temporal, el ciclo de `fireTransition(t)` es:

```
lock.lock()
→ verificar habilitación y política
→ lock.unlock()               ← lock suelto
→ Thread.sleep(tiempo)         ← N hilos duermen en PARALELO
→ lock.lock()
→ disparar, rastrear, verificar, registrar, señalizar
lock.unlock()
```

Durante el `sleep`, el lock está libre y otros hilos pueden progresar. Si M(P14) = 5 y T11 fuera temporal (por ejemplo, 200 ms), los 5 hilos de S_salida dormirían simultáneamente, completando los 5 retiros en ~200 ms en lugar de ~1000 ms secuenciales.

T11, en cambio, es **inmediata** (no temporal). No hay `sleep`, de modo que el ciclo completo ocurre dentro de la sección crítica:

```
lock.lock()
→ verificar habilitación
→ disparar (P14 -= 1, P0 += 1)
→ rastrear, verificar, registrar, señalizar
lock.unlock()
```

Con N hilos compitiendo por T11, solo uno puede estar dentro del lock en cada instante. Los demás aguardan en la cola del lock. El resultado es ejecución **completamente secuencial** para cualquier N ≥ 1. El throughput de T11 con 5 hilos es idéntico al de 1 hilo.

### ¿Qué cambiaría si T11 fuera temporal?

Todo. Si T11 tuviera, por ejemplo, un `sleep` de 200 ms modelando el procesamiento de salida del cliente:

| Configuración | Tiempo para 5 retiros simultáneos |
|---|---|
| 1 hilo, T11 temporal (200 ms) | ~1000 ms (secuencial) |
| 5 hilos, T11 temporal (200 ms) | ~200 ms (paralelo) |
| 1 hilo, T11 inmediata | ~0 ms (instantáneo) |
| 5 hilos, T11 inmediata | ~0 ms (instantáneo, mismo que 1) |

Para T11 inmediata, la distinción entre 1 y 5 hilos desaparece: no hay tiempo de espera fuera del lock que pueda solaparse.

### Decisión de implementación

Se instancia **1 hilo** para S_salida. La desviación respecto al resultado del Algoritmo 4.3 está justificada: el criterio de "máximo paralelismo" del artículo no se ve afectado porque, para una transición inmediata, N hilos ≡ 1 hilo en términos de progreso del sistema. Instanciar 5 hilos redundantes solo introduciría overhead de creación, context switches y contención en el `AtomicInteger` sin aportar nada.

### Tabla comparativa de los segmentos

| Segmento | Transiciones temporales | ¿Múltiples hilos aportan paralelismo? | Hilos implementados |
|---|---|---|---|
| S_A | T1 | — (máx. 1 hilo por PI-1) | 1 |
| S_inferior | T4 | — (máx. 1 hilo por PI-6) | 1 |
| S_superior | T5 | — (máx. 1 hilo por PI-5) | 1 |
| S_aprobacion | T9, T10 | — (máx. 1 hilo por PI-2) | 1 |
| S_rechazo | T8 | — (máx. 1 hilo por PI-2) | 1 |
| **S_salida** | **T11 (inmediata)** | **No — serializa en el lock** | **1** |

Paradójicamente, los únicos segmentos que se beneficiarían de múltiples hilos (si sus invariantes lo permitieran) son los que tienen transiciones temporales. El único segmento donde el algoritmo prescribe múltiples hilos resulta ser el único con una transición no temporal. El resultado: **6 hilos implementados** sobre los 10 teóricos del Algoritmo 4.3, sin pérdida de paralelismo real.

---

## 4. Una sección crítica garantiza que la operación es "indivisible", pero el SO puede preemptar el hilo en cualquier momento. ¿No se contradice esto?

**Contexto:** Cuando un hilo entra a una sección crítica y toma el lock, se dice que la operación es atómica. Sin embargo, el sistema operativo usa scheduling preemptivo y puede quitarle el CPU a ese hilo en cualquier instante — incluso en la mitad de la sección crítica — dejando el recurso en un estado parcialmente modificado. ¿Cómo puede ser atómica una operación que el SO puede dividir así?

### El SO sí puede preemptar un hilo dentro de una sección crítica

Un sistema operativo moderno con scheduling preemptivo puede suspender cualquier hilo en cualquier momento, sin importar si ese hilo tiene un lock tomado. El hilo queda pausado con el estado de CPU guardado, otro hilo recibe tiempo de procesador, y eventualmente el primero retoma desde exactamente donde quedó — todavía con el lock en su poder.

En ese sentido, la operación sí es "divisible" en términos de tiempo de CPU.

### Por qué la corrección no se rompe

La clave es entender qué significa "atómico" en el contexto de concurrencia. No significa *indivisible a nivel de instrucción de CPU* — significa **ningún otro hilo puede observar un estado intermedio del recurso protegido**.

```
Hilo A entra a la sección crítica, empieza a modificar el marcado...
  → OS preempta a A (marcado parcialmente modificado en memoria)
  → Hilo B intenta adquirir el lock → BLOQUEADO (A todavía lo tiene)
  → Hilo C intenta adquirir el lock → BLOQUEADO
  → OS le devuelve CPU a A
  → A termina la modificación, libera el lock
  → B o C entran y ven el marcado COMPLETO y consistente
```

B y C no pueden entrar mientras A tiene el lock, esté ejecutando activamente o esté preemptado. El estado intermedio existe en memoria pero es invisible para cualquier otro hilo. Eso es la exclusión mutua.

### Los dos tipos de "atomicidad"

| Tipo | Qué garantiza | Ejemplo |
|------|--------------|---------|
| **Atomicidad hardware** | Indivisible a nivel de instrucción de CPU; el OS no puede interrumpir a mitad | `CAS` (Compare-And-Swap), lectura/escritura alineada de 32/64 bits |
| **Atomicidad por mutex** | Ningún otro hilo observa estado intermedio; el OS sí puede preemptar | `synchronized`, `ReentrantLock`, toda sección crítica clásica |

`AtomicInteger.getAndIncrement()` usa `CAS` internamente — eso sí es indivisible a nivel hardware, por eso no necesita lock. El disparo en `RedPetri`, que modifica un array de 15 posiciones, usa un lock porque son múltiples operaciones sobre múltiples variables: ninguna instrucción de CPU puede hacerlo en un solo paso, pero el lock garantiza que ningún otro hilo accede al marcado mientras tanto.

### ¿En qué estado queda el hilo preemptado? ¿Sigue "corriendo" para sí mismo?

Cuando el scheduler preempta un hilo, lo mueve de **RUNNING** a **READY** (denominado `RUNNABLE` en Java). No está bloqueado — no necesita que ocurra ningún evento para continuar, simplemente no tiene CPU asignada en este instante. Cuando el scheduler lo elige nuevamente, vuelve a RUNNING y retoma desde la instrucción exacta donde quedó.

```
              preemption
    RUNNING ──────────────→ READY
       ↑                      │
       └──────────────────────┘
         scheduler le asigna CPU de nuevo

    RUNNING ──────────────→ BLOCKED / WAITING
               lock, sleep,     │
               await(), I/O     └→ necesita que ocurra algo para volver a READY
```

El hilo no tiene ninguna percepción de haber sido preemptado. No existe ninguna instrucción de código que le permita detectarlo: su program counter, registros y stack se guardan y restauran de forma completamente transparente. Desde la perspectiva del código del hilo, la ejecución es continua — el tiempo simplemente "no existió". Es la ilusión de procesador dedicado que provee el sistema operativo.

Esto es lo opuesto de lo que ocurre cuando un hilo llama `condiciones[t].await()` en el Monitor: ahí el hilo **activamente** libera el lock y se registra como WAITING, sabiendo que necesita ser despertado por un `signalAll()`. La preempción es involuntaria e imperceptible; el bloqueo es voluntario y el hilo tiene plena conciencia de él.

Vale notar que Java agrupa READY y RUNNING bajo un único estado `RUNNABLE` en `Thread.State`. Es intencional: Java es independiente de la plataforma y no puede garantizar que "tengo el CPU en este instante exacto" sea observable de forma portable. Desde el modelo de la JVM, RUNNABLE simplemente significa "este hilo puede ejecutar".

### El único escenario donde sí se rompe la garantía

Si algún hilo accediera al recurso protegido **sin pasar por el lock**, podría ver el estado intermedio aunque A esté en la sección crítica y no haya liberado el lock. Por eso en el Monitor el invariante más importante es que **todo acceso al marcado pase por `fireTransition`** — si algún hilo leyera `red.getMarcado()` directamente sin sincronización, podría obtener un marcado corrupto mientras A está preemptado a mitad de un disparo.

---
