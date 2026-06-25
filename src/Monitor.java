import java.util.Date;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Monitor implements MonitorInterface {

    private static final int NUM_TRANSICIONES = 12;

    // Transiciones que participan en conflictos estructurales de la red.
    private static final Set<Integer> TRANSICIONES_CONFLICTO = Set.of(2, 3, 6, 7);

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
            //   1. Habilitada estructuralmente (marcado >= Pre[t])
            //   2. Autorizada por política para transiciones de conflicto (T2,T3,T6,T7)
            //   3. Ventana temporal alcanzada: ahora >= habilitación + alfa (β = ∞)
            //
            // Si la transición es temporal y ya cumple condiciones 1 y 2 pero alfa aún
            // no transcurrió, se usa awaitUntil para liberar el lock y esperar exactamente
            // hasta el instante alfa sin ocupar CPU. En otro caso, await() espera señal.
            while (!listaParaDisparar(t)) {
                colaEspera[t]++;
                try {
                    if (tiempos.esTemporal(t) && habilitadaPorEstadoYPolitica(t)) {
                        // Estructural y política OK, pero alfa aún no transcurrió.
                        // awaitUntil libera el lock y espera hasta el instante objetivo.
                        condiciones[t].awaitUntil(new Date(tiempoObjetivo[t]));
                    } else {
                        // Espera estructural o de política: necesita señal externa.
                        condiciones[t].await();
                    }
                } finally {
                    // Se ejecuta tanto en retorno normal como si InterruptedException
                    // propaga hacia el catch exterior, garantizando conteo correcto.
                    colaEspera[t]--;
                }
            }

            // Disparar, verificar invariantes y registrar.
            red.disparar(t);
            red.verificarInvariantesPlaza();
            if (esConflicto(t)) politica.registrarDisparo(t);
            logger.registrar(t);

            // Actualizar relojes para las transiciones afectadas por el nuevo marcado.
            tiempoHabilitacion[t] = -1;
            long ahora = System.currentTimeMillis();
            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                boolean habilitada = red.estaHabilitada(i);
                if (habilitada && tiempoHabilitacion[i] < 0) {
                    // Recién habilitada: arrancar reloj y fijar objetivo en habilitación + alfa.
                    tiempoHabilitacion[i] = ahora;
                    tiempoObjetivo[i]     = calcularObjetivo(i, ahora);
                } else if (!habilitada) {
                    // Deshabilitada: limpiar reloj; se reiniciará si se vuelve a habilitar.
                    tiempoHabilitacion[i] = -1;
                }
            }

            // Señalización dirigida por política — flujo solicitado por el profesor:
            //   sensibilizadas     = transiciones habilitadas estructuralmente en el nuevo marcado
            //   conHilosEsperando  = transiciones con algún hilo bloqueado en await()
            //   candidatos         = sensibilizadas ∩ conHilosEsperando  (AND lógico)
            //   política elige de candidatos a quién(es) señalizar, resolviendo conflictos
            //
            // Nota: transiciones del mismo segmento cuyo hilo aún no llamó fireTransition
            // (p.ej. T5 justo después de que H3 disparó T2) tienen colaEspera == 0 y no
            // aparecen en candidatos; el hilo las disparará directamente al llamar
            // fireTransition(5), sin pasar por await().
            Set<Integer> sensibilizadas    = new HashSet<>();
            Set<Integer> conHilosEsperando = new HashSet<>();
            for (int i = 0; i < NUM_TRANSICIONES; i++) {
                if (red.estaHabilitada(i)) sensibilizadas.add(i);
                if (colaEspera[i] > 0)     conHilosEsperando.add(i);
            }
            Set<Integer> candidatos = new HashSet<>(sensibilizadas);
            candidatos.retainAll(conHilosEsperando);

            Set<Integer> aSeñalizar = politica.cualDisparar(candidatos);
            for (int i : aSeñalizar) {
                condiciones[i].signal();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
        return true;
    }

    // Verdadero si la transición cumple simultáneamente las tres condiciones de disparo:
    //   1. habilitada estructuralmente
    //   2. autorizada por política (solo para transiciones de conflicto)
    //   3. ventana temporal alcanzada (alfa transcurrido; β = ∞)
    private boolean listaParaDisparar(int t) {
        if (!red.estaHabilitada(t)) return false;
        if (esConflicto(t) && !politica.debeDisparar(t)) return false;
        if (tiempos.esTemporal(t) && System.currentTimeMillis() < tiempoObjetivo[t]) return false;
        return true;
    }

    // Verdadero si la transición cumple las condiciones estructural y de política,
    // sin verificar la ventana temporal. Permite elegir awaitUntil en lugar de await
    // cuando alfa aún no transcurrió (el único freno pendiente es el tiempo).
    private boolean habilitadaPorEstadoYPolitica(int t) {
        return red.estaHabilitada(t) && (!esConflicto(t) || politica.debeDisparar(t));
    }

    // Fija el momento objetivo de disparo en habilitadoEn + alfa (EFT).
    // β = ∞: no hay límite superior; la transición dispara exactamente cuando alfa transcurre.
    private long calcularObjetivo(int t, long habilitadoEn) {
        if (!tiempos.esTemporal(t)) return habilitadoEn;
        return habilitadoEn + tiempos.getAlfa(t);
    }

    private boolean esConflicto(int t) {
        return TRANSICIONES_CONFLICTO.contains(t);
    }
}
