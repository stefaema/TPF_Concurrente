import java.util.Date;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Monitor implements MonitorInterface {

    private static final int NUM_TRANSICIONES = 12;

    private final RedPetri          red;
    private final Politica          politica;
    private final TiemposTransicion tiempos;
    private final Logger            logger;

    private final ReentrantLock lock;
    private final Condition[]   condiciones;

    // Cantidad de hilos bloqueados en await() esperando cada transición (protegido por lock).
    private final int[] colaEspera;

    // Instante en que cada transición fue habilitada por última vez (-1 = no habilitada).
    private final long[] tiempoHabilitacion;
    // Momento objetivo de disparo: habilitación + alfa (EFT). β = ∞ (semántica débil).
    private final long[] tiempoObjetivo;

    public Monitor(RedPetri red, Politica politica,
                   TiemposTransicion tiempos, Logger logger) {
        this.red      = red;
        this.politica = politica;
        this.tiempos  = tiempos;
        this.logger   = logger;

        this.lock        = new ReentrantLock();
        this.condiciones = new Condition[NUM_TRANSICIONES];
        this.colaEspera  = new int[NUM_TRANSICIONES];
        for (int i = 0; i < NUM_TRANSICIONES; i++) {
            condiciones[i] = lock.newCondition();
        }

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
            // El hilo espera hasta que pueda disparar: habilitada, ventana temporal alcanzada
            // Y la política lo permite (en caso de conflicto estructural).
            while (!listaParaDisparar(t)) {
                colaEspera[t]++;
                if (red.estaHabilitada(t) && tiempos.esTemporal(t)
                        && System.currentTimeMillis() < tiempoObjetivo[t]) {
                    // Habilitada pero alfa aún no transcurrió: esperar hasta el instante exacto.
                    condiciones[t].awaitUntil(new Date(tiempoObjetivo[t]));
                } else {
                    // No sensibilizada O la política bloquea: esperar señal de otro disparo.
                    condiciones[t].await();
                }
                colaEspera[t]--;
            }

            red.disparar(t);
            red.verificarInvariantesPlaza();
            logger.registrar(t);
            politica.registrarDisparo(t);

            // Actualizar relojes: solo registrar transiciones recién habilitadas;
            // las que ya corrían (tiempoHabilitacion >= 0) conservan su reloj original.
            tiempoHabilitacion[t] = -1;
            long ahora = System.currentTimeMillis();
            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                boolean habilitada = red.estaHabilitada(i);
                if (habilitada && tiempoHabilitacion[i] < 0) {
                    tiempoHabilitacion[i] = ahora;
                    tiempoObjetivo[i]     = calcularObjetivo(i, ahora);
                } else if (!habilitada) {
                    tiempoHabilitacion[i] = -1;
                }
            }

            // Señalización dirigida: la política elige a quién despertar entre los
            // candidatos (sensibilizadas ∩ hilos esperando).
            Set<Integer> sensibilizadas    = new HashSet<>();
            Set<Integer> conHilosEsperando = new HashSet<>();
            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                if (red.estaHabilitada(i)) sensibilizadas.add(i);
                if (colaEspera[i] > 0)     conHilosEsperando.add(i);
            }
            Set<Integer> candidatos = new HashSet<>(sensibilizadas);
            candidatos.retainAll(conHilosEsperando); // AND

            Optional<Integer> aSeñalizar = politica.cualDisparar(candidatos);
            if (aSeñalizar.isPresent()) {
                condiciones[aSeñalizar.get()].signal();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
        return true;
    }

    // Condiciones de disparo: habilitada estructuralmente, ventana temporal alcanzada
    // Y la política lo permite (para transiciones sin conflicto siempre devuelve true).
    private boolean listaParaDisparar(int t) {
        if (!red.estaHabilitada(t)) return false;
        if (tiempos.esTemporal(t) && System.currentTimeMillis() < tiempoObjetivo[t]) return false;
        if (!politica.puedoDisparar(t)) return false;
        return true;
    }

    private long calcularObjetivo(int t, long habilitadoEn) {
        if (!tiempos.esTemporal(t)) return habilitadoEn;
        return habilitadoEn + tiempos.getAlfa(t);
    }
}
