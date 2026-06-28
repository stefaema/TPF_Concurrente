import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Análisis temporal de la red de Petri.
 * Ejecuta 5 configuraciones de tiempos × 2 políticas × N repeticiones y
 * compara los tiempos prácticos contra el mínimo teórico por cuello de botella.
 *
 * Uso: java MainAnalizadorTemporal [repeticiones]   (default: 3)
 */
public class MainAnalizadorTemporal {

    static final int    OBJETIVO = 186;
    static final String CSV      = "logs/analisis_temporal.csv";

    static TiemposTransicion.VentanaTemporal v(long ms) {
        return new TiemposTransicion.VentanaTemporal(ms);
    }

    // ── Configuraciones temporales ────────────────────────────────────────────

    record Cfg(String nombre, String desc,
               Map<Integer, TiemposTransicion.VentanaTemporal> tiempos) {}

    static final List<Cfg> CONFIGS = List.of(

        new Cfg("BASE",
                "T1=100 T4=180 T5=220 T8=100 T9=150 T10=150",
                Map.of(1,v(100), 4,v(180), 5,v(220), 8,v(100), 9,v(150), 10,v(150))),

        new Cfg("TODO_RAPIDO",
                "T1=50  T4=90  T5=110 T8=50  T9=75  T10=75  (x0.5)",
                Map.of(1,v(50),  4,v(90),  5,v(110), 8,v(50),  9,v(75),  10,v(75))),

        new Cfg("TODO_LENTO",
                "T1=200 T4=360 T5=440 T8=200 T9=300 T10=300 (x2)",
                Map.of(1,v(200), 4,v(360), 5,v(440), 8,v(200), 9,v(300), 10,v(300))),

        new Cfg("DECISION_RAPIDA",
                "T1=100 T4=180 T5=220 T8=10  T9=10  T10=10  (tramites ~0)",
                Map.of(1,v(100), 4,v(180), 5,v(220), 8,v(10),  9,v(10),  10,v(10))),

        new Cfg("AGENTES_RAPIDOS",
                "T1=100 T4=10  T5=10  T8=100 T9=150 T10=150 (agentes ~0)",
                Map.of(1,v(100), 4,v(10),  5,v(10),  8,v(100), 9,v(150), 10,v(150)))
    );

    enum Modo { BALANCEADA, PRIORIZADA }
    static final Modo[] MODOS = Modo.values();

    // ═════════════════════════════════════════════════════════════════════════
    // Main
    // ═════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws InterruptedException {
        int reps = (args.length > 0) ? Integer.parseInt(args[0]) : 3;

        encabezado(reps);
        analisisAnalitico();

        long[][][] res = new long[CONFIGS.size()][MODOS.length][reps];
        List<String[]> csv = new ArrayList<>();
        csv.add(new String[]{"config", "descripcion", "politica", "run", "duracion_ms"});

        StringBuilder runHdr = new StringBuilder();
        for (int r = 0; r < reps; r++) runHdr.append(String.format("   Run%-2d", r + 1));

        System.out.println("\n  EJECUCIÓN PRÁCTICA");
        sep(70);
        System.out.printf("  %-18s %-12s %s  | Promedio    σ%n",
                          "CONFIG", "POLÍTICA", runHdr);
        sep(70);

        for (int ci = 0; ci < CONFIGS.size(); ci++) {
            Cfg cfg = CONFIGS.get(ci);
            for (int mi = 0; mi < MODOS.length; mi++) {
                Modo modo = MODOS[mi];
                System.out.printf("  %-18s %-12s", cfg.nombre(), modo.name());
                System.out.flush();
                for (int r = 0; r < reps; r++) {
                    long d = ejecutar(cfg, modo);
                    res[ci][mi][r] = d;
                    csv.add(new String[]{
                        cfg.nombre(), cfg.desc(), modo.name(),
                        String.valueOf(r + 1), String.valueOf(d)
                    });
                    System.out.printf(" %6.1fs", d / 1000.0);
                    System.out.flush();
                }
                long avg = promedio(res[ci][mi]);
                long sd  = desvio(res[ci][mi]);
                System.out.printf("  | %6.1fs ±%.1fs%n", avg / 1000.0, sd / 1000.0);
            }
        }

