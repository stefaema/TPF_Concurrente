# Análisis de Hilos — Aplicación de los Algoritmos del artículo

> **Referencia:** Ventre & Micolini, *"Algoritmos para determinar cantidad y responsabilidad de hilos en sistemas embebidos modelados con Redes de Petri S³PR"*, FCEFyN-UNC.

---

## Base: T-invariantes

Los cuatro T-invariantes (IT) de la red son los ciclos elementales que devuelven el sistema al marcado inicial. Cada uno representa un camino completo de un cliente.

| IT | Transiciones | Camino |
|---|---|---|
| I1 | T0, T1, T3, T4, T7, T8, T11 | Agente inferior → Rechazado → Cancelación |
| I2 | T0, T1, T3, T4, T6, T9, T10, T11 | Agente inferior → Aprobado → Pago |
| I3 | T0, T1, T2, T5, T7, T8, T11 | Agente superior → Rechazado → Cancelación |
| I4 | T0, T1, T2, T5, T6, T9, T10, T11 | Agente superior → Aprobado → Pago |

Arcos de cada transición (necesarios para los tres algoritmos):

| Transición | Entradas | Salidas |
|---|---|---|
| T0 | P0, P1, P4 | P2 |
| T1 | P2 | P1, P3 |
| T2 | P3, P6 | P4, P5 |
| T3 | P3, P7 | P4, P8 |
| T4 | P8 | P7, P9 |
| T5 | P5 | P6, P9 |
| T6 | P9, P10 | P11 |
| T7 | P9, P10 | P12 |
| T8 | P12 | P10, P14 |
| T9 | P11 | P13 |
| T10 | P13 | P10, P14 |
| T11 | P14 | P0 |

---

## Algoritmo 4.1 — Hilos máximos activos simultáneos

Este algoritmo determina cuántos hilos pueden estar activos **en el mismo instante** como máximo. Se basa en encontrar el marcado máximo del conjunto de plazas de acción.

### Paso 1: Clasificación de plazas

El artículo distingue tres tipos de plazas. Las **idle** son el punto de partida del ciclo (buffer de entrada). Las **recursos** son plazas 1-acotadas que modelan acceso exclusivo. Las **de acción** (PA) representan estados intermedios que ocupa un cliente mientras es procesado.

| Tipo | Plazas | Criterio |
|---|---|---|
| Idle | P0 | Buffer de clientes; inicia cada ciclo |
| Recursos | P1, P4, P6, P7, P10 | Marcado inicial ≥ 1; representan capacidad/disponibilidad de recursos |
| Acción (PA) | P2, P3, P5, P8, P9, P11, P12, P13, P14 | Estados del cliente en tránsito |

### Paso 2: PI_i — plazas tocadas por cada IT

Para cada IT, PI_i es la unión de **todas** las plazas de entrada y salida de sus transiciones (ec. 4 del artículo: PI_i = ∪_{t∈IT} •t ∪ ∪_{t∈IT} t•).

| IT | PI_i |
|---|---|
| I1 | P0, P1, P2, P3, P4, P7, P8, P9, P10, P12, P14 |
| I2 | P0, P1, P2, P3, P4, P7, P8, P9, P10, P11, P13, P14 |
| I3 | P0, P1, P2, P3, P4, P5, P6, P9, P10, P12, P14 |
| I4 | P0, P1, P2, P3, P4, P5, P6, P9, P10, P11, P13, P14 |

### Paso 3: PA_i — plazas de acción por IT

PA_i = PI_i − {idle} − {recursos} (ec. 5 del artículo). Se eliminan P0 (idle) y P1, P4, P6, P7, P10 (recursos).

| IT | PA_i |
|---|---|
| I1 | P2, P3, P8, P9, P12, P14 |
| I2 | P2, P3, P8, P9, P11, P13, P14 |
| I3 | P2, P3, P5, P9, P12, P14 |
| I4 | P2, P3, P5, P9, P11, P13, P14 |

**PA combinado** = PA₁ ∪ PA₂ ∪ PA₃ ∪ PA₄ = {P2, P3, P5, P8, P9, P11, P12, P13, P14}

### Pasos 4-5: Marcado máximo del conjunto MA

El artículo pide obtener MA (el conjunto de todos los marcados posibles de las plazas PA) del árbol de alcanzabilidad, y luego buscar la suma máxima de marcas.

