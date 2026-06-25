import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// Mantiene los pares (T2,T3) y (T6,T7) equilibrados en 50/50.
public class PoliticaBalanceada implements Politica {

    private int c2 = 0, c3 = 0;
    private int c6 = 0, c7 = 0;

    // Guardia del while del Monitor: impide que una transición dispare si ya lleva ventaja.
    // Garantiza |c(tA) - c(tB)| <= 1 durante toda la ejecución.
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

    // Implementa el contrato de dos fases de Politica.cualDisparar:
    //
    // Fase 1 — resolución de conflictos (un ganador por par):
    //   c2 > c3  → eliminar T2 (rezagada es T3).
    //   c2 <= c3 → eliminar T3 (incluye empate: T2 gana como desempate determinista).
    //
    //   En empate T2 siempre gana la fase 1. Su disparo incrementa c2, por lo que
    //   en la siguiente evaluación c2 > c3 y T3 gana. Resultado: alternancia perfecta
    //   T2, T3, T2, T3, … sin competencia aleatoria entre hilos. Misma lógica para T6/T7.
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
            if (c2 > c3) seleccionables.remove(2);
            else          seleccionables.remove(3);
        }
        if (seleccionables.contains(6) && seleccionables.contains(7)) {
            if (c6 > c7) seleccionables.remove(6);
            else          seleccionables.remove(7);
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
