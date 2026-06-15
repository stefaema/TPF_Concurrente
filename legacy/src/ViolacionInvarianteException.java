import java.util.Arrays;

public class ViolacionInvarianteException extends RuntimeException {
    public ViolacionInvarianteException(String invariante, int[] marcado) {
        super("Invariante violado — " + invariante
              + " | marcado actual: " + Arrays.toString(marcado));
    }
}