Sea suma(PA) = M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14).

Del P-invariante PI-3:

```
M(P0) + M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14) = 5
```

Por lo tanto: **suma(PA) = 5 − M(P0)**. La suma es máxima cuando M(P0) = 0, es decir cuando los 5 clientes están activos dentro del sistema simultáneamente.

**Estado de ejemplo que alcanza suma(PA) = 5:**

| P2 | P3 | P5 | P8 | P9 | P11 | P12 | P13 | P14 | SUMA |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 0 | 1 | 1 | 2 | 0 | 0 | 0 | 0 | **5** |

**Secuencia de disparo que alcanza este estado** (marcado inicial: P0=5, P1=1, P4=5, P6=1, P7=1, P10=1):

```
T0        → P0=4, P1=0, P4=4, P2=1
T1        → P2=0, P1=1, P3=1
T2        → P3=0, P6=0, P4=5, P5=1      (cliente 1 toma agente superior)
T5        → P5=0, P6=1, P9=1            (cliente 1 termina con agente superior)
T0        → P0=3, P1=0, P4=4, P2=1
T1        → P2=0, P1=1, P3=1
T3        → P3=0, P7=0, P4=5, P8=1      (cliente 2 toma agente inferior)
T4        → P8=0, P7=1, P9=2            (cliente 2 termina con agente inferior)
T0        → P0=2, P1=0, P4=4, P2=1
T1        → P2=0, P1=1, P3=1
T2        → P3=0, P6=0, P4=5, P5=1      (cliente 3 toma agente superior)
T0        → P0=1, P1=0, P4=4, P2=1      (P1 consumido por T0 cliente 4)
T1        → P2=0, P1=1, P3=1
T3        → P3=0, P7=0, P4=5, P8=1      (cliente 4 toma agente inferior)
T0        → P0=0, P1=0, P4=4, P2=1      (cliente 5 ingresa)
```

Estado final: P2=1 (cliente 5 en P2), P5=1 (cliente 3 con agente superior), P8=1 (cliente 4 con agente inferior), P9=2 (clientes 1 y 2 esperando aprobación). Suma = 5. ✓

> **Resultado Algoritmo 4.1: máximo de hilos activos simultáneos = 5.**

---

## Algoritmo 4.2 — Segmentos de ejecución y responsabilidad

El artículo define tres casos que fraccionan la responsabilidad de ejecución de los IT:

- **Caso 1 (IT lineal):** sin conflictos ni joins → un único segmento para todo el IT.
- **Caso 2 (fork/conflicto):** se generan un segmento antes del fork y un segmento por cada rama posterior.
- **Caso 3 (join):** se generan un segmento por cada IT hasta el join, más un único segmento extra posterior al join (compartido por todos los IT que confluyen).

En nuestra red los cuatro IT comparten transiciones y presentan la siguiente estructura anidada de forks y joins:

```
IT1, IT2, IT3, IT4 comparten T0, T1
           │
      [FORK en P3]       ← Caso 2: conflicto estructural T2 vs T3
      (T2 y T3 compiten por el token de P3)
      /            \
 IT1, IT2         IT3, IT4
 T3, T4           T2, T5
      \            /
      [JOIN en P9]        ← Caso 3: T4 y T5 producen tokens en P9
           │
   [FORK en P9+P10]      ← Caso 2: conflicto estructural T6 vs T7
   (T6 y T7 compiten por P9 y P10 simultáneamente)
      /            \
 IT2, IT4         IT1, IT3
 T6, T9, T10      T7, T8
      \            /
      [JOIN en P14]       ← Caso 3: T8 y T10 producen tokens en P14
           │
          T11
```

### Aplicación caso a caso

**Caso 2 — Fork en P3 (Conflicto 1: T2 vs T3)**

T2 y T3 comparten P3 como plaza de entrada: cuando P3 tiene exactamente 1 token, el disparo de T2 desensibiliza T3 y viceversa. Esto es un conflicto estructural (fork).

- Transiciones comunes previas al fork: T0, T1 → **Segmento S_A**
- Rama IT1, IT2 (agente inferior): T3, T4 → **Segmento S_inferior**
- Rama IT3, IT4 (agente superior): T2, T5 → **Segmento S_superior**