        tablaResumen(res);
        escribirCsv(csv);
        System.out.println();
        System.out.printf("  CSV guardado en: %s%n", CSV);
        System.out.println("═".repeat(74));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Ejecución individual
    // ═════════════════════════════════════════════════════════════════════════

    static long ejecutar(Cfg cfg, Modo modo) throws InterruptedException {
        RedPetri      red     = new RedPetri();
        Logger        logger  = Logger.noOp();
        Politica      pol     = (modo == Modo.BALANCEADA) ? new PoliticaBalanceada()
                                                          : new PoliticaPriorizada();
        Monitor       monitor = new Monitor(red, pol,
                                            new TiemposTransicion(cfg.tiempos()), logger);
        AtomicInteger cnt     = new AtomicInteger(0);

        Thread[] inter = {
            new Thread(new SegmentoIntermedio(monitor, new int[]{0, 1}),     "AT-H1"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{3, 4}),     "AT-H2"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{2, 5}),     "AT-H3"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{6, 9, 10}), "AT-H4"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{7, 8}),     "AT-H5"),
        };
        Thread salida = new Thread(
            new SegmentoSalida(monitor, 11, cnt, OBJETIVO), "AT-H6");

        long t0 = System.currentTimeMillis();
        for (Thread h : inter) h.start();
        salida.start();

        salida.join(600_000L);                            // timeout 10 min por seguridad
        for (Thread h : inter) { h.interrupt(); h.join(5_000L); }

        return System.currentTimeMillis() - t0;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Análisis analítico
    // ═════════════════════════════════════════════════════════════════════════

    static void analisisAnalitico() {
        System.out.println("\n  ANÁLISIS ANALÍTICO — Tiempo mínimo teórico (cuello de botella)");
        sep(70);
        System.out.printf("  %-18s %-20s %12s %12s %8s%n",
                          "CONFIG", "Cuello (BAL)", "T_min BAL", "T_min PRIO", "Δ%");
        sep(70);
        for (Cfg cfg : CONFIGS) {
            long   tb  = tMin(cfg, 0.50, 0.50);
            long   tp  = tMin(cfg, 0.75, 0.80);
            String cb  = cuello(cfg, 0.50, 0.50);
            double pct = 100.0 * (tp - tb) / tb;
            System.out.printf("  %-18s %-20s %10.1fs %10.1fs %+7.1f%%%n",
                              cfg.nombre(), cb, tb / 1000.0, tp / 1000.0, pct);
        }
        System.out.println();
        System.out.println("  Fórmula: T_min = max(T_entrada, T_ag_sup, T_ag_inf, T_decision)");
        System.out.println("    T_entrada  = 186 × α(T1)");
        System.out.println("    T_ag_sup   = round(186 × p_sup) × α(T5)");
        System.out.println("    T_ag_inf   = (186 − round(186 × p_sup)) × α(T4)");
        System.out.println("    T_decision = 186 × (p_apr × (α(T9)+α(T10)) + (1−p_apr) × α(T8))");
        System.out.println("    BALANCEADA: p_sup=0.50 · p_apr=0.50");
        System.out.println("    PRIORIZADA: p_sup=0.75 · p_apr=0.80");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tabla resumen
    // ═════════════════════════════════════════════════════════════════════════

    static void tablaResumen(long[][][] res) {
        System.out.println("\n  RESUMEN — Teórico vs. Práctico");
        sep(74);
        System.out.printf("  %-18s %11s %12s %12s %12s %10s%n",
                          "CONFIG",
                          "T_min BAL", "Medido BAL",
                          "T_min PRIO", "Medido PRIO",
                          "Overhead");
        sep(74);
        for (int ci = 0; ci < CONFIGS.size(); ci++) {
            Cfg  cfg = CONFIGS.get(ci);
            long tb  = tMin(cfg, 0.50, 0.50);
            long tp  = tMin(cfg, 0.75, 0.80);
            long mb  = promedio(res[ci][0]);
            long mp  = promedio(res[ci][1]);
            double ov = 100.0 * (mb - tb) / tb;
            System.out.printf("  %-18s %9.1fs %10.1fs %10.1fs %10.1fs %+9.1f%%%n",
                              cfg.nombre(),
                              tb / 1000.0, mb / 1000.0,
                              tp / 1000.0, mp / 1000.0,
                              ov);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cálculo teórico: cuello de botella
    // ═════════════════════════════════════════════════════════════════════════

    // Tiempo mínimo teórico: dominado por el recurso serial más cargado.
    // Cada recurso serializado (P1, P6, P7, P10) impone un piso de tiempo.
    static long tMin(Cfg cfg, double pSup, double pApr) {
        long t1 = a(cfg,1), t4 = a(cfg,4), t5 = a(cfg,5),
             t8 = a(cfg,8), t9 = a(cfg,9), t10 = a(cfg,10);
        int  nSup = (int) Math.round(OBJETIVO * pSup);
        int  nInf = OBJETIVO - nSup;

        long ent = (long) OBJETIVO * t1;                            // P1  serial
        long sup = (long) nSup    * t5;                            // P6  serial
        long inf = (long) nInf    * t4;                            // P7  serial
        long dec = (long)(OBJETIVO * (pApr*(t9+t10) + (1-pApr)*t8)); // P10 serial

        return Math.max(Math.max(ent, sup), Math.max(inf, dec));
    }

    static String cuello(Cfg cfg, double pSup, double pApr) {
        long t1 = a(cfg,1), t4 = a(cfg,4), t5 = a(cfg,5),
             t8 = a(cfg,8), t9 = a(cfg,9), t10 = a(cfg,10);
        int  nSup = (int) Math.round(OBJETIVO * pSup);
        int  nInf = OBJETIVO - nSup;

        long ent = (long) OBJETIVO * t1;
        long sup = (long) nSup    * t5;
        long inf = (long) nInf    * t4;
        long dec = (long)(OBJETIVO * (pApr*(t9+t10) + (1-pApr)*t8));
        long max = Math.max(Math.max(ent, sup), Math.max(inf, dec));

        if (dec == max) return "Etapa decisión";
        if (sup == max) return "Agente superior";
        if (inf == max) return "Agente inferior";
        return "Etapa entrada";
    }

    static long a(Cfg cfg, int t) { return cfg.tiempos().get(t).alfa(); }

    // ═════════════════════════════════════════════════════════════════════════
    // Estadísticas
    // ═════════════════════════════════════════════════════════════════════════

    static long promedio(long[] v) {
        long s = 0; for (long x : v) s += x; return s / v.length;
    }

    static long desvio(long[] v) {
        long avg = promedio(v); double s = 0;
        for (long x : v) s += (double)(x - avg) * (x - avg);
        return (long) Math.sqrt(s / v.length);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CSV
    // ═════════════════════════════════════════════════════════════════════════

    static void escribirCsv(List<String[]> filas) {
        new java.io.File("logs").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(CSV))) {
            for (String[] row : filas) pw.println(String.join(",", row));
        } catch (IOException e) {
            System.err.println("  No se pudo escribir CSV: " + e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Presentación
    // ═════════════════════════════════════════════════════════════════════════

    static void encabezado(int reps) {
        long estMs = 0;
        for (Cfg cfg : CONFIGS)
            for (Modo m : MODOS) {
                double ps = (m == Modo.PRIORIZADA) ? 0.75 : 0.50;
                double pa = (m == Modo.PRIORIZADA) ? 0.80 : 0.50;
                estMs += tMin(cfg, ps, pa) * reps;
            }
        System.out.println();
        System.out.println("═".repeat(74));
        System.out.println("  ANÁLISIS TEMPORAL — RED DE PETRI AGENCIA DE VIAJES");
        System.out.printf ("  %d invariantes/corrida · %d repeticiones · " +
                           "%d políticas · %d configs%n",
                           OBJETIVO, reps, MODOS.length, CONFIGS.size());
        System.out.printf ("  Tiempo estimado total: ~%.0f minutos%n",
                           estMs * 1.08 / 60_000.0);
        System.out.println("═".repeat(74));
    }

    static void sep(int n) { System.out.println("  " + "─".repeat(n)); }
}
