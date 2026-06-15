# Análisis de la Red de Petri — Sistema de Agencia de Viajes

## Red modelada

La red de Petri modela el flujo completo de un cliente en una agencia de viajes, desde su ingreso hasta su retiro. Fue construida y analizada con la herramienta PIPE (Platform Independent Petri net Editor).

![Red de Petri](../img/img.png)

---

## Propiedades de la red

El análisis del espacio de estados arrojó los siguientes resultados:

![State Space Analysis](../img/Captura%20de%20pantalla%20de%202026-05-11%2018-03-52.png)

### Bounded (Acotada) — `true`

Una red es **acotada** si el número de tokens en cada plaza nunca crece indefinidamente, independientemente de cuántas veces se disparen las transiciones. En esta red, el resultado `true` se justifica por la conservación de tokens:

- Hay exactamente **5 tokens de cliente** que circulan por el sistema (nunca se crean ni se destruyen).
- Los **recursos** (agentes P1, P6, P7, P10) tienen marcado inicial de 1 token y se conservan mediante invariantes de plaza.
- Ninguna plaza puede acumular tokens de forma ilimitada.

Esto garantiza que la implementación Java no puede tener condiciones de "desbordamiento" de estados por diseño de la red.

### Safe (Segura) — `false`

Una red es **segura** si además de acotada es **1-acotada**: cada plaza tiene como máximo 1 token en cualquier instante. Esta red **no es segura** porque varias plazas pueden contener más de un token simultáneamente:

- **P0** (buffer de clientes): hasta 5 tokens.
- **P3** (sala de espera): hasta 5 tokens (un token por cada cliente esperando).
- **P4** (capacidad de sala): hasta 5 tokens.
- **P9** (cola de aprobación): hasta 5 tokens.

Esto es **intencional** — el sistema está diseñado para atender múltiples clientes en paralelo. Las plazas de recursos sí son 1-acotadas.

### Deadlock — `false`

La red **no presenta deadlock**. Nunca se alcanza un estado donde ninguna transición esté habilitada. Esto se garantiza porque los cuatro T-invariantes forman ciclos cerrados: la transición T11 devuelve siempre un token a P0, permitiendo que el sistema continúe indefinidamente.

---

## Invariantes de plaza (P-invariantes)

Los P-invariantes son ecuaciones que se cumplen en **todo estado alcanzable** de la red. Representan leyes de conservación: ciertas sumas de tokens permanecen constantes sin importar qué transiciones se hayan disparado.

![P-Invariants Matrix](../img/Captura%20de%20pantalla%20de%202026-05-11%2018-04-45.png)

![P-Invariant Equations](../img/Captura%20de%20pantalla%20de%202026-05-11%2018-04-59.png)

### PI-1: M(P1) + M(P2) = 1

**Conservación del recurso de entrada.**
La plaza P1 representa el recurso de control de ingreso (el acceso a la agencia) y P2 el estado de un cliente que está atravesando ese ingreso. En todo momento, ese recurso está en exactamente uno de dos estados: libre (1 token en P1, 0 en P2) o siendo usado por un cliente (0 en P1, 1 en P2). Solo puede ingresar un cliente a la vez.

### PI-2: M(P10) + M(P11) + M(P12) + M(P13) = 1

**Conservación del agente aprobador.**
P10 es el recurso del agente que aprueba o rechaza reservas. Las plazas P11 (confirmación), P12 (cancelación) y P13 (pago) representan las etapas posteriores a la decisión. El invariante garantiza que el agente aprobador siempre está procesando exactamente un cliente: libre en P10, o ya tomó una decisión y el cliente avanza por P11, P12 o P13.

### PI-3: M(P0) + M(P2) + M(P3) + M(P5) + M(P8) + M(P9) + M(P11) + M(P12) + M(P13) + M(P14) = 5

**Conservación de los 5 clientes.**
Los 5 tokens que representan clientes están siempre distribuidos en alguna de las plazas del flujo principal. Nunca se crean ni se destruyen: un cliente que sale del sistema (T11) repone el token en P0, habilitando la entrada de un nuevo cliente. Este invariante verifica que el sistema no "pierde" clientes.

### PI-4: M(P2) + M(P3) + M(P4) = 5

**Conservación de la capacidad de la sala de espera.**
P4 representa los lugares disponibles en la sala de espera, P3 los clientes en la sala, y P2 los clientes en proceso de ingreso. La suma siempre es 5: cuando un cliente entra (consume P4 y eventualmente ocupa P3), y cuando sale hacia un agente (libera P4), la capacidad total se conserva. Esto modela un sistema de sala con aforo máximo de 5 personas.

### PI-5: M(P5) + M(P6) = 1

**Conservación del agente de reservas superior.**
P6 es el agente superior disponible y P5 es el estado "cliente siendo atendido por el agente superior". El invariante garantiza que el agente superior siempre está en uno de dos estados: libre (P6=1, P5=0) o atendiendo exactamente a un cliente (P6=0, P5=1).

### PI-6: M(P7) + M(P8) = 1

**Conservación del agente de reservas inferior.**
Análogo al anterior pero para el agente inferior. P7 es el agente inferior disponible y P8 el estado "cliente siendo atendido por el agente inferior". En todo momento el agente está libre o atendiendo exactamente a un cliente.

---

## Invariantes de transición (T-invariantes)

Los T-invariantes son los **ciclos elementales** de la red: secuencias mínimas de disparos que devuelven el sistema al marcado inicial. Cada T-invariante representa un camino completo que puede recorrer un cliente a través del sistema.

PIPE encontró **4 T-invariantes**, que se corresponden con las 4 combinaciones posibles de decisiones en los dos puntos de conflicto de la red (elección de agente y decisión de aprobación):

