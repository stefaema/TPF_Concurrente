import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Monitor implements MonitorInterface {

    private static final int NUM_TRANSICIONES = 12;

    // Transiciones que participan en conflictos estructurales de la red.
    private static final Set<Integer> TRANSICIONES_CONFLICTO = Set.of(2, 3, 6, 7);

    private final RedPetri           red;
    private final Politica           politica;
    private final RastreadorClientes rastreador;
    private final TiemposTransicion  tiempos;
    private final Logger             logger;

    private final ReentrantLock lock;
    private final Condition[]   condiciones;

    // Instante en que cada transición fue habilitada por última vez (-1 = no habilitada).
    private final long[] tiempoHabilitacion;
    // Momento objetivo de disparo: un instante aleatorio dentro de [alfa, beta] desde habilitación.
    private final long[] tiempoObjetivo;
    private final Random rng = new Random();

    public Monitor(RedPetri red, Politica politica, RastreadorClientes rastreador,
                   TiemposTransicion tiempos, Logger logger) {
        this.red        = red;
        this.politica   = politica;
        this.rastreador = rastreador;
        this.tiempos    = tiempos;
        this.logger     = logger;

        this.lock        = new ReentrantLock();
        this.condiciones = new Condition[NUM_TRANSICIONES];
        for (int i = 0; i < NUM_TRANSICIONES; i++) {
            condiciones[i] = lock.newCondition();
        }

        // Inicializar relojes para las transiciones habilitadas en el marcado inicial.
        tiempoHabilitacion = new long[NUM_TRANSICIONES];
        tiempoObjetivo     = new long[NUM_TRANSICIONES];
        Arrays.fill(tiempoHabilitacion, -1L);
        long ahora = System.currentTimeMillis();
        for (int i = 0; i < NUM_TRANSICIONES; i++) {
            if (red.estaHabilitada(i)) {
                tiempoHabilitacion[i] = ahora;
                tiempoObjetivo[i]     = calcularObjetivo(i, ahora);
            }
        }
    }

    @Override
    public boolean fireTransition(int t) {
        lock.lock();
        try {
            // Esperar hasta que la transición esté lista para disparar:
            //   - habilitada estructuralmente (marcado >= Pre[t])
            //   - autorizada por política (solo transiciones de conflicto)
            //   - ventana temporal alcanzada (solo transiciones temporizadas)
            while (!listaParaDisparar(t)) {
                if (tiempos.esTemporal(t) && habilitadaPorEstadoYPolitica(t)) {
                    // Condición estructural y de política ya satisfecha, pero el momento
                    // objetivo aún no llegó. awaitUntil libera el lock y espera hasta
                    // tiempoObjetivo[t] o hasta recibir un signal, lo que ocurra primero.
                    condiciones[t].awaitUntil(new Date(tiempoObjetivo[t]));
                } else {
                    condiciones[t].await();
                }
            }

            // Verificar que no se superó el límite superior β de la ventana temporal.
            if (tiempos.esTemporal(t) && tiempoHabilitacion[t] >= 0) {
                long elapsed = System.currentTimeMillis() - tiempoHabilitacion[t];
                if (elapsed > tiempos.getBeta(t))
                    logger.registrarViolacionBeta(t, elapsed, tiempos.getBeta(t));
            }

            // Disparar, rastrear, verificar invariantes y registrar.
            red.disparar(t);
            Cliente cliente = rastreador.disparar(t);
            red.verificarInvariantesPlaza();
            if (esConflicto(t)) politica.registrarDisparo(t);
            logger.registrar(t, cliente.getId());

            // Actualizar relojes y despertar hilos en espera.
            long ahora = System.currentTimeMillis();
            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                boolean habilitada = red.estaHabilitada(i);
                if (habilitada && tiempoHabilitacion[i] < 0) {
                    // Recién habilitada: arrancar reloj y fijar objetivo aleatorio en [alfa, beta].
                    tiempoHabilitacion[i] = ahora;
                    tiempoObjetivo[i]     = calcularObjetivo(i, ahora);
                } else if (!habilitada) {
                    // Deshabilitada: limpiar reloj; se reiniciará si se vuelve a habilitar.
                    tiempoHabilitacion[i] = -1;
                }
                if (habilitada) condiciones[i].signalAll();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
        return true;
    }

    // Verdadero si la transición cumple simultáneamente las tres condiciones de disparo.
    private boolean listaParaDisparar(int t) {
        if (!red.estaHabilitada(t)) return false;
        if (esConflicto(t) && !politica.debeDisparar(t)) return false;
        if (tiempos.esTemporal(t) && System.currentTimeMillis() < tiempoObjetivo[t]) return false;
        return true;
    }

    // Verdadero si la transición cumple las condiciones estructural y de política,
    // sin considerar la ventana temporal. Permite elegir awaitUntil en lugar de await.
    private boolean habilitadaPorEstadoYPolitica(int t) {
        return red.estaHabilitada(t) && (!esConflicto(t) || politica.debeDisparar(t));
    }

    // Fija el momento objetivo de disparo en un instante aleatorio dentro de [alfa, beta]
    // contado desde habilitadoEn. Si alfa == beta el disparo es determinista en alfa.
    private long calcularObjetivo(int t, long habilitadoEn) {
        if (!tiempos.esTemporal(t)) return habilitadoEn;
        long alfa  = tiempos.getAlfa(t);
        long beta  = tiempos.getBeta(t);
        long rango = beta - alfa;
        return habilitadoEn + alfa + (rango > 0 ? (long)(rng.nextDouble() * rango) : 0L);
    }

    private boolean esConflicto(int t) {
        return TRANSICIONES_CONFLICTO.contains(t);
    }
}
