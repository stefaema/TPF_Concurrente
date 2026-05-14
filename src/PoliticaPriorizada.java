// Política priorizada: T2 ocupa el 75% (ratio 3:1 sobre T3), T6 ocupa el 80% (ratio 4:1 sobre T7).
//
// Bloqueo bidireccional: la transición preferida también se bloquea cuando ya supera
// el ratio objetivo, obligando a su hilo a await() y cediendo el token a la no-preferida.
// Esto garantiza que el ratio emerja del algoritmo y no del azar del scheduler.
//
// Condiciones (par T2/T3):
//   T2 habilitada: (c2+1) <= 3*(c3+1)   →  T2 se bloquea cuando lleva 3:1 de ventaja
//   T3 habilitada: (c3+1)*4 <= (c2+c3+1) →  T3 se bloquea cuando superaría el 25%
//
// Prueba de liveness: si T2 bloqueada → c2 >= 3*c3+3 → T3 siempre habilitada.
//                     si T3 bloqueada → c2 <= 3*c3+2 → T2 siempre habilitada.
// En todo instante exactamente una del par está habilitada: no hay carrera entre hilos.
// Mismo razonamiento aplica al par T6/T7 con ratio 4:1.
public class PoliticaPriorizada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

    @Override
    public boolean debeDisparar(int t) {
        return switch (t) {
            case 2 -> (c2 + 1) <= 3 * (c3 + 1);
            case 3 -> (c3 + 1) * 4 <= (c2 + c3 + 1);
            case 6 -> (c6 + 1) <= 4 * (c7 + 1);
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