![T-Invariants Matrix](../img/Captura%20de%20pantalla%20de%202026-05-11%2018-04-31.png)

### I1: T0 → T1 → T3 → T4 → T7 → T8 → T11

**Agente inferior — Reserva rechazada — Cancelación.**

El cliente ingresa (T0, T1), es atendido por el **agente de reservas inferior** (T3, T4), la reserva es **rechazada** por el agente aprobador (T7), se procesa la **cancelación** (T8) y el cliente se retira (T11).

### I2: T0 → T1 → T3 → T4 → T6 → T9 → T10 → T11

**Agente inferior — Reserva aprobada — Confirmación y pago.**

El cliente ingresa (T0, T1), es atendido por el **agente de reservas inferior** (T3, T4), la reserva es **aprobada** (T6), se procesa la **confirmación** (T9) y el **pago** (T10), y el cliente se retira (T11).

### I3: T0 → T1 → T2 → T5 → T7 → T8 → T11

**Agente superior — Reserva rechazada — Cancelación.**

El cliente ingresa (T0, T1), es atendido por el **agente de reservas superior** (T2, T5), la reserva es **rechazada** por el agente aprobador (T7), se procesa la **cancelación** (T8) y el cliente se retira (T11).

### I4: T0 → T1 → T2 → T5 → T6 → T9 → T10 → T11

**Agente superior — Reserva aprobada — Confirmación y pago.**

El cliente ingresa (T0, T1), es atendido por el **agente de reservas superior** (T2, T5), la reserva es **aprobada** (T6), se procesa la **confirmación** (T9) y el **pago** (T10), y el cliente se retira (T11).

---

---

## Representación matricial de la red

Las matrices Pre y Post codifican los arcos de la red y son la base de la implementación de `RedPetri`. Las filas corresponden a las transiciones T0–T11 y las columnas a las plazas P0–P14.

**Pre\[t]\[p]** — tokens que la transición T consume de la plaza P:

|     | P0 | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 | P11 | P12 | P13 | P14 |
|-----|----|----|----|----|----|----|----|----|----|----|-----|-----|-----|-----|-----|
| **T0**  | 1  | 1  | 0  | 0  | 1  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T1**  | 0  | 0  | 1  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T2**  | 0  | 0  | 0  | 1  | 0  | 0  | 1  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T3**  | 0  | 0  | 0  | 1  | 0  | 0  | 0  | 1  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T4**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T5**  | 0  | 0  | 0  | 0  | 0  | 1  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T6**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1  | 1   | 0   | 0   | 0   | 0   |
| **T7**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1  | 1   | 0   | 0   | 0   | 0   |
| **T8**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 1   | 0   | 0   |
| **T9**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 1   | 0   | 0   | 0   |
| **T10** | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 1   | 0   |
| **T11** | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 1   |

**Post\[t]\[p]** — tokens que la transición T produce en la plaza P:

|     | P0 | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 | P11 | P12 | P13 | P14 |
|-----|----|----|----|----|----|----|----|----|----|----|-----|-----|-----|-----|-----|
| **T0**  | 0  | 0  | 1  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T1**  | 0  | 1  | 0  | 1  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T2**  | 0  | 0  | 0  | 0  | 1  | 1  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T3**  | 0  | 0  | 0  | 0  | 1  | 0  | 0  | 0  | 1  | 0  | 0   | 0   | 0   | 0   | 0   |
| **T4**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1  | 0  | 1  | 0   | 0   | 0   | 0   | 0   |
| **T5**  | 0  | 0  | 0  | 0  | 0  | 0  | 1  | 0  | 0  | 1  | 0   | 0   | 0   | 0   | 0   |
| **T6**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 1   | 0   | 0   | 0   |
| **T7**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 1   | 0   | 0   |
| **T8**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1   | 0   | 0   | 0   | 1   |
| **T9**  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 1   | 0   |
| **T10** | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 1   | 0   | 0   | 0   | 1   |
| **T11** | 1  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0  | 0   | 0   | 0   | 0   | 0   |

**Marcado inicial M0:**

| P0 | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 | P11 | P12 | P13 | P14 |
|----|----|----|----|----|----|----|----|----|----|----|-----|-----|-----|-----|
| 5  | 1  | 0  | 0  | 5  | 0  | 1  | 1  | 0  | 0  | 1  | 0   | 0   | 0   | 0   |

Verificación contra los 6 P-invariantes en M0:

| Invariante | Cálculo | Resultado |
|---|---|---|
| PI-1: M(P1)+M(P2) | 1+0 | = 1 ✓ |
| PI-2: M(P10)+M(P11)+M(P12)+M(P13) | 1+0+0+0 | = 1 ✓ |
| PI-3: M(P0)+M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14) | 5+0+0+0+0+0+0+0+0+0 | = 5 ✓ |
| PI-4: M(P2)+M(P3)+M(P4) | 0+0+5 | = 5 ✓ |
| PI-5: M(P5)+M(P6) | 0+1 | = 1 ✓ |
| PI-6: M(P7)+M(P8) | 1+0 | = 1 ✓ |

---

### Relación entre T-invariantes y las políticas requeridas

Los dos **puntos de conflicto** de la red generan los 4 T-invariantes:

| Conflicto | Transiciones | Afecta |
|---|---|---|
| Elección de agente | T2 vs T3 (compiten por P3) | I1/I2 vs I3/I4 |
| Decisión de aprobación | T6 vs T7 (compiten por P9 y P10) | I1/I3 vs I2/I4 |

La **política balanceada** busca que I1+I2 ≈ I3+I4 (equiparar agentes) y que I1+I3 ≈ I2+I4 (equiparar cancelaciones y confirmaciones).
La **política priorizada** busca que I3+I4 representen el 75% del total (agente superior) y que I2+I4 representen el 80% (confirmaciones).
