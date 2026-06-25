import java.util.Set;

public interface Politica {
    // Consulta pura: ¿puede disparar la transición t según los contadores actuales?
    // Se llama dentro del while del Monitor como guardia de correctitud; no modifica estado.
    boolean debeDisparar(int transicion);

    // Registra que la transición t fue disparada (actualiza contadores internos).
    // Se llama una única vez por disparo, dentro del lock del Monitor.
    void registrarDisparo(int transicion);

    // Decide exactamente cuál transición señalizar en este ciclo de disparo.
    // Proceso en dos fases:
    //   Fase 1 — resolución de conflictos: si las dos transiciones de un par
    //     conflictivo (T2/T3 o T6/T7) están en candidatos, se elimina la perdedora
    //     según la política; la ganadora permanece.
    //   Fase 2 — elección única: de los candidatos restantes se elige exactamente
    //     uno por índice mínimo (determinista entre no-conflictos).
    //
    // Contrato: |resultado| == 1, o 0 si candidatos está vacío.
    // Invariante: nunca se señalizan dos candidatos simultáneamente, eliminando
    // wakeups espurios y la competencia entre hilos despertados al mismo tiempo.
    Set<Integer> cualDisparar(Set<Integer> candidatos);

    EstadisticasPolitica getEstadisticas();
}
