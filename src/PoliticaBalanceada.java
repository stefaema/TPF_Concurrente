// Mantiene los pares (T2,T3) y (T6,T7) equilibrados en 50/50.
public class PoliticaBalanceada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

    @Override
    public boolean debeDisparar(int t) {
        return switch (t) {
            case 2 -> c2 <= c3;
            case 3 -> c3 <= c2;
            case 6 -> c6 <= c7;
            case 7 -> c7 <= c6;
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
