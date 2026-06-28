import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// Mantiene los pares (T2,T3) y (T6,T7) equilibrados en 50/50.
public class PoliticaBalanceada implements Politica {

    // Máxima diferencia permitida entre los contadores de un par en conflicto.
    private static final int DRIFT = 2;

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
            if      (c2 > c3 + DRIFT) seleccionables.remove(2);
            else if (c3 > c2 + DRIFT) seleccionables.remove(3);
            // dentro de la banda: ambas sobreviven, fase 2 elige al azar
        }
        if (seleccionables.contains(6) && seleccionables.contains(7)) {
            if      (c6 > c7 + DRIFT) seleccionables.remove(6);
            else if (c7 > c6 + DRIFT) seleccionables.remove(7);
        }

        List<Integer> lista = new ArrayList<>(seleccionables);
        return Optional.of(lista.get(ThreadLocalRandom.current().nextInt(lista.size())));
    }

    @Override
    public boolean puedoDisparar(int t) {
        return switch (t) {
            case 2 -> c2 <= c3 + DRIFT;
            case 3 -> c3 <= c2 + DRIFT;
            case 6 -> c6 <= c7 + DRIFT;
            case 7 -> c7 <= c6 + DRIFT;
            default -> true;
        };
    }

    @Override
    public EstadisticasPolitica getEstadisticas() {
        return new EstadisticasPolitica(c2, c3, c6, c7);
    }
}
