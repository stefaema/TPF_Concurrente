import java.util.Map;

public class TiemposTransicion {

    /**
     * Ventana temporal de una transición temporizada: [alfa, beta] ms desde la habilitación.
     * La transición no puede disparar antes de alfa; el objetivo de disparo se fija aleatoriamente
     * en [alfa, beta] en el momento de la habilitación.
     */
    public record VentanaTemporal(long alfa, long beta) {
        public VentanaTemporal {
            if (alfa < 0)    throw new IllegalArgumentException("alfa debe ser >= 0");
            if (beta < alfa) throw new IllegalArgumentException("beta debe ser >= alfa");
        }
    }

    private final Map<Integer, VentanaTemporal> ventanas;

    public TiemposTransicion(Map<Integer, VentanaTemporal> ventanas) {
        this.ventanas = Map.copyOf(ventanas);
    }

    public boolean esTemporal(int t) {
        return ventanas.containsKey(t);
    }

    public long getAlfa(int t) {
        return ventanas.get(t).alfa();
    }

    public long getBeta(int t) {
        return ventanas.get(t).beta();
    }
}
