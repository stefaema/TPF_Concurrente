import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// Política priorizada: T2 ocupa el 75% (ratio 3:1 sobre T3), T6 ocupa el 80% (ratio 4:1 sobre T7).
//
// Bloqueo bidireccional: la transición preferida también se bloquea cuando ya supera
// el ratio objetivo, cediendo el token a la no-preferida. Esto garantiza que el ratio
// emerja del algoritmo y no del azar del scheduler.
//
// Condiciones (par T2/T3):
//   T2 habilitada: (c2+1) <= 3*(c3+1)   →  T2 se bloquea cuando lleva 3:1 de ventaja
//   T3 habilitada: (c3+1)*4 <= (c2+c3+1) →  T3 se bloquea cuando superaría el 25%
//
// Prueba de liveness: si T2 bloqueada → c2 >= 3*c3+3 → T3 siempre habilitada.
//                     si T3 bloqueada → c2 <= 3*c3+2 → T2 siempre habilitada.
// En todo instante exactamente una del par está habilitada. Mismo razonamiento para T6/T7.
public class PoliticaPriorizada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

    // Guardia del while del Monitor: bloquea la transición cuando ya superó su cuota.
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

    // Implementa el contrato de dos fases de Politica.cualDisparar:
    //
    // Fase 1 — resolución de conflictos (un ganador por par):
    //   debeDisparar garantiza exclusión mutua: exactamente una del par está habilitada
    //   en cada instante, por lo que siempre hay un ganador claro.
    //
    // Fase 2 — elección única entre los candidatos restantes:
    //   Se elige al azar entre los candidatos no conflictivos: ninguno de ellos comparte
    //   recursos con los otros, por lo que no hay criterio de política que justifique
    //   preferir uno sobre otro. La elección aleatoria evita sesgos implícitos.
    @Override
    public Set<Integer> cualDisparar(Set<Integer> candidatos) {
        if (candidatos.isEmpty()) return Set.of();

        Set<Integer> seleccionables = new HashSet<>(candidatos);

        // Fase 1: resolver conflictos.
        if (seleccionables.contains(2) && seleccionables.contains(3)) {
            if (debeDisparar(2)) seleccionables.remove(3);
            else                  seleccionables.remove(2);
        }
        if (seleccionables.contains(6) && seleccionables.contains(7)) {
            if (debeDisparar(6)) seleccionables.remove(7);
            else                  seleccionables.remove(6);
        }

        // Fase 2: exactamente una transición — elegida al azar.
        List<Integer> lista = new ArrayList<>(seleccionables);
        return Set.of(lista.get(ThreadLocalRandom.current().nextInt(lista.size())));
    }

    @Override
    public EstadisticasPolitica getEstadisticas() {
        return new EstadisticasPolitica(c2, c3, c6, c7);
    }
}
