import java.util.Map;

public class TiemposTransicion {

    // Ventana temporal de una transición temporizada: alfa ms desde la habilitación.
    // Semántica débil con β = ∞: la transición no puede disparar antes de que
    // transcurra alfa ms desde su habilitación; no existe límite superior.
    public static final class VentanaTemporal {
        private final long alfa;

        public VentanaTemporal(long alfa) {
            if (alfa < 0) throw new IllegalArgumentException("alfa debe ser >= 0");
            this.alfa = alfa;
        }

        public long alfa() { return alfa; }
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
}
