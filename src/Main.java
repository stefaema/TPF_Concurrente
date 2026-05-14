import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final int    OBJETIVO  = 186;
    private static final String DIR_LOGS  = "logs";
    private static final String LOG_BATCH = DIR_LOGS + "/log.txt";

    // Tiempos por defecto de las transiciones temporales {T1,T4,T5,T8,T9,T10} en ms.
    private static final Map<Integer, Long> TIEMPOS_DEFAULT = Map.of(
        1,  100L,   // T1:  ingreso del cliente a la sala de espera
        4,  200L,   // T4:  atención finalizada por agente de reservas inferior
        5,  200L,   // T5:  atención finalizada por agente de reservas superior
        8,  100L,   // T8:  procesamiento de cancelación
        9,  150L,   // T9:  procesamiento de confirmación
        10, 150L    // T10: procesamiento de pago
    );

    // ═════════════════════════════════════════════════════════════════════════
    // Tipos internos
    // ═════════════════════════════════════════════════════════════════════════

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

    static final class ResultadoEjecucion {
        final int                          numero;
        final TipoPolitica                 politica;
        final long                         duracionMs;
        final EstadisticasPolitica         estadisticas;
        final AnalizadorInvariantes.Resultado analisis;
        final String                       archivoLog;

        ResultadoEjecucion(int numero, TipoPolitica politica, long duracionMs,
                           EstadisticasPolitica estadisticas,
                           AnalizadorInvariantes.Resultado analisis,
                           String archivoLog) {
            this.numero       = numero;
            this.politica     = politica;
            this.duracionMs   = duracionMs;
            this.estadisticas = estadisticas;
            this.analisis     = analisis;
            this.archivoLog   = archivoLog;
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

        switch (args.length) {
            case 0  -> modoInteractivo();
            case 1  -> modoBatch(args[0]);
            default -> {
                System.err.println("Uso: java Main [balanceada|priorizada]");
                System.err.println("     Sin argumentos → modo interactivo");
                System.exit(1);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Modo interactivo
    // ═════════════════════════════════════════════════════════════════════════

    private static void modoInteractivo() {
        Scanner sc = new Scanner(System.in);
        imprimirBanner();

        // ── Número de ejecuciones ──
        System.out.println("  Configuración");
        System.out.println("  " + "─".repeat(50));
        int total = leerEntero(sc, "  Número de ejecuciones", 1, 1, 50);

        // ── Distribución de políticas ──
        System.out.println();
        int numBalanceadas;
        if (total == 1) {
            System.out.println("  Política:");
            System.out.println("    [1] BALANCEADA");
            System.out.println("    [2] PRIORIZADA");
            numBalanceadas = leerEntero(sc, "  Opción", 1, 1, 2) == 1 ? 1 : 0;
        } else {
            numBalanceadas = leerEntero(sc,
                String.format("  Ejecuciones BALANCEADA (resto → PRIORIZADA, total=%d)", total),
                total / 2, 0, total);
        }
        int numPriorizadas = total - numBalanceadas;
        System.out.printf("    → %d× BALANCEADA  +  %d× PRIORIZADA%n", numBalanceadas, numPriorizadas);

        // ── Tiempos de transición ──
        TiemposTransicion tiempos = elegirTiempos(sc);

        // ── Confirmación ──
        System.out.println();
        System.out.println("  " + "─".repeat(57));
        System.out.printf("  %d ejecución(es):  %d× BALANCEADA  +  %d× PRIORIZADA%n",
            total, numBalanceadas, numPriorizadas);
        System.out.printf("  Tiempos : %s%n", formatearTiempos(tiempos));
        System.out.printf("  Objetivo: %d invariantes por ejecución%n", OBJETIVO);
        System.out.println("  " + "─".repeat(57));
        if (!leerConfirmacion(sc, "  ¿Confirmar?")) {
            System.out.println("  Cancelado.");
            return;
        }
        System.out.println();

        // ── Ejecutar ──
        List<TipoPolitica> orden = new ArrayList<>();
        for (int i = 0; i < numBalanceadas; i++) orden.add(TipoPolitica.BALANCEADA);
        for (int i = 0; i < numPriorizadas; i++) orden.add(TipoPolitica.PRIORIZADA);

        List<ResultadoEjecucion> resultados = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            TipoPolitica tipo = orden.get(i);
            String archivoLog = String.format("%s/ejecucion-%d-%s.txt",
                DIR_LOGS, i + 1, tipo.name().toLowerCase());
            resultados.add(ejecutar(tipo, tiempos, i + 1, total, archivoLog));
        }

        // ── Resumen y drill-down ──
        mostrarTablaResumen(resultados);
        preguntarDetalle(sc, resultados);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Modo batch — compatibilidad con scripts
    // ═════════════════════════════════════════════════════════════════════════

    private static void modoBatch(String arg) {
        TipoPolitica tipo = TipoPolitica.parsear(arg);
        System.out.printf("Ejecución iniciada — política: %s — objetivo: %d invariantes%n",
            tipo, OBJETIVO);

        ResultadoEjecucion r = ejecutar(tipo, new TiemposTransicion(TIEMPOS_DEFAULT),
            1, 1, LOG_BATCH);

        System.out.println(r.estadisticas.formatear());
        new AnalizadorInvariantes(LOG_BATCH, OBJETIVO).analizar();
        System.out.println("Ejecución finalizada.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Núcleo de ejecución
    // ═════════════════════════════════════════════════════════════════════════

    private static ResultadoEjecucion ejecutar(
            TipoPolitica tipo, TiemposTransicion tiempos,
            int numEjecucion, int totalEjecuciones,
            String archivoLog) {

        RedPetri  red      = new RedPetri();
        Logger    logger   = new Logger(archivoLog);
        Politica  politica = switch (tipo) {
            case BALANCEADA -> new PoliticaBalanceada();
            case PRIORIZADA -> new PoliticaPriorizada();
        };
        RastreadorClientes rastreador = new RastreadorClientes();
        Monitor            monitor   = new Monitor(red, politica, rastreador, tiempos, logger);
        AtomicInteger      contador  = new AtomicInteger(0);

        Thread[] hilosIntermedios = {
            new Thread(new SegmentoIntermedio(monitor, new int[]{0, 1}),     "H1-ingreso"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{3, 4}),     "H2-inferior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{2, 5}),     "H3-superior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{6, 9, 10}), "H4-aprobacion"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{7, 8}),     "H5-rechazo"),
        };
        Thread[] hilosSalida = new Thread[5];
        for (int i = 0; i < 5; i++) {
            hilosSalida[i] = new Thread(
                new SegmentoSalida(monitor, 11, contador, OBJETIVO),
                "H" + (i + 6) + "-salida");
        }

        long   inicio         = System.currentTimeMillis();
        Thread progresoThread = iniciarProgreso(contador, numEjecucion, totalEjecuciones, tipo);

        for (Thread t : hilosIntermedios) t.start();
        for (Thread t : hilosSalida)      t.start();
1

        try {
            for (Thread t : hilosSalida)      t.join();
            for (Thread t : hilosIntermedios) t.interrupt();
            for (Thread t : hilosIntermedios) t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duracion = System.currentTimeMillis() - inicio;

        progresoThread.interrupt();
        try { progresoThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        imprimirBarraFinal(numEjecucion, totalEjecuciones, tipo, duracion);

        EstadisticasPolitica stats = politica.getEstadisticas();
        logger.escribirResumen(stats.formatear());
        logger.cerrar();

        AnalizadorInvariantes.Resultado analisis =
            new AnalizadorInvariantes(archivoLog, OBJETIVO).calcular();

        return new ResultadoEjecucion(numEjecucion, tipo, duracion, stats, analisis, archivoLog);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Barra de progreso
    // ═════════════════════════════════════════════════════════════════════════

    private static Thread iniciarProgreso(AtomicInteger contador,
                                          int numEjecucion, int totalEjecuciones,
                                          TipoPolitica tipo) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int n = Math.min(contador.get(), OBJETIVO);
                imprimirBarra(numEjecucion, totalEjecuciones, tipo, n);
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

    private static void imprimirBarra(int num, int total, TipoPolitica tipo, int actual) {
        final int barWidth = 25;
        int filled = OBJETIVO > 0 ? (int) ((long) actual * barWidth / OBJETIVO) : 0;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barWidth; i++) bar.append(i < filled ? '█' : '░');
        bar.append(']');
        System.out.printf("\r  Ejecución %d/%d [%-11s] %s %3d/%d  ",
            num, total, tipo.name(), bar, actual, OBJETIVO);
        System.out.flush();
    }

    private static void imprimirBarraFinal(int num, int total, TipoPolitica tipo, long ms) {
        System.out.printf("\r  Ejecución %d/%d [%-11s] [%s] %d/%d  %.1fs ✓%n",
            num, total, tipo.name(), "█".repeat(25), OBJETIVO, OBJETIVO, ms / 1000.0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tabla resumen
    // ═════════════════════════════════════════════════════════════════════════

    private static void mostrarTablaResumen(List<ResultadoEjecucion> resultados) {
        final int SEP = 74;
        System.out.println();
        System.out.println("  " + "═".repeat(SEP));
        System.out.println("  RESUMEN DE EJECUCIONES");
        System.out.println("  " + "═".repeat(SEP));
        System.out.printf("  %-4s  %-11s  %-7s  %-26s  %-20s%n",
            "Ej.", "Política", "Tiempo", "Agente superior", "Confirmadas");
        System.out.println("  " + "─".repeat(SEP));

        for (ResultadoEjecucion r : resultados) {
            double pctSup = r.analisis.pctSuperior();
            double pctApb = r.analisis.pctAprobados();

            boolean okSup, okApb;
            String  etqSup, etqApb;
            if (r.politica == TipoPolitica.PRIORIZADA) {
                okSup  = pctSup >= 75.0; etqSup = "obj≥75%";
                okApb  = pctApb >= 80.0; etqApb = "obj≥80%";
            } else {
                okSup  = pctSup >= 45.0 && pctSup <= 55.0; etqSup = "obj~50%";
                okApb  = pctApb >= 45.0 && pctApb <= 55.0; etqApb = "obj~50%";
            }

            System.out.printf("  %-4d  %-11s  %4.1fs    %5.1f%% %-8s %s     %5.1f%% %-8s %s%n",
                r.numero, r.politica.name(), r.duracionMs / 1000.0,
                pctSup, etqSup, okSup ? "✓" : "✗",
                pctApb, etqApb, okApb ? "✓" : "✗");
        }
        System.out.println("  " + "═".repeat(SEP));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Detalle de ejecución
    // ═════════════════════════════════════════════════════════════════════════

    private static void preguntarDetalle(Scanner sc, List<ResultadoEjecucion> resultados) {
        System.out.println();
        while (true) {
            System.out.printf("  Ver detalle de ejecución (1-%d, Enter para salir): ",
                resultados.size());
            String linea = sc.nextLine().trim();
            if (linea.isEmpty()) break;
            try {
                int num = Integer.parseInt(linea);
                if (num < 1 || num > resultados.size()) {
                    System.out.printf("    ✗ Ingrese un número entre 1 y %d.%n", resultados.size());
                    continue;
                }
                mostrarDetalle(resultados.get(num - 1));
            } catch (NumberFormatException e) {
                System.out.println("    ✗ Valor inválido.");
            }
        }
        System.out.println();
        System.out.println("  Fin.");
    }

    private static void mostrarDetalle(ResultadoEjecucion r) {
        AnalizadorInvariantes.Resultado a = r.analisis;
        final int SEP = 52;

        System.out.println();
        System.out.printf("  Ejecución %d — %s — %.1fs%n",
            r.numero, r.politica.name(), r.duracionMs / 1000.0);
        System.out.println("  " + "─".repeat(SEP));

        System.out.println("  Distribución de T-invariantes:");
        String[] nombres = {
            "I1 (inf + rechazado)",
            "I2 (inf + aprobado) ",
            "I3 (sup + rechazado)",
            "I4 (sup + aprobado) ",
        };
        for (int i = 0; i < 4; i++) {
            double pct = a.total > 0 ? 100.0 * a.conteos[i] / a.total : 0.0;
            System.out.printf("    %s : %3d  (%5.1f%%)%n", nombres[i], a.conteos[i], pct);
        }

        System.out.println();
        System.out.println("  Agentes:");
        System.out.printf("    Superior (I3+I4): %3d  (%5.1f%%)%n",
            a.superior(), a.pctSuperior());
        System.out.printf("    Inferior (I1+I2): %3d  (%5.1f%%)%n",
            a.inferior(), 100.0 - a.pctSuperior());

        System.out.println();
        System.out.println("  Decisiones:");
        System.out.printf("    Confirmadas (I2+I4): %3d  (%5.1f%%)%n",
            a.aprobados(), a.pctAprobados());
        System.out.printf("    Canceladas  (I1+I3): %3d  (%5.1f%%)%n",
            a.cancelados(), 100.0 - a.pctAprobados());

        System.out.println();
        System.out.println("  Contadores internos de política:");
        r.estadisticas.formatear().lines()
            .filter(l -> !l.startsWith("---"))
            .forEach(l -> System.out.println("    " + l));

        System.out.println();
        System.out.printf("  Log → %s%n", r.archivoLog);
        if (a.incompletos > 0)
            System.out.printf("  (secuencias incompletas al shutdown: %d)%n", a.incompletos);
        System.out.println("  " + "─".repeat(SEP));
        System.out.println();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Configuración de tiempos
    // ═════════════════════════════════════════════════════════════════════════

    private static TiemposTransicion elegirTiempos(Scanner sc) {
        System.out.println();
        System.out.println("  Tiempos de transición:");
        System.out.printf("    [1] Default  (%s)%n", formatearTiempos(new TiemposTransicion(TIEMPOS_DEFAULT)));
        System.out.println("    [2] Personalizar");
        int opcion = leerEntero(sc, "  Opción", 1, 1, 2);

        if (opcion == 1) return new TiemposTransicion(TIEMPOS_DEFAULT);

        System.out.println("    (Enter conserva el valor por defecto)");
        int[]    ids    = {  1,                4,                5,                8,              9,               10    };
        String[] labels = { "T1  ingreso sala", "T4  atención inf.", "T5  atención sup.",
                             "T8  cancelación ", "T9  confirmación", "T10 pago         " };

        Map<Integer, Long> custom = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) {
            long def = TIEMPOS_DEFAULT.get(ids[i]);
            long val = leerLong(sc,
                String.format("    %s", labels[i]), def, 0, 30_000);
            custom.put(ids[i], val);
        }
        return new TiemposTransicion(custom);
    }

    private static String formatearTiempos(TiemposTransicion t) {
        return String.format("T1=%dms  T4=%dms  T5=%dms  T8=%dms  T9=%dms  T10=%dms",
            t.getTiempo(1), t.getTiempo(4), t.getTiempo(5),
            t.getTiempo(8), t.getTiempo(9), t.getTiempo(10));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers de lectura
    // ═════════════════════════════════════════════════════════════════════════

    private static int leerEntero(Scanner sc, String prompt, int def, int min, int max) {
        while (true) {
            System.out.printf("%s [%d]: ", prompt, def);
            String linea = sc.nextLine().trim();
            if (linea.isEmpty()) return def;
            try {
                int v = Integer.parseInt(linea);
                if (v >= min && v <= max) return v;
                System.out.printf("    ✗ Ingrese un valor entre %d y %d.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("    ✗ Valor inválido.");
            }
        }
    }

    private static long leerLong(Scanner sc, String prompt, long def, long min, long max) {
        while (true) {
            System.out.printf("%s [%d ms]: ", prompt, def);
            String linea = sc.nextLine().trim();
            if (linea.isEmpty()) return def;
            try {
                long v = Long.parseLong(linea);
                if (v >= min && v <= max) return v;
                System.out.printf("    ✗ Ingrese un valor entre %d y %d ms.%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("    ✗ Valor inválido.");
            }
        }
    }

    private static boolean leerConfirmacion(Scanner sc, String prompt) {
        System.out.printf("%s [S/n]: ", prompt);
        String r = sc.nextLine().trim().toLowerCase();
        return r.isEmpty() || r.equals("s") || r.equals("si") || r.equals("sí");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI cosmética
    // ═════════════════════════════════════════════════════════════════════════

    private static void imprimirBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════╗");
        System.out.println("  ║   Agencia de Viajes — Simulación Red de Petri    ║");
        System.out.println("  ╚══════════════════════════════════════════════════╝");
        System.out.println();
    }
}
