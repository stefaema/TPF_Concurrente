import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {

    private static final int MAX_BACKUPS = 5;

    private final PrintWriter writer;
    private final long        startTime;
    private       int         violaciones = 0;

    public Logger(String archivo) {
        File dir = new File(archivo).getParentFile();
        if (dir != null) dir.mkdirs();
        rotarSiExiste(archivo);
        this.startTime = System.currentTimeMillis();
        try {
            this.writer = new PrintWriter(new BufferedWriter(new FileWriter(archivo)));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo abrir el archivo de log: " + archivo, e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            writer.flush();
            writer.close();
        }, "logger-shutdown"));
    }

    // Invocado dentro del lock del Monitor — acceso completamente serializado.
    public void registrar(int t, int clienteId) {
        writer.printf("[%s] T%d (cliente=%d)%n", timestamp(), t, clienteId);
    }

    public void escribirResumen(String texto) {
        writer.println();
        writer.println(texto);
    }

    /** Registra que la transición t disparó fuera de su ventana β. */
    public void registrarViolacionBeta(int t, long elapsed, long beta) {
        violaciones++;
        writer.printf("[WARN] T%d β superado — elapsed=%dms  beta=%dms  exceso=+%dms%n",
            t, elapsed, beta, elapsed - beta);
    }

    public int getViolaciones() {
        return violaciones;
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

    // Rota los backups existentes y renombra el log actual a log.1.txt.
    // El backup más viejo (MAX_BACKUPS) se descarta.
    private static void rotarSiExiste(String archivo) {
        if (!new File(archivo).exists()) return;
        new File(archivo + "." + MAX_BACKUPS).delete();
        for (int i = MAX_BACKUPS - 1; i >= 1; i--) {
            File backup = new File(archivo + "." + i);
            if (backup.exists())
                backup.renameTo(new File(archivo + "." + (i + 1)));
        }
        new File(archivo).renameTo(new File(archivo + ".1"));
    }
}
