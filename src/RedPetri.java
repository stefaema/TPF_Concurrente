import java.util.Arrays;

public class RedPetri {

    private static final int NUM_PLAZAS      = 15;
    private static final int NUM_TRANSICIONES = 12;

    // Pre[t][p]: tokens que T consume de P
    private static final int[][] PRE = {
        // P0  P1  P2  P3  P4  P5  P6  P7  P8  P9  P10 P11 P12 P13 P14
        {  1,  1,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T0
        {  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T1
        {  0,  0,  0,  1,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0 }, // T2
        {  0,  0,  0,  1,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0 }, // T3
        {  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0 }, // T4
        {  0,  0,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T5
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  0,  0,  0,  0 }, // T6
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  0,  0,  0,  0 }, // T7
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0 }, // T8
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0 }, // T9
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0 }, // T10
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1 }, // T11
    };

    // Post[t][p]: tokens que T produce en P
    private static final int[][] POST = {
        // P0  P1  P2  P3  P4  P5  P6  P7  P8  P9  P10 P11 P12 P13 P14
        {  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T0
        {  0,  1,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T1
        {  0,  0,  0,  0,  1,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T2
        {  0,  0,  0,  0,  1,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0 }, // T3
        {  0,  0,  0,  0,  0,  0,  0,  1,  0,  1,  0,  0,  0,  0,  0 }, // T4
        {  0,  0,  0,  0,  0,  0,  1,  0,  0,  1,  0,  0,  0,  0,  0 }, // T5
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0 }, // T6
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0 }, // T7
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0,  1 }, // T8
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0 }, // T9
        {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  0,  0,  0,  1 }, // T10
        {  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0 }, // T11
    };

    // Marcado inicial M0
    private static final int[] MARCADO_INICIAL = {
        5, 1, 0, 0, 5, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0
    };

    private final int[] marcado;

    public RedPetri() {
        this.marcado = Arrays.copyOf(MARCADO_INICIAL, NUM_PLAZAS);
    }

    public boolean estaHabilitada(int t) {
        for (int p = 0; p < NUM_PLAZAS; p++) {
            if (marcado[p] < PRE[t][p]) return false;
        }
        return true;
    }

    public void disparar(int t) {
        for (int p = 0; p < NUM_PLAZAS; p++) {
            marcado[p] += POST[t][p] - PRE[t][p];
        }
    }

    public void verificarInvariantesPlaza() {
        int[] m = marcado;

        if (m[1] + m[2] != 1)
            throw new ViolacionInvarianteException("PI-1: M(P1)+M(P2)=1", getMarcado());

        if (m[10] + m[11] + m[12] + m[13] != 1)
            throw new ViolacionInvarianteException("PI-2: M(P10)+M(P11)+M(P12)+M(P13)=1", getMarcado());

        if (m[0]+m[2]+m[3]+m[5]+m[8]+m[9]+m[11]+m[12]+m[13]+m[14] != 5)
            throw new ViolacionInvarianteException(
                "PI-3: M(P0)+M(P2)+M(P3)+M(P5)+M(P8)+M(P9)+M(P11)+M(P12)+M(P13)+M(P14)=5", getMarcado());

        if (m[2] + m[3] + m[4] != 5)
            throw new ViolacionInvarianteException("PI-4: M(P2)+M(P3)+M(P4)=5", getMarcado());

        if (m[5] + m[6] != 1)
            throw new ViolacionInvarianteException("PI-5: M(P5)+M(P6)=1", getMarcado());

        if (m[7] + m[8] != 1)
            throw new ViolacionInvarianteException("PI-6: M(P7)+M(P8)=1", getMarcado());
    }

    // Retorna copia defensiva del marcado para logging/excepción.
    public int[] getMarcado() {
        return Arrays.copyOf(marcado, NUM_PLAZAS);
    }
}
