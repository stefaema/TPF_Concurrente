import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// Política priorizada: T2 ocupa el 75% (ratio 3:1 sobre T3), T6 ocupa el 80% (ratio 4:1 sobre T7).
//
//
// Condiciones internas (par T2/T3):
//   T2 habilitada: (c2+1) <= 3*(c3+1)    →  T2 cede cuando ya lleva 3:1 de ventaja
//   T3 habilitada: (c3+1)*4 <= (c2+c3+1) →  T3 cede cuando superaría el 25%
//
// Prueba de exclusión mutua: si T2 bloqueada → c2 >= 3*c3+3 → T3 siempre habilitada.
//                            si T3 bloqueada → c2 <= 3*c3+2 → T2 siempre habilitada.
// Misma lógica para T6/T7.
public class PoliticaPriorizada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

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
    public Optional<Integer> cualDisparar(Set<Integer> candidatos) {
        if (candidatos.isEmpty()) return Optional.empty();

        Set<Integer> seleccionables = new HashSet<>(candidatos);

        if (seleccionables.contains(2) && seleccionables.contains(3)) {
            if (debeDisparar(2)) seleccionables.remove(3);
            else                  seleccionables.remove(2);
        }
        if (seleccionables.contains(6) && seleccionables.contains(7)) {
            if (debeDisparar(6)) seleccionables.remove(7);
            else                  seleccionables.remove(6);
        }

        List<Integer> lista = new ArrayList<>(seleccionables);
        return Optional.of(lista.get(ThreadLocalRandom.current().nextInt(lista.size())));
    }

    // Retorna true si la transición t puede disparar sin superar su cuota objetivo.
    private boolean debeDisparar(int t) {
        return switch (t) {
            case 2 -> (c2 + 1) <= 3 * (c3 + 1);
            case 3 -> (c3 + 1) * 4 <= (c2 + c3 + 1);
            case 6 -> (c6 + 1) <= 4 * (c7 + 1);
            case 7 -> (c7 + 1) * 5 <= (c6 + c7 + 1);
            default -> true;
        };
    }

    @Override
    public boolean puedoDisparar(int t) {
        return switch (t) {
            case 2, 3, 6, 7 -> debeDisparar(t);
            default -> true;
        };
    }

    @Override
    public EstadisticasPolitica getEstadisticas() {
        return new EstadisticasPolitica(c2, c3, c6, c7);
    }
}
