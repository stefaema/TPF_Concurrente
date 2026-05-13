import java.util.ArrayDeque;
import java.util.Queue;

// Capa de identidad de tokens. Siempre invocado dentro del lock del Monitor.
class RastreadorClientes {

    private int idCounter = 0;

    // Colas por plaza de acción: P2, P3, P5, P8, P9, P11, P12, P13, P14
    @SuppressWarnings("unchecked")
    private final Queue<Cliente>[] colas = new Queue[15];

    RastreadorClientes() {
        int[] plazasActivas = {2, 3, 5, 8, 9, 11, 12, 13, 14};
        for (int p : plazasActivas) {
            colas[p] = new ArrayDeque<>();
        }
    }

    // Ejecuta la operación de identidad correspondiente a la transición y retorna el cliente afectado.
    Cliente disparar(int t) {
        return switch (t) {
            case 0  -> crear();
            case 1  -> mover(2, 3);
            case 2  -> mover(3, 5);
            case 3  -> mover(3, 8);
            case 4  -> mover(8, 9);
            case 5  -> mover(5, 9);
            case 6  -> mover(9, 11);
            case 7  -> mover(9, 12);
            case 8  -> mover(12, 14);
            case 9  -> mover(11, 13);
            case 10 -> mover(13, 14);
            case 11 -> descartar();
            default -> throw new IllegalArgumentException("Transición desconocida: " + t);
        };
    }

    private Cliente crear() {
        Cliente c = new Cliente(idCounter++);
        colas[2].add(c);
        return c;
    }

    private Cliente mover(int origen, int destino) {
        Cliente c = colas[origen].poll();
        colas[destino].add(c);
        return c;
    }

    private Cliente descartar() {
        return colas[14].poll();
    }
}
