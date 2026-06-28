import java.util.concurrent.atomic.AtomicInteger;

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
