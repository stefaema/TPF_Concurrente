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

    // Patrones exactos de los 4 T-invariantes.
    private static final String[] PATRONES = {
        "T0 T1 T3 T4 T7 T8 T11",         // I1: inferior + rechazado
        "T0 T1 T3 T4 T6 T9 T10 T11",     // I2: inferior + aprobado
        "T0 T1 T2 T5 T7 T8 T11",         // I3: superior + rechazado
        "T0 T1 T2 T5 T6 T9 T10 T11",     // I4: superior + aprobado
    };

    private static final String[] NOMBRES = {
        "I1 (inferior + rechazado) ",
        "I2 (inferior + aprobado) ",
        "I3 (superior + rechazado)",
        "I4 (superior + aprobado) ",
    };

    private final String archivo;

    public AnalizadorInvariantes(String archivo) {
        this.archivo = archivo;
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
            if (secuencia.equals(PATRONES[i])) return i;
        }
        return -1;
    }

    private void imprimirReporte(int total, int[] conteos, int incompletos, int invalidos) {
        System.out.println();
        System.out.println("=== Análisis de T-invariantes ===");
        System.out.printf("Total invariantes completados: %d%n", total);
        for (int i = 0; i < 4; i++) {
            System.out.printf("  %s: %d%n", NOMBRES[i], conteos[i]);
        }
        if (incompletos > 0)
            System.out.printf("  Secuencias incompletas (sin T11 final): %d%n", incompletos);
        if (invalidos > 0)
            System.out.printf("  Secuencias inválidas (terminan en T11 pero no matchean): %d%n", invalidos);

        if (total > 0) {
            int totalAgente = conteos[2] + conteos[3] + conteos[0] + conteos[1];
            int superior    = conteos[2] + conteos[3];
            int aprobados   = conteos[1] + conteos[3];
            System.out.printf("%nDistribución de agentes:%n");
            System.out.printf("  Superior (I3+I4): %d  (%.1f%%)%n",
                superior, 100.0 * superior / totalAgente);
            System.out.printf("  Inferior (I1+I2): %d  (%.1f%%)%n",
                totalAgente - superior, 100.0 * (totalAgente - superior) / totalAgente);
            System.out.printf("Distribución de decisiones:%n");
            System.out.printf("  Aprobados (I2+I4): %d  (%.1f%%)%n",
                aprobados, 100.0 * aprobados / total);
            System.out.printf("  Cancelados (I1+I3): %d  (%.1f%%)%n",
                total - aprobados, 100.0 * (total - aprobados) / total);
        }
        System.out.println("=================================");
    }
}
