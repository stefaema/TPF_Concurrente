import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {

    private final PrintWriter writer;
    private final long startTime;

    public Logger(String archivo) {
        this.startTime = System.currentTimeMillis();
        try {
            this.writer = new PrintWriter(new BufferedWriter(new FileWriter(archivo)));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo abrir el archivo de log: " + archivo, e);
        }
    }

    // Invocado dentro del lock del Monitor — acceso completamente serializado.
    public void registrar(int t, int clienteId) {
        writer.printf("[%s] T%d (cliente=%d)%n", timestamp(), t, clienteId);
    }

    public void escribirResumen(String texto) {
        writer.println();
        writer.println(texto);
    }

    public void cerrar() {
        writer.flush();
        writer.close();
    }

    private String timestamp() {
        long ms   = System.currentTimeMillis() - startTime;
        long h    = ms / 3_600_000;
        long min  = (ms % 3_600_000) / 60_000;
        long s    = (ms % 60_000) / 1_000;
        long mili = ms % 1_000;
        return String.format("%02d:%02d:%02d.%03d", h, min, s, mili);
    }
}
