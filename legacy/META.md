# TPF Concurrente — Sistema de Agencia de Viajes

Trabajo Práctico Final de Programación Concurrente: simulación y ejecución de una
Red de Petri que modela una agencia de viajes, gobernada por un monitor de
concurrencia en Java.

Este README es un documento de ingeniería. Describe qué hace el proyecto, en qué se
diferencia de la versión de referencia de un compañero, qué falta para cerrarlo como
entregable, y hacia dónde queremos llevarlo en una rama nueva (un backend pensado para
poder, algún día, conectarle una interfaz gráfica).

---

## El problema que resuelve

La consigna entrega una Red de Petri (Figura 1 del enunciado) con 15 plazas y 12
transiciones que modela el recorrido de un cliente por una agencia: ingresa, pasa por
uno de dos agentes de reservas, su reserva se aprueba o se rechaza, paga o cancela, y se
retira. Varias plazas son recursos compartidos 1-acotados (los agentes), y hasta 5
clientes circulan en paralelo.

El núcleo del trabajo no es dibujar la red, sino esto: **varios hilos quieren disparar
transiciones a la vez sobre un estado compartido (el marcado), y solo algunas
combinaciones de disparos son válidas**. Si dos hilos compiten por el mismo token, hay
que decidir quién gana sin corromper el marcado ni romper los invariantes de la red. Ese
árbitro es el monitor de concurrencia, y construirlo correctamente es el corazón del TP.

Sobre ese árbitro se montan tres exigencias adicionales de la consigna:

1. **Cantidad y responsabilidad de hilos**, justificada con el artículo de Micolini
   (Algoritmos 4.1, 4.2 y 4.3 sobre cuántos hilos y qué transiciones cubre cada uno).
2. **Políticas** que resuelvan los conflictos: una balanceada (50/50) y una priorizada
   (75% al agente superior, 80% de confirmaciones).
3. **Semántica temporal**: las transiciones {T1, T4, T5, T8, T9, T10} tardan un tiempo, y
   hay que analizar cómo ese tiempo afecta la ejecución.

El detalle completo del análisis vive en `docs/`:

- `docs/analisis-red-petri.md` — propiedades de la red, P-invariantes, T-invariantes, matrices Pre/Post.
- `docs/analisis-hilos.md` — aplicación de los tres algoritmos del artículo para llegar a la cantidad de hilos.
- `docs/diseno.md` — diseño detallado de cada clase del sistema.
- `consigna/Consigna.md` — el enunciado transcripto.

---

## Estado actual

