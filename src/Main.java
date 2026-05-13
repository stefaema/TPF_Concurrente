import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final int OBJETIVO = 186;

    // -----------------------------------------------------------------------
    // Tiempos de transiciones temporales {T1, T4, T5, T8, T9, T10} en ms
    // -----------------------------------------------------------------------
    private static final Map<Integer, Long> TIEMPOS_DEFAULT = Map.of(
        1,  100L,   // T1:  ingreso del cliente a la sala de espera
        4,  200L,   // T4:  atención finalizada por agente de reservas inferior
        5,  200L,   // T5:  atención finalizada por agente de reservas superior
        8,  100L,   // T8:  procesamiento de cancelación
        9,  150L,   // T9:  procesamiento de confirmación
        10, 150L    // T10: procesamiento de pago
    );

    enum TipoPolitica {
        BALANCEADA, PRIORIZADA;

        static TipoPolitica parsear(String arg) {
            try {
                return valueOf(arg.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Política inválida: '" + arg + "'");
                System.err.println("Uso: java Main <política>");
                System.err.println("     política: balanceada | priorizada");
                System.exit(1);
                return null;
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java Main <política>");
            System.err.println("     política: balanceada | priorizada");
            System.exit(1);
        }
        TipoPolitica tipoPolitica = TipoPolitica.parsear(args[0]);

        // Cualquier ViolacionInvarianteException no capturada detiene la JVM.
        Thread.setDefaultUncaughtExceptionHandler((hilo, ex) -> {
            System.err.println("[FATAL] " + ex.getMessage());
            System.exit(1);
        });

        RedPetri red = new RedPetri();
        Logger   logger = new Logger("log.txt");

        Politica politica = switch (tipoPolitica) {
            case BALANCEADA -> new PoliticaBalanceada();
            case PRIORIZADA -> new PoliticaPriorizada();
        };

        RastreadorClientes rastreador = new RastreadorClientes();
        TiemposTransicion  tiempos   = new TiemposTransicion(TIEMPOS_DEFAULT);
        Monitor            monitor   = new Monitor(red, politica, rastreador, tiempos, logger);

        AtomicInteger contador = new AtomicInteger(0);

        // H1–H5: pipeline, terminación por interrupción externa.
        Thread[] hilosIntermedios = {
            new Thread(new SegmentoIntermedio(monitor, new int[]{0, 1}),     "H1-ingreso"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{3, 4}),     "H2-inferior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{2, 5}),     "H3-superior"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{6, 9, 10}), "H4-aprobacion"),
            new Thread(new SegmentoIntermedio(monitor, new int[]{7, 8}),     "H5-rechazo"),
        };

        // H6–H10: retiro de clientes, auto-terminación tras OBJETIVO disparos de T11.
        Thread[] hilosSalida = new Thread[5];
        for (int i = 0; i < 5; i++) {
            hilosSalida[i] = new Thread(
                new SegmentoSalida(monitor, 11, contador, OBJETIVO),
                "H" + (i + 6) + "-salida"
            );
        }

        for (Thread t : hilosIntermedios) t.start();
        for (Thread t : hilosSalida)      t.start();

        System.out.printf("Ejecución iniciada — política: %s — objetivo: %d invariantes%n",
            tipoPolitica, OBJETIVO);

        try {
            // Esperar a que los SegmentoSalida completen los OBJETIVO invariantes.
            for (Thread t : hilosSalida) t.join();

            // Interrumpir H1–H5 (pueden estar bloqueados en await dentro del Monitor).
            for (Thread t : hilosIntermedios) t.interrupt();

            // Esperar terminación limpia.
            for (Thread t : hilosIntermedios) t.join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Resumen de política al log (Logger aún abierto).
        EstadisticasPolitica stats = politica.getEstadisticas();
        logger.escribirResumen(stats.formatear());
        logger.cerrar();

        // Imprimir estadísticas de política en consola.
        System.out.println(stats.formatear());

        // Verificar T-invariantes por regex sobre el log generado.
        new AnalizadorInvariantes("log.txt").analizar();

        System.out.println("Ejecución finalizada.");
    }
}
