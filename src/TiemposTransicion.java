import java.util.Map;

public class TiemposTransicion {

    private final Map<Integer, Long> tiempos;

    public TiemposTransicion(Map<Integer, Long> tiempos) {
        this.tiempos = Map.copyOf(tiempos);
    }

    public boolean esTemporal(int t) {
        return tiempos.containsKey(t);
    }

    public long getTiempo(int t) {
        return tiempos.getOrDefault(t, 0L);
    }
}
