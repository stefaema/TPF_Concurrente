// T2 >= 75% del total agente; T6 >= 80% del total decisión.
// factor = 1/(1-u): T2/T3 → 4; T6/T7 → 5.
public class PoliticaPriorizada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

    @Override
    public boolean debeDisparar(int t) {
        return switch (t) {
            case 2 -> true;
            case 3 -> (c3 + 1) * 4 <= (c2 + c3 + 1);
            case 6 -> true;
            case 7 -> (c7 + 1) * 5 <= (c6 + c7 + 1);
            default -> true;
        };
    }

    @Override
    public void registrarDisparo(int t) {
        switch (t) {
            case 2 -> c2++;
            case 3 -> c3++;
            case 6 -> c6++;
            case 7 -> c7++;
        }
    }

    @Override
    public EstadisticasPolitica getEstadisticas() {
        return new EstadisticasPolitica(c2, c3, c6, c7);
    }
}
