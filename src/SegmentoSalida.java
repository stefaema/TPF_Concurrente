import java.util.concurrent.atomic.AtomicInteger;

// H6 (único hilo de S_salida): dispara T11 reclamando slots atómicos hasta agotar el objetivo.
//
// El Algoritmo 4.3 prescribe 5 hilos para este segmento porque M(P14) puede llegar a 5.
// Sin embargo, T11 es una transición INMEDIATA (no temporal): no hay Thread.sleep() fuera del
// lock, por lo que cada disparo ocurre íntegramente dentro de la sección crítica del Monitor.
// Los N hilos se serializarían exactamente igual que 1 solo hilo — el paralelismo de múltiples
// threads solo se materializa cuando existe un sleep fuera del lock. Por ello se instancia
// un único hilo, que itera disparando T11 hasta completar los 186 invariantes.
// Ver docs/preguntas-respuestas.md §3 para el análisis completo.
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