**Caso 3 — Join en P9 (T4 y T5 confluyen)**

T4 (de S_inferior) produce P9 y T5 (de S_superior) produce P9: ambas ramas depositan tokens en la misma plaza. Esto es un join.

El Caso 3 requiere un segmento extra posterior al join con las transiciones compartidas a partir de P9. Entre P9 y el siguiente fork (T6 vs T7) **no existe ninguna transición intermedia**: T6 y T7 consumen P9 directamente. Por lo tanto, el segmento extra posterior al join está **vacío** y no genera ningún hilo adicional.

**Caso 2 — Fork en P9+P10 (Conflicto 2: T6 vs T7)**

T6 y T7 comparten P9 y P10 como plazas de entrada: cuando P9≥1 y P10=1, el disparo de T6 desensibiliza T7 y viceversa. Conflicto estructural.

- No hay transiciones pre-fork (el segmento extra post-join-P9 es vacío).
- Rama IT2, IT4 (aprobación): T6, T9, T10 → **Segmento S_aprobacion**
- Rama IT1, IT3 (rechazo): T7, T8 → **Segmento S_rechazo**

**Caso 3 — Join en P14 (T8 y T10 confluyen)**

T8 (de S_rechazo) produce P14 y T10 (de S_aprobacion) produce P14: ambas ramas depositan tokens en P14.

- Segmento extra posterior al join: T11 → **Segmento S_salida**

### Tabla de segmentos

| Segmento | Transiciones | IT que cubre | Rol |
|---|---|---|---|
| **S_A** | T0, T1 | I1, I2, I3, I4 | Ingreso de clientes al sistema |
| **S_inferior** | T3, T4 | I1, I2 | Atención por agente de reservas inferior |
| **S_superior** | T2, T5 | I3, I4 | Atención por agente de reservas superior |
| **S_aprobacion** | T6, T9, T10 | I2, I4 | Aprobación → Confirmación → Pago |
| **S_rechazo** | T7, T8 | I1, I3 | Rechazo → Cancelación |
| **S_salida** | T11 | I1, I2, I3, I4 | Retiro del cliente / devolución al buffer |

---

## Algoritmo 4.3 — Hilos máximos por segmento

Para cada segmento se determina PS_i (las plazas de acción del segmento) y luego se busca el marcado máximo de esas plazas en el árbol de alcanzabilidad. Ese máximo es la cantidad de hilos requeridos por el segmento.

### Definición de PS_i

El artículo define PS_i como las **plazas de acción** asociadas al segmento. Para un segmento con múltiples transiciones, PS_i son las plazas de acción internas (producidas por transiciones no finales del segmento y consumidas dentro del mismo segmento). Para un segmento con una única transición, PS_i es la plaza de acción de entrada a esa transición (la "cola" de clientes esperando para entrar al segmento).

Esta definición es consistente con el ejemplo del artículo: para el segmento SD = {T6} de la red de Huang, PS_D = {P5}, donde P5 es la plaza de entrada a T6 (producida por las ramas anteriores al join y consumida por T6).

### Cálculo de PS_i y máximos

**S_A = {T0, T1}**

- T0 produce P2 (acción); T1 consume P2 y produce P3 (acción).
- P2 es interno al segmento: producido por T0 (no-final) y consumido por T1 (final) dentro de S_A.
- P3 es la plaza de salida de S_A (alimenta el fork T2/T3); pertenece a los segmentos siguientes.
- **PS_A = {P2}**

Cota formal: PI-1 establece M(P1)+M(P2)=1. Como P1 es un recurso 1-acotado, M(P2) ≤ 1 en todo estado alcanzable.

**Max(M(P2)) = 1 → 1 hilo para S_A.**

---

**S_inferior = {T3, T4}**

- T3 produce P8 (acción); T4 consume P8 y produce P9 (acción).
- P8 es interno al segmento; P9 es la plaza de salida hacia el join/fork siguiente.
- **PS_inferior = {P8}**

Cota formal: PI-6 establece M(P7)+M(P8)=1. P7 es un recurso 1-acotado, por lo que M(P8) ≤ 1.

**Max(M(P8)) = 1 → 1 hilo para S_inferior.**

---

**S_superior = {T2, T5}**

