import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {

    private static final int MAX_BACKUPS = 5;

    private final PrintWriter writer;

    public Logger(String archivo) {
        File dir = new File(archivo).getParentFile();
        if (dir != null) dir.mkdirs();
        rotarSiExiste(archivo);
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
    public void registrar(int t) {
        writer.println("T" + t);
    }

    public void escribirResumen(String texto) {
        writer.println();
        writer.println(texto);
    }

    public void cerrar() {
        writer.flush();
        writer.close();
    }

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
