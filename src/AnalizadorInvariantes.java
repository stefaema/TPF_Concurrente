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

    private static final Pattern LINEA = Pattern.compile(
        "\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] T(\\d+) \\(cliente=(\\d+)\\)"
    );

    // Expresiones regulares exactas de los 4 T-invariantes (requisito 12).
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

    private final String archivo;
    private final int objetivo;

    public AnalizadorInvariantes(String archivo, int objetivo) {
        this.archivo  = archivo;
        this.objetivo = objetivo;
    }

    public void analizar() {
        Map<Integer, List<String>> secuencias = agruparPorCliente();
        int[] conteos = new int[4];
        int incompletos = 0;
        int invalidos   = 0;

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
        imprimirReporte(total, conteos, incompletos, invalidos);
    }

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

    private void imprimirReporte(int total, int[] conteos, int incompletos, int invalidos) {
        String sep = "=".repeat(52);
        System.out.println();
        System.out.println(sep);
        System.out.println("  ANÁLISIS DE T-INVARIANTES");
        System.out.println(sep);

        String estadoTotal = (total == objetivo)
            ? "[OK]"
            : String.format("[FALLO — se esperaban %d]", objetivo);
        System.out.printf("Invariantes completados : %d / %d  %s%n", total, objetivo, estadoTotal);

        if (incompletos > 0)
            System.out.printf("Secuencias incompletas  : %d  (shutdown antes de T11)%n", incompletos);
        if (invalidos > 0)
            System.out.printf("Secuencias inválidas    : %d  [FALLO — T11 sin patrón conocido]%n", invalidos);

        System.out.println();
        System.out.println("  Desglose por camino:");
        for (int i = 0; i < 4; i++) {
            double pct = total > 0 ? 100.0 * conteos[i] / total : 0.0;
            System.out.printf("    %s : %3d  (%5.1f%%)%n", NOMBRES[i], conteos[i], pct);
        }

        if (total > 0) {
            int superior   = conteos[2] + conteos[3];
            int inferior   = conteos[0] + conteos[1];
            int aprobados  = conteos[1] + conteos[3];
            int cancelados = conteos[0] + conteos[2];

            System.out.println();
            System.out.println("  Distribución de agentes:");
            System.out.printf("    Agente superior (I3+I4) : %3d  (%5.1f%%)%n",
                superior, 100.0 * superior / total);
            System.out.printf("    Agente inferior (I1+I2) : %3d  (%5.1f%%)%n",
                inferior, 100.0 * inferior / total);

            System.out.println();
            System.out.println("  Distribución de decisiones:");
            System.out.printf("    Confirmadas  (I2+I4) : %3d  (%5.1f%%)%n",
                aprobados,  100.0 * aprobados  / total);
            System.out.printf("    Canceladas   (I1+I3) : %3d  (%5.1f%%)%n",
                cancelados, 100.0 * cancelados / total);
        }

        System.out.println(sep);
    }
}
