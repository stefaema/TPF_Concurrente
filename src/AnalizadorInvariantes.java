import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    // Patrones de los 4 T-invariantes (requisito 12)
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern LINEA = Pattern.compile(
        "\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] T(\\d+) \\(cliente=(\\d+)\\)"
    );

    private static final Pattern[] PATRONES = {
        Pattern.compile("T0 T1 T3 T4 T7 T8 T11"),         // I1: inferior + rechazado
        Pattern.compile("T0 T1 T3 T4 T6 T9 T10 T11"),     // I2: inferior + aprobado
        Pattern.compile("T0 T1 T2 T5 T7 T8 T11"),         // I3: superior + rechazado
        Pattern.compile("T0 T1 T2 T5 T6 T9 T10 T11"),     // I4: superior + aprobado
    };

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
        Map<Integer, List<String>> secuencias = agruparPorCliente();
        int[] conteos    = new int[4];
        int   incompletos = 0;
        int   invalidos   = 0;

        for (List<String> transiciones : secuencias.values()) {
            String secuencia = String.join(" ", transiciones);
            int idx = clasificar(secuencia);
            if (idx >= 0) {
                conteos[idx]++;
            } else if (secuencia.endsWith("T11")) {
                invalidos++;
            } else {
                incompletos++;
            }
        }

        int total = conteos[0] + conteos[1] + conteos[2] + conteos[3];
        return new Resultado(total, conteos, incompletos, invalidos);
    }

    /** Calcula e imprime el reporte completo por consola. */
    public void analizar() {
        Resultado r = calcular();
        imprimirReporte(r);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internos
    // ─────────────────────────────────────────────────────────────────────────

    private Map<Integer, List<String>> agruparPorCliente() {
        Map<Integer, List<String>> mapa = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                Matcher m = LINEA.matcher(linea);
                if (!m.find()) continue;
                int transicion = Integer.parseInt(m.group(1));
                int clienteId  = Integer.parseInt(m.group(2));
                mapa.computeIfAbsent(clienteId, k -> new ArrayList<>())
                    .add("T" + transicion);
            }
        } catch (IOException e) {
            System.err.println("Error leyendo log: " + e.getMessage());
        }
        return mapa;
    }

    private int clasificar(String secuencia) {
        for (int i = 0; i < PATRONES.length; i++) {
            if (PATRONES[i].matcher(secuencia).matches()) return i;
        }
        return -1;
    }

    private void imprimirReporte(Resultado r) {
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
