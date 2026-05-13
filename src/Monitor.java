import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Monitor implements MonitorInterface {

    private static final int NUM_TRANSICIONES = 12;

    // Transiciones que participan en conflictos estructurales de la red.
    private static final Set<Integer> TRANSICIONES_CONFLICTO = Set.of(2, 3, 6, 7);

    private final RedPetri            red;
    private final Politica            politica;
    private final RastreadorClientes  rastreador;
    private final TiemposTransicion   tiempos;
    private final Logger              logger;

    private final ReentrantLock lock;
    private final Condition[]   condiciones;

    public Monitor(RedPetri red, Politica politica, RastreadorClientes rastreador,
                   TiemposTransicion tiempos, Logger logger) {
        this.red        = red;
        this.politica   = politica;
        this.rastreador = rastreador;
        this.tiempos    = tiempos;
        this.logger     = logger;

        this.lock       = new ReentrantLock();
        this.condiciones = new Condition[NUM_TRANSICIONES];
        for (int i = 0; i < NUM_TRANSICIONES; i++) {
            condiciones[i] = lock.newCondition();
        }
    }

    @Override
    public boolean fireTransition(int t) {
        boolean lockHeld = false;
        lock.lock();
        lockHeld = true;
        try {
            // Paso 2: esperar hasta que la transición esté habilitada y la política la autorice.
            while (!red.estaHabilitada(t) || (esConflicto(t) && !politica.debeDisparar(t))) {
                condiciones[t].await();
            }

            // Paso 3: semántica temporal — sleep con el lock suelto.
            if (tiempos.esTemporal(t)) {
                lock.unlock();
                lockHeld = false;

                try {
                    Thread.sleep(tiempos.getTiempo(t));
                } catch (InterruptedException e) {
                    // El lock NO está tomado — no llamar unlock().
                    Thread.currentThread().interrupt();
                    return false;
                }

                lock.lock();
                lockHeld = true;

                // Re-verificar: otro hilo pudo haber alterado el estado durante el sleep.
                while (!red.estaHabilitada(t) || (esConflicto(t) && !politica.debeDisparar(t))) {
                    condiciones[t].await();
                }
            }

            // Pasos 4–9: disparar, rastrear, verificar, registrar y señalizar.
            red.disparar(t);
            Cliente cliente = rastreador.disparar(t);
            red.verificarInvariantesPlaza();
            if (esConflicto(t)) politica.registrarDisparo(t);
            logger.registrar(t, cliente.getId());

            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                if (red.estaHabilitada(i)) condiciones[i].signalAll();
            }

        } catch (InterruptedException e) {
            // Alcanzado solo desde await() — el lock siempre está tomado aquí.
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (lockHeld) lock.unlock();
        }
        return true;
    }

    private boolean esConflicto(int t) {
        return TRANSICIONES_CONFLICTO.contains(t);
    }
}
