public class EstadisticasPolitica {
    private final int disparosT2;
    private final int disparosT3;
    private final int disparosT6;
    private final int disparosT7;

    public EstadisticasPolitica(int t2, int t3, int t6, int t7) {
        this.disparosT2 = t2;
        this.disparosT3 = t3;
        this.disparosT6 = t6;
        this.disparosT7 = t7;
    }

    public int getDisparosT2() { return disparosT2; }
    public int getDisparosT3() { return disparosT3; }
    public int getDisparosT6() { return disparosT6; }
    public int getDisparosT7() { return disparosT7; }

    public String formatear() {
        int totalAgente   = disparosT2 + disparosT3;
        int totalDecision = disparosT6 + disparosT7;
        return String.format(
            "--- Estadísticas de política ---%n" +
            "Agente superior (T2): %d  Agente inferior (T3): %d  (%.1f%% / %.1f%%)%n" +
            "Confirmadas     (T6): %d  Canceladas      (T7): %d  (%.1f%% / %.1f%%)",
            disparosT2, disparosT3,
            100.0 * disparosT2 / totalAgente, 100.0 * disparosT3 / totalAgente,
            disparosT6, disparosT7,
            100.0 * disparosT6 / totalDecision, 100.0 * disparosT7 / totalDecision
        );
    }
}