- T2 produce P5 (acción); T5 consume P5 y produce P9 (acción).
- P5 es interno al segmento; P9 es la plaza de salida.
- **PS_superior = {P5}**

Cota formal: PI-5 establece M(P5)+M(P6)=1. P6 es un recurso 1-acotado, por lo que M(P5) ≤ 1.

**Max(M(P5)) = 1 → 1 hilo para S_superior.**

---

**S_aprobacion = {T6, T9, T10}**

- T6 produce P11 (acción); T9 consume P11 y produce P13 (acción); T10 consume P13 y produce P14 (acción).
- P11 y P13 son internos al segmento; P14 es la plaza de salida.
- **PS_aprobacion = {P11, P13}**

Cota formal: PI-2 establece M(P10)+M(P11)+M(P12)+M(P13)=1. Como la suma de los cuatro términos es siempre 1, M(P11)+M(P13) ≤ 1 (el resto está distribuido en P10 o P12).

**Max(M(P11)+M(P13)) = 1 → 1 hilo para S_aprobacion.**

---

**S_rechazo = {T7, T8}**

- T7 produce P12 (acción); T8 consume P12 y produce P14 (acción).
- P12 es interno al segmento; P14 es la plaza de salida.
- **PS_rechazo = {P12}**

Cota formal: PI-2 establece M(P10)+M(P11)+M(P12)+M(P13)=1, por lo que M(P12) ≤ 1.

**Max(M(P12)) = 1 → 1 hilo para S_rechazo.**

---

**S_salida = {T11}**

S_salida tiene una única transición. Siguiendo la definición del artículo para segmentos de una sola transición (análogo al segmento SD = {T6} en el ejemplo de la red de Huang), PS_i es la **plaza de entrada** a esa transición.

- T11 consume P14 (acción) y produce P0 (idle).
- **PS_salida = {P14}**

**Análisis formal del máximo de M(P14):**

P14 recibe tokens de T8 (S_rechazo: P12 → P10, P14) y de T10 (S_aprobacion: P13 → P10, P14). Cada disparo de T8 o T10 restaura P10 (recurso compartido). Esto significa que tras completar un ciclo de aprobación/rechazo, el recurso P10 queda libre inmediatamente para que otro cliente entre al segmento de aprobación, mientras el token del cliente anterior permanece en P14 esperando T11.

Por lo tanto, los tokens pueden acumularse en P14 si T11 no dispara entre ciclos consecutivos de aprobación/rechazo. El árbol de alcanzabilidad confirma que M(P14) puede llegar a 5:

Del P-invariante PI-3: M(P0)+M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14) = 5.

El estado M(P14)=5 implica M(P0)=M(P2)=M(P3)=...=M(P13)=0. Verificación contra todos los invariantes:

| Invariante | Con M(P14)=5 | Cumple |
|---|---|---|
| PI-1: M(P1)+M(P2)=1 | M(P2)=0 → M(P1)=1 ✓ | ✓ |
| PI-2: M(P10)+M(P11)+M(P12)+M(P13)=1 | todos cero → M(P10)=1 ✓ | ✓ |
| PI-3: suma clientes = 5 | 0+0+0+0+0+0+0+0+0+5 = 5 ✓ | ✓ |
| PI-4: M(P2)+M(P3)+M(P4)=5 | 0+0+5 → M(P4)=5 ✓ | ✓ |
| PI-5: M(P5)+M(P6)=1 | M(P5)=0 → M(P6)=1 ✓ | ✓ |
| PI-6: M(P7)+M(P8)=1 | M(P8)=0 → M(P7)=1 ✓ | ✓ |

El estado es alcanzable (todos los invariantes satisfechos). Una secuencia que lo alcanza: los 5 clientes pasan secuencialmente por el ciclo completo (entran, van a un agente, pasan por aprobación/rechazo, llegan a P14) sin que T11 dispare en ningún momento. Dado que P10 se restaura tras cada T8 o T10, el siguiente cliente puede iniciar su ciclo de aprobación inmediatamente.

**Max(M(P14)) = 5 → el Algoritmo 4.3 prescribe 5 hilos para S_salida.**

Esto es coherente con la consigna (referencia al artículo): *"si el IT presenta un join con otro IT, luego del join debe haber tantos hilos como tokens simultáneos en la plaza."* La plaza del join es P14, y el máximo de tokens simultáneos en ella es 5.