Esta versión (`TPF_Concurrente`) **corre**: el monitor dispara la red, las dos políticas
llegan a sus objetivos, la semántica temporal está modelada y hay un `Main` con modo
interactivo y batch. Pero correr no es lo mismo que estar bien resuelta: tiene **problemas
conceptuales** que sabemos que hay que corregir (ver
[Lo que sabemos que está mal en la nuestra](#lo-que-sabemos-que-está-mal-en-la-nuestra)).

La versión de referencia (`TPF_CONCU_REFE`), hecha por un compañero y **ya aprobada, es la
solución correcta y bien resuelta**. La tomamos como vara. La idea de la rama nueva no es
"ganarle": es **rescatar la esencia de nuestra versión y corregir lo que está mal
planteado**, apoyándonos en la referencia donde ella ya acierta.

---

## Las dos versiones

Existen dos implementaciones del mismo TP:

- **`TPF_Concurrente`** (esta) — la que hicimos nosotros.
- **`TPF_CONCU_REFE`** — la de un compañero, ya aprobada, que usamos como referencia.

Ambas resuelven la misma consigna y llegan a los mismos números (186 invariantes, ~50/50
y ~75/80 según política). Lo que cambia es **cómo** está construido el árbitro y cómo se
verifica el resultado. Antes de la tabla, que quede claro: **la referencia está bien
resuelta**. La columna de la izquierda no es "lo mejor"; es *nuestra forma* de haber
encarado lo mismo, con aciertos y con errores.

### Comparación

| Aspecto | `TPF_Concurrente` (nuestra) | `TPF_CONCU_REFE` (referencia) |
|---|---|---|
| **Mecanismo del monitor** | `ReentrantLock` + un `Condition` por transición. Cada hilo hace `await()` en su condición y se lo despierta con `signalAll()`. | `Semaphore` mutex + un semáforo por transición + flags de "encolada". Handoff explícito: al terminar un disparo, el monitor elige a quién despertar. |
| **Rol de la política** | Consulta pura con veto: `debeDisparar(t)` no tiene efectos, `registrarDisparo(t)` actualiza contadores después del disparo real. La proporción emerge de los contadores (bloqueo bidireccional determinista). | La política **elige a quién despertar** entre las transiciones encoladas y sensibilizadas. La proporción emerge de alternancia (flag) o de un sorteo por probabilidad. |
| **Políticas como tipos** | Dos clases (`PoliticaBalanceada`, `PoliticaPriorizada`) detrás de una interfaz `Politica`. Se elige por argumento. | Una sola clase `Politica` con constantes y flags estáticos que se editan para cambiar de modo. |
| **Cantidad de hilos** | Bajamos `S_salida` de 5 a **1**, y ahí está el error: la reducción justificable es a **2**, no a 1. Ver [Lo que sabemos que está mal](#lo-que-sabemos-que-está-mal-en-la-nuestra). | Cantidad de hilos correcta según la consigna y el artículo. |
| **Identidad de tokens** | Objetos `Cliente` con ID único por ciclo, gestionados por `RastreadorClientes` (colas FIFO por plaza). Cada disparo se loguea con `(cliente=id)`. | Se loguea el marcado tras cada disparo; el token no tiene identidad propia. |
| **Verificación de T-invariantes** | En Java, `AnalizadorInvariantes`: agrupa el log por ID de cliente y matchea cada secuencia 1:1 contra las 4 regex exactas. Sin ambigüedad de cruce entre clientes. | Externa, `regex.py` (Python) sobre el log de transiciones disparadas. |
| **Semántica temporal** | `Condition.awaitUntil(Date)` sobre una ventana `[alfa, beta]` con objetivo aleatorio. Violación de β se loguea (semántica débil) pero no aborta. | `Thread.sleep()` fuera del mutex (unlock / sleep / re-lock), con tiempos alfa/beta leídos de archivos `.txt`. |
| **Terminación** | `AtomicInteger` con pre-claim de los 186 disparos de T11; H6 se auto-termina, `Main` hace `join()` y recién ahí interrumpe al resto. | Polling: el hilo principal hace `sleep(50)` en un loop hasta `reservasCompletas()`, luego `shutdown()` libera todos los semáforos. |
| **Fuente de la red** | Matrices Pre/Post y marcado inicial **hardcodeados** en `RedPetri`. | Matrices leídas de archivos (`incidenciaNegativa.txt`, `inicial.txt`, `alfa.txt`, ...). |
| **Punto de entrada** | `Main` con menú interactivo (N ejecuciones, mezcla de políticas, tiempos), barra de progreso, tabla resumen, y modo batch. | `Agencia` de ejecución única; barrido de tiempos vía `launcher.sh` que vuelca CSV a `ejecuciones.txt`. |

### Cómo leer estas diferencias

Primero lo importante: **la referencia está bien resuelta**. Esto no es un ranking donde
ganamos nosotros; son las decisiones que tomamos, con aciertos y con errores.

- **Mecanismo del monitor (lock vs. semáforos).** Son dos maneras estándar y
  esencialmente equivalentes de construir un monitor. Usar `ReentrantLock` con `Condition`
  es perfectamente válido y **no nos da ninguna ventaja conceptual** sobre el `Semaphore`
  de la referencia: es simplemente la herramienta que elegimos.
- **Dónde vive la decisión del conflicto.** En la referencia la política es activa (el
  monitor le pregunta a quién despertar); en la nuestra es pasiva (cada hilo pregunta
  "¿me toca?" y la política responde sí/no con `debeDisparar`, una consulta pura). Las dos
  son correctas. Y la referencia tampoco depende del azar: por defecto alterna, lo que da
  un 50/50 exacto.
- **Identidad de tokens.** Acá sí hay algo distintivo nuestro que vale la pena conservar
  (*nuestra esencia*): cada `Cliente` lleva un ID único durante todo su ciclo, así que el
  log queda 1:1 con los T-invariantes y la verificación es matchear cuatro regex exactas
  dentro del mismo proyecto Java, sin script externo. No es una corrección a la referencia
  (la suya también verifica bien, con `regex.py`); es solo otra forma de hacerlo.

### Lo que sabemos que está mal en la nuestra

- **Cantidad de hilos.** El artículo prescribe 5 hilos para `S_salida` (M(P14) puede
  llegar a 5). Como T11 es inmediata, la reducción **justificable es a 2 hilos**. En
  nuestra implementación bajamos a **1** (`Main.java`, `new Thread[1]`; y así lo dicen
  `docs/analisis-hilos.md` y `docs/diseno.md`). Eso es una desviación: la consigna pide
  determinar y justificar los hilos *según* el artículo, y el argumento de "1 por
  equivalencia funcional" se pasa de largo. La cuenta correcta es la de la referencia; del
  lado nuestro hay que rehacerla y dejar `S_salida` en 2, no en 1. **Corregir esto implica
  tocar también el código y los docs**, hoy todos en 1.

> Esta lista se está relevando con el grupo: pueden sumarse otros problemas conceptuales a
> medida que los confirmemos.

---

## Qué falta para cerrar el entregable

El código corre, pero el TP como entregable todavía tiene huecos. En orden de prioridad:

1. **Toolchain portable.** `compilar.sh` y `ejecutar.sh` apuntan a un JDK con ruta
   absoluta de la máquina de un integrante (`/home/javi/...`). Hay que reemplazarlos por
   `javac` / `java` del `PATH`, o migrar a un build estándar. No compila en otra máquina
   tal como está.

2. **Diagramas renderizados.** Los entregables (a) y (b) piden imágenes del diagrama de
   clases y de secuencia. Hoy solo existen las fuentes PlantUML (`docs/diagrama-clases.puml`,
   `docs/diagrama-secuencia.puml`). Falta exportarlas a PNG. La referencia ya tiene los PNG.

3. **Informe final.** El entregable (f) es un informe que documente lo hecho, explique el
   código y justifique los resultados. Tenemos el material desperdigado en `docs/`, pero
   falta compilarlo en un único documento entregable. La referencia tiene su
   `Informe Trabajo Final.pdf`.

4. **Datos de experimentos temporales.** El requerimiento 9 pide análisis de tiempos
   analítico **y práctico**, variando los tiempos elegidos y sacando conclusiones. Falta
   capturar y registrar esas corridas. La referencia tiene `ejecuciones.txt` / `.ods` con
   un barrido sistemático. Para esto conviene agregar un **modo batch con salida CSV** al
   `Main`, equivalente al `launcher.sh` de la referencia, y dejar los resultados versionados.

5. **Limpieza del repo.** README (este), y revisar que el `.xml` de PIPE y la imagen de la
   red estén actualizados y coincidan con las matrices de `RedPetri`.

---

## Cómo construir y ejecutar

> **Nota:** los scripts actuales tienen el JDK hardcodeado (ver punto 1 de arriba).
> Mientras tanto, con un JDK en el `PATH`:

```bash
# Compilar
mkdir -p out
javac -encoding UTF-8 -d out src/*.java

# Modo interactivo (menú de configuración)
java -cp out Main

# Modo batch (una corrida, log a logs/log.txt)
java -cp out Main balanceada
java -cp out Main priorizada
```

El modo interactivo permite elegir cantidad de ejecuciones, mezcla de políticas y tiempos
de transición, y muestra una tabla resumen. El modo batch corre una sola vez con los
tiempos por defecto e imprime el análisis de invariantes por consola.

---

## La rama nueva: backend pensado para una GUI

La meta de la próxima rama es entregar el TP final completo y, en el camino, **diseñar el
backend de modo que el estado de la simulación sea observable desde afuera**. La idea es
no pintarnos contra una pared: que el día de mañana se le pueda enganchar una interfaz
sin reescribir el monitor.

### El sueño delirante (no es prioridad)

Que quede claro: lo que sigue es un **sueño delirante**, una prueba de concepto que
**no** vamos a abordar hasta tener todo lo demás funcionando y entregado. Se documenta
acá solo para que las decisiones de diseño del backend no lo vuelvan imposible más
adelante.

La fantasía es una GUI que muestre la evolución de la Red de Petri en vivo: ver los
tokens moverse de plaza en plaza, los conflictos resolverse, los hilos avanzar. La forma
tentativa de llegar ahí:

- Un **daemon Java** que corre los pasos de la simulación (automáticos o paso a paso).
- Un canal **WebSocket** por el que el frontend manda comandos tipo RPC ("dispará un
  paso", "corré en automático", "pausá", "reseteá").
- El daemon responde con la **matriz de estado de la red** (el marcado, y quizás qué
  transiciones están sensibilizadas y qué hilo tocó qué).
- El **frontend mapea esa matriz a un SVG procedural** de la red: cada plaza y transición
  es un nodo, los tokens son círculos que aparecen y desaparecen según el marcado.

### Qué premisa nos llevamos al diseño del backend (esto sí)

De ese sueño rescatamos una sola disciplina concreta, barata de respetar desde ahora:

- **El marcado tiene que ser serializable y consultable como un snapshot.** Hoy solo el
  `Monitor` conoce el marcado y `RedPetri.getMarcado()` ya devuelve una copia defensiva.
  Mantener esa propiedad (un punto único, observable, que entrega el estado sin exponer
  referencias internas) es exactamente lo que un futuro daemon necesitaría para emitir el
  estado por WebSocket.
- **Separar "avanzar la simulación" de "cómo se dispara".** Si el control del avance (paso
  a paso vs automático) queda desacoplado de la mecánica del disparo, un frontend podría
  manejar el ritmo sin tocar el monitor.

No vamos a construir nada de esto todavía. Solo evitamos cerrar puertas: nada de estado
escondido en variables locales de los hilos, nada que impida sacar una foto del marcado
en cualquier momento.

---

## Estructura del repositorio

```
TPF_Concurrente/
├── src/                      Código fuente Java (monitor, red, políticas, hilos, logger)
├── docs/                     Análisis de la red, de hilos, diseño, fuentes de diagramas
├── consigna/                 Enunciado del TP
├── img/                      Imagen de la red y capturas de PIPE
├── Petri net 1.xml           Fuente de la red modelada en PIPE
├── compilar.sh / ejecutar.sh Scripts de build/run (pendiente: rutas portables)
└── README.md                 Este documento
```
</content>
</invoke>
