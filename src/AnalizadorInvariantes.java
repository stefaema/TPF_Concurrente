import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalizadorInvariantes {

    // ─────────────────────────────────────────────────────────────────────────
    // Resultado del análisis (datos sin presentación)
    // ─────────────────────────────────────────────────────────────────────────

    static final class Resultado {
        final int   total;
        final int[] conteos;      // [I1, I2, I3, I4]
        final int   incompletos;
        final int   invalidos;

        Resultado(int total, int[] conteos, int incompletos, int invalidos) {
            this.total       = total;
            this.conteos     = conteos;
            this.incompletos = incompletos;
            this.invalidos   = invalidos;
        }

        int superior()   { return conteos[2] + conteos[3]; }
        int inferior()   { return conteos[0] + conteos[1]; }
        int aprobados()  { return conteos[1] + conteos[3]; }
        int cancelados() { return conteos[0] + conteos[2]; }

        double pctSuperior()  { return total > 0 ? 100.0 * superior()  / total : 0.0; }
        double pctAprobados() { return total > 0 ? 100.0 * aprobados() / total : 0.0; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regex unificada de T-invariantes (requisito 12)
    // ─────────────────────────────────────────────────────────────────────────

    // Cada línea del log tiene formato exacto: T<n> (solo eso, sin más texto).
    // Se usa matches() sobre la línea completa para evitar falsos positivos en la
    // sección de estadísticas, que contiene subcadenas como "(T2):" o "(T6):".
    private static final Pattern LINEA = Pattern.compile("T(\\d+)");

    // Regex única que cubre los 4 T-invariantes mediante alternación.
    // Se aplica sobre secuencias ya reconstruidas por FIFO (una cadena aislada
    // por cliente), NO sobre el log crudo intercalado. Aplicarla directamente
    // sobre el log con wildcards (.*?) produciría "robo": la rama T2 T5 podría
    // consumir transiciones de un cliente distinto al que abrió el T0.
    //
    // Estructura del árbol de la red:
    //   T0 T1 ──┬── T3 T4 (agente inferior)  ──┬── T7 T8       (cancelado)  → I1
    //           │                               └── T6 T9 T10  (aprobado)   → I2
    //           └── T2 T5 (agente superior)   ──┬── T7 T8       (cancelado)  → I3
    //                                           └── T6 T9 T10  (aprobado)   → I4
    //           T11
    private static final Pattern PATRON_INVARIANTES = Pattern.compile(
        "T0 T1 " +
        "(?:(?<inferior>T3 T4)|(?<superior>T2 T5))" +
        " " +
        "(?:(?<cancelado>T7 T8)|(?<aprobado>T6 T9 T10))" +
        " T11"
    );

    private static final String[] NOMBRES = {
        "I1 (inferior + rechazado)",
        "I2 (inferior + aprobado) ",
        "I3 (superior + rechazado)",
        "I4 (superior + aprobado) ",
    };

    // ─────────────────────────────────────────────────────────────────────────

    private final String archivo;
    private final int    objetivo;

    public AnalizadorInvariantes(String archivo, int objetivo) {
        this.archivo  = archivo;
        this.objetivo = objetivo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /** Calcula los conteos sin imprimir nada. */
    public Resultado calcular() {
        List<List<String>> secuencias = reconstruirSecuencias();
        int[] conteos     = new int[4];
        int   incompletos = 0;
        int   invalidos   = 0;

        for (List<String> seq : secuencias) {
            String ultima = seq.get(seq.size() - 1);
            if (!ultima.equals("T11")) {
                incompletos++;
                continue;
            }
            String cadena = String.join(" ", seq);
            int idx = clasificar(cadena);
            if (idx >= 0) conteos[idx]++;
            else invalidos++;
        }

        int total = conteos[0] + conteos[1] + conteos[2] + conteos[3];
        return new Resultado(total, conteos, incompletos, invalidos);
    }

    /** Calcula e imprime el reporte completo por consola. */
    public void analizar() {
        imprimirReporte(calcular(), objetivo);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reconstruye las secuencias por cliente a partir del log plano (sin IDs),
     * usando semántica FIFO sobre la estructura de la red de Petri.
     *
     * El log está serializado por el lock del Monitor, por lo que el orden
     * de las líneas refleja el orden real de disparo. Cada T0 abre una nueva
     * secuencia; cada T11 cierra la primera secuencia abierta cuyo último
     * elemento es T8 o T10 (los únicos predecesores de T11 en la red).
     *
     * Las secuencias que no alcanzaron T11 al momento del shutdown quedan
     * en "pending" y se reportan como incompletas.
     */
    private List<List<String>> reconstruirSecuencias() {
        List<List<String>> pending = new ArrayList<>();
        List<List<String>> todas   = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                Matcher m = LINEA.matcher(linea.trim());
                if (!m.matches()) continue;  // matches() exige que TODA la línea sea T<n>
                String t = "T" + m.group(1);

                switch (t) {
                    case "T0" -> {
                        List<String> nueva = new ArrayList<>();
                        nueva.add("T0");
                        pending.add(nueva);
                    }
                    case "T1"  -> extender(pending, t, "T0");
                    case "T2"  -> extender(pending, t, "T1");
                    case "T3"  -> extender(pending, t, "T1");
                    case "T4"  -> extender(pending, t, "T3");
                    case "T5"  -> extender(pending, t, "T2");
                    case "T6"  -> extender(pending, t, "T4", "T5");
                    case "T7"  -> extender(pending, t, "T4", "T5");
                    case "T8"  -> extender(pending, t, "T7");
                    case "T9"  -> extender(pending, t, "T6");
                    case "T10" -> extender(pending, t, "T9");
                    case "T11" -> {
                        Iterator<List<String>> it = pending.iterator();
                        while (it.hasNext()) {
                            List<String> seq = it.next();
                            String last = seq.get(seq.size() - 1);
                            if (last.equals("T8") || last.equals("T10")) {
                                seq.add("T11");
                                todas.add(seq);
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo log: " + e.getMessage());
        }

        // Secuencias que no completaron T11 al shutdown → incompletas
        todas.addAll(pending);
        return todas;
    }

    // Extiende la primera secuencia en pending cuyo último elemento está en expectedLast.
    private static void extender(List<List<String>> pending, String t, String... expectedLast) {
        Set<String> expected = new HashSet<>(Arrays.asList(expectedLast));
        for (List<String> seq : pending) {
            if (expected.contains(seq.get(seq.size() - 1))) {
                seq.add(t);
                return;
            }
        }
    }

    private int clasificar(String secuencia) {
        Matcher m = PATRON_INVARIANTES.matcher(secuencia);
        if (!m.matches()) return -1;
        boolean esInferior = m.group("inferior") != null;
        boolean esAprobado = m.group("aprobado") != null;
        if ( esInferior && !esAprobado) return 0;  // I1: inferior + cancelado
        if ( esInferior &&  esAprobado) return 1;  // I2: inferior + aprobado
        if (!esInferior && !esAprobado) return 2;  // I3: superior + cancelado
        return 3;                                   // I4: superior + aprobado
    }

    static void imprimirReporte(Resultado r, int objetivo) {
        String sep = "=".repeat(52);
        System.out.println();
        System.out.println(sep);
        System.out.println("  ANÁLISIS DE T-INVARIANTES");
        System.out.println(sep);

        String estadoTotal = (r.total == objetivo)
            ? "[OK]"
            : String.format("[FALLO — se esperaban %d]", objetivo);
        System.out.printf("Invariantes completados : %d / %d  %s%n", r.total, objetivo, estadoTotal);

        if (r.incompletos > 0)
            System.out.printf("Secuencias incompletas  : %d  (shutdown antes de T11)%n", r.incompletos);
        if (r.invalidos > 0)
            System.out.printf("Secuencias inválidas    : %d  [FALLO — T11 sin patrón conocido]%n", r.invalidos);

        System.out.println();
        System.out.println("  Desglose por camino:");
        for (int i = 0; i < 4; i++) {
            double pct = r.total > 0 ? 100.0 * r.conteos[i] / r.total : 0.0;
            System.out.printf("    %s : %3d  (%5.1f%%)%n", NOMBRES[i], r.conteos[i], pct);
        }

        if (r.total > 0) {
            System.out.println();
            System.out.println("  Distribución de agentes:");
            System.out.printf("    Agente superior (I3+I4) : %3d  (%5.1f%%)%n",
                r.superior(), r.pctSuperior());
            System.out.printf("    Agente inferior (I1+I2) : %3d  (%5.1f%%)%n",
                r.inferior(), 100.0 - r.pctSuperior());

            System.out.println();
            System.out.println("  Distribución de decisiones:");
            System.out.printf("    Confirmadas  (I2+I4) : %3d  (%5.1f%%)%n",
                r.aprobados(),  r.pctAprobados());
            System.out.printf("    Canceladas   (I1+I3) : %3d  (%5.1f%%)%n",
                r.cancelados(), 100.0 - r.pctAprobados());
        }

        System.out.println(sep);
    }
}
