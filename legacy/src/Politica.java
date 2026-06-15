public interface Politica {
    boolean debeDisparar(int transicion);
    void registrarDisparo(int transicion);
    EstadisticasPolitica getEstadisticas();
}