> **Nota de implementación — T11 es inmediata:** el resultado del Algoritmo 4.3 es matemáticamente correcto (5 hilos), pero solo produce paralelismo real cuando las transiciones del segmento son **temporales**. En ese caso, cada hilo suelta el lock durante su `Thread.sleep()`, permitiendo que múltiples hilos ejecuten sus actividades en paralelo. T11 es una transición **inmediata**: no tiene sleep fuera del lock, de modo que todo el ciclo de `fireTransition(11)` — verificar habilitación, disparar, rastrear, señalizar — ocurre íntegramente dentro de la sección crítica. Con N hilos compitiendo por T11, solo uno puede estar dentro del lock en cada instante; los demás aguardan. El throughput resultante es idéntico al de 1 solo hilo. Por esta razón, la implementación instancia **1 único hilo** para S_salida en lugar de los 5 prescritos por el algoritmo. La desviación está justificada: el criterio de "máximo paralelismo" del artículo no se ve afectado porque, para una transición inmediata, N hilos ≡ 1 hilo en términos de progreso del sistema. Ver preguntas-respuestas.md §3 para el análisis completo.

---

### Tabla resumen

| Segmento | PS_i | Restricción formal | Max(MS_i) | Hilos (Alg. 4.3) | Hilos implementados |
|---|---|---|---|---|---|
| S_A | {P2} | PI-1: M(P1)+M(P2)=1, P1 recurso 1-acotado | 1 | 1 | 1 |
| S_inferior | {P8} | PI-6: M(P7)+M(P8)=1, P7 recurso 1-acotado | 1 | 1 | 1 |
| S_superior | {P5} | PI-5: M(P5)+M(P6)=1, P6 recurso 1-acotado | 1 | 1 | 1 |
| S_aprobacion | {P11, P13} | PI-2: M(P10)+M(P11)+M(P12)+M(P13)=1 | 1 | 1 | 1 |
| S_rechazo | {P12} | PI-2: M(P10)+M(P11)+M(P12)+M(P13)=1 | 1 | 1 | 1 |
| S_salida | {P14} | PI-3: M(P14) ≤ 5; estado M(P14)=5 alcanzable | **5** | **5** | **1** (†) |

> (†) T11 es inmediata: múltiples hilos se serializarían igual que 1 solo. El Algoritmo 4.3 prescribe 5; la implementación usa 1 por equivalencia funcional.

> **Resultado Algoritmo 4.3: 1+1+1+1+1+5 = 10 hilos teóricos. Implementación: 6 hilos (la reducción de S_salida de 5 a 1 no altera el paralelismo real del sistema).**

---

## Resultado: 6 hilos (implementados) / 10 hilos (teórico Alg. 4.3)

| Hilo | Segmento | Transiciones | Rol |
|---|---|---|---|
| **H1** | S_A | T0, T1 | Ingreso de clientes al sistema |
| **H2** | S_inferior | T3, T4 | Atención por agente de reservas inferior |
| **H3** | S_superior | T2, T5 | Atención por agente de reservas superior |
| **H4** | S_aprobacion | T6, T9, T10 | Aprobación → Confirmación → Pago |
| **H5** | S_rechazo | T7, T8 | Rechazo → Cancelación |
| **H6** | S_salida | T11 | Retiro del cliente y devolución al buffer (único hilo; T11 es inmediata) |

---

## Diagrama de responsabilidades

