import java.util.Optional;
import java.util.Set;

public interface Politica {

    // Registra que la transición t fue disparada (actualiza contadores internos).
    // No-op para transiciones que no participan en conflictos estructurales.
    // Se invoca una única vez por disparo, dentro del lock del Monitor.
    void registrarDisparo(int transicion);

    // Indica si la transición t puede disparar según los ratios de la política.
    // Para transiciones fuera de conflictos estructurales siempre devuelve true.
    // Se invoca dentro del lock, antes del disparo efectivo.
    boolean puedoDisparar(int t);

    // Decide a qué transición señalizar tras un disparo.
    // Recibe candidatos = sensibilizadas ∩ hilos_esperando y actúa en dos fases:
    //   Fase 1 — resolución de conflictos: si ambas transiciones de un par
    //     conflictivo ({T2,T3} o {T6,T7}) están en candidatos, elimina la perdedora.
    //   Fase 2 — elección única: de los candidatos restantes elige exactamente uno
    //     (al azar, ya que los sobrevivientes no comparten recursos entre sí).
    // Retorna empty() si candidatos está vacío.
    Optional<Integer> cualDisparar(Set<Integer> candidatos);

    EstadisticasPolitica getEstadisticas();
}
