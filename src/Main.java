import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final int    OBJETIVO = 186;
    private static final String LOG_FILE = "logs/log.txt";

    private static final Map<Integer, TiemposTransicion.VentanaTemporal> TIEMPOS_DEFAULT = Map.of(
         1, new TiemposTransicion.VentanaTemporal(100),  // T1:  ingreso a la sala de espera
         4, new TiemposTransicion.VentanaTemporal(180),  // T4:  atención agente inferior
         5, new TiemposTransicion.VentanaTemporal(220),  // T5:  atención agente superior
         8, new TiemposTransicion.VentanaTemporal(100),  // T8:  procesamiento de cancelación
         9, new TiemposTransicion.VentanaTemporal(150),  // T9:  procesamiento de confirmación
        10, new TiemposTransicion.VentanaTemporal(150)   // T10: procesamiento de pago
    );

    enum TipoPolitica {
        BALANCEADA, PRIORIZADA;

        static TipoPolitica parsear(String arg) {
            try {
                return valueOf(arg.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Política inválida: '" + arg + "'");
                System.err.println("Uso: java Main [balanceada|priorizada]");
                System.exit(1);
                return null;
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Entry point
    // ═════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((hilo, ex) -> {
            System.err.println("\n[FATAL] " + ex.getMessage());
            System.exit(1);
        });

        TipoPolitica tipo;
        if (args.length == 0) {
            tipo = elegirPolitica();
        } else if (args.length == 1) {
            tipo = TipoPolitica.parsear(args[0]);
        } else {
            System.err.println("Uso: java Main [balanceada|priorizada]");
            System.exit(1);
            return;
        }

        ejecutar(tipo, new TiemposTransicion(TIEMPOS_DEFAULT));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Selección de política (modo interactivo)
    // ═════════════════════════════════════════════════════════════════════════

    private static TipoPolitica elegirPolitica() {
        Scanner sc = new Scanner(System.in);
        System.out.println();
        System.out.println("  Política:");
        System.out.println("    [1] BALANCEADA  (50% / 50%)");
        System.out.println("    [2] PRIORIZADA  (75% agente sup. / 80% confirmadas)");
        System.out.printf("  Opción [1]: ");
        String linea = sc.nextLine().trim();
        return "2".equals(linea) ? TipoPolitica.PRIORIZADA : TipoPolitica.BALANCEADA;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Núcleo de ejecución
    // ═════════════════════════════════════════════════════════════════════════

    private static void ejecutar(TipoPolitica tipo, TiemposTransicion tiempos) {
        System.out.printf("%nIniciando — política: %s — objetivo: %d invariantes%n%n",
            tipo.name(), OBJETIVO);

        RedPetri      red      = new RedPetri();
        Logger        logger   = new Logger(LOG_FILE);
        Politica      politica = switch (tipo) {
            case BALANCEADA -> new PoliticaBalanceada();
            case PRIORIZADA -> new PoliticaPriorizada();
        };
        Monitor       monitor  = new Monitor(red, politica, tiempos, logger);
        AtomicInteger contador = new AtomicInteger(0);

        Thread[] hilosIntermedios = {
            new Thread(new SegmentoIntermedio(monitor, new int[]{0, 1}),     "H1-ingreso"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{3, 4}),     "H2-inferior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{2, 5}),     "H3-superior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{6, 9, 10}), "H4-aprobacion"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{7, 8}),     "H5-rechazo"),
        };
        // H6 (S_salida): 1 hilo. T11 es inmediata; SegmentoSalida re-dispara
        // T11 directamente sin señal cuando P14 tiene múltiples tokens.
        Thread hiloSalida = new Thread(
            new SegmentoSalida(monitor, 11, contador, OBJETIVO), "H6-salida");

        long   inicio   = System.currentTimeMillis();
        Thread progreso = iniciarProgreso(contador);

        for (Thread t : hilosIntermedios) t.start();
        hiloSalida.start();

        try {
            hiloSalida.join();
            for (Thread t : hilosIntermedios) t.interrupt();
            for (Thread t : hilosIntermedios) t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duracion = System.currentTimeMillis() - inicio;

        progreso.interrupt();
        try { progreso.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        System.out.printf("\r  [%s] %d/%d  %.1fs ✓%n",
            "█".repeat(25), OBJETIVO, OBJETIVO, duracion / 1000.0);

        logger.cerrar();

        System.out.printf("%nInvariantes completados: %d / %d  %s%n",
            contador.get(), OBJETIVO,
            contador.get() == OBJETIVO ? "[OK]" : "[FALLO]");
        System.out.println();
        System.out.println(politica.getEstadisticas().formatear());
        System.out.printf("%nLog: %s%n", LOG_FILE);
        System.out.println("  → Análisis de invariantes por regex: python3 analizar_log.py " + LOG_FILE);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Barra de progreso
    // ═════════════════════════════════════════════════════════════════════════

    private static Thread iniciarProgreso(AtomicInteger contador) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int n      = Math.min(contador.get(), OBJETIVO);
                int filled = OBJETIVO > 0 ? n * 25 / OBJETIVO : 0;
                System.out.printf("\r  [%s%s] %3d/%d  ",
                    "█".repeat(filled), "░".repeat(25 - filled), n, OBJETIVO);
                System.out.flush();
                if (n >= OBJETIVO) break;
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "progreso");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