```
                    ┌──────────────────────────────────────┐
                    │        H1 (S_A): T0, T1              │
                    │       (ingreso de clientes)           │
                    └───────────────┬──────────────────────┘
                                    │ P3
                           [FORK — P3]
                      (conflicto: T2 vs T3)
                      (resuelto por la Política)
                    ┌───────────────┴──────────────────────┐
                    │                                      │
       ┌────────────▼──────────────┐     ┌────────────────▼──────────────┐
       │  H2 (S_inferior): T3, T4 │     │  H3 (S_superior): T2, T5     │
       │   PS = {P8}               │     │   PS = {P5}                   │
       │   max = 1 hilo            │     │   max = 1 hilo                │
       └────────────┬──────────────┘     └────────────────┬──────────────┘
                    │ P9                                   │ P9
                    └─────────────────┬────────────────────┘
                                      │
                              [JOIN — P9]
                    (T4 y T5 depositan tokens en P9)
                    (segmento extra post-join: vacío → 0 hilos)
                                      │
                           [FORK — P9 + P10]
                      (conflicto: T6 vs T7)
                      (resuelto por la Política)
                    ┌─────────────────┴────────────────────┐
                    │                                      │
   ┌────────────────▼──────────────────┐  ┌───────────────▼──────────────────┐
   │  H4 (S_aprobacion): T6, T9, T10  │  │  H5 (S_rechazo): T7, T8         │
   │   PS = {P11, P13}                 │  │   PS = {P12}                     │
   │   max = 1 hilo                    │  │   max = 1 hilo                   │
   └────────────────┬──────────────────┘  └───────────────┬──────────────────┘
                    │ P14                                  │ P14
                    └─────────────────┬────────────────────┘
                                      │
                              [JOIN — P14]
                  (T10 y T8 depositan tokens en P14)
                  M(P14) máximo = 5 (demostrado por PI-3
                  y árbol de alcanzabilidad)
                                      │
                    ┌─────────────────▼────────────────────┐
                    │    H6 (S_salida): T11                 │
                    │    PS = {P14},  Alg.4.3 = 5 hilos     │
                    │    T11 INMEDIATA → serializa en lock  │
                    │    Implementación: 1 hilo suficiente  │
                    └──────────────────────────────────────┘
```

---

## Puntos de conflicto y políticas

| Plaza de conflicto | Transiciones en conflicto | Política aplicada |
|---|---|---|
| P3 | T2 vs T3 | Balanceada (50%/50%) o Priorizada (75% agente superior) |
| P9 + P10 | T6 vs T7 | Balanceada (50%/50%) o Priorizada (80% confirmaciones) |

El Monitor resuelve cada conflicto consultando al objeto Política **dentro de la sección crítica** de `fireTransition`. Solo el Monitor accede al marcado; los hilos invocan `fireTransition(t)` y bloquean si la transición no está sensibilizada o si la política no la autoriza.

---

## Resumen de los tres algoritmos

| Algoritmo | Resultado teórico | Resultado implementado | Explicación |
|---|---|---|---|
| **4.1** — Hilos activos simultáneos máximos | **5** | **5** | suma(PA) = 5 − M(P0); máximo cuando M(P0)=0 |
| **4.2** — Segmentos de ejecución | **6 segmentos** | **6 segmentos** | S_A, S_inferior, S_superior, S_aprobacion, S_rechazo, S_salida |
| **4.3** — Hilos máximos por segmento | **10 hilos** (1+1+1+1+1+**5**) | **6 hilos** (1+1+1+1+1+**1**) | S_salida reducida de 5 a 1: T11 es inmediata, no temporal |

**Diferencia entre 5 (Alg. 4.1) y 10 (Alg. 4.3 teórico):**

El Algoritmo 4.1 mide cuántos hilos pueden estar *activos* en el mismo instante. El máximo es 5 porque siempre hay al menos un hilo bloqueado: mientras 5 clientes ocupan plazas de acción activas, el hilo de S_salida estará *esperando* el token de P14 o habrá ya terminado.

El Algoritmo 4.3 mide cuántos hilos hay que *crear* para cubrir el paralelismo máximo del sistema. Para S_salida, el algoritmo prescribe 5 porque P14 puede acumular 5 tokens simultáneamente. Sin embargo, la ventaja de múltiples hilos solo se concreta cuando la transición es **temporal**: el hilo suelta el lock durante el sleep, permitiendo que otros hilos progresen en paralelo. T11 es inmediata (sin sleep), por lo que todo el disparo ocurre dentro del lock y los hilos se serializan inevitablemente. El resultado práctico es que 1 hilo y 5 hilos son equivalentes para T11: la implementación usa 1.

**Diferencia entre 10 (teórico) y 6 (implementado):**

La reducción de 10 a 6 aplica exclusivamente a S_salida. Todos los demás segmentos conservan el número de hilos prescrito por el algoritmo. El paralelismo real del sistema no se ve afectado: la cota de 5 hilos activos simultáneos (Alg. 4.1) se sigue cumpliendo, ya que los 5 clientes que pueden estar en plazas de acción simultáneamente corresponden a los segmentos H1–H5, no a H6.
