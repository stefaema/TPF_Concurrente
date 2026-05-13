abstract class Segmento implements Runnable {
    protected final MonitorInterface monitor;

    protected Segmento(MonitorInterface monitor) {
        this.monitor = monitor;
    }
}
