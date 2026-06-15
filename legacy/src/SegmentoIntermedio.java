// H1–H5: ejecutan un tramo fijo del pipeline en bucle hasta ser interrumpidos externamente.
class SegmentoIntermedio extends Segmento {

    private final int[] transiciones;

    SegmentoIntermedio(MonitorInterface monitor, int[] transiciones) {
        super(monitor);
        this.transiciones = transiciones.clone();
    }

    @Override
    public void run() {
        while (true) {
            for (int t : transiciones) {
                if (!monitor.fireTransition(t)) return;
            }
        }
    }
}
