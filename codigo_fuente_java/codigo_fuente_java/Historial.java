import java.util.*;

/*
 * Aqui va todo lo relacionado con el historial de transformaciones.
 * La idea es que por cada etapa (eliminar nulas, eliminar unitarias,
 * etc.) se guarde un "paso" con la gramatica antes, la gramatica
 * despues, que se elimino y que se agrego. Asi despues se puede mostrar
 * todo el proceso completo, no solo el resultado final.
 *
 * Puse las dos clases juntas en el mismo archivo (Historial y
 * PasoHistorial) porque van siempre de la mano y no vi necesidad de
 * separarlas en dos archivos distintos.
 */
public class Historial {
    public List<PasoHistorial> pasos = new ArrayList<>();

    public void agregar(PasoHistorial paso) {
        pasos.add(paso);
    }

    public void limpiar() {
        pasos.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (PasoHistorial p : pasos) {
            sb.append(p).append("\n");
        }
        return sb.toString();
    }
}

// representa un solo paso del historial: como estaba la gramatica antes
// de la transformacion, que se detecto, que se quito, que se agrego, y
// como quedo despues
class PasoHistorial {
    String etapa;
    Grammar gramaticaAntes;
    List<String> elementosIdentificados;
    List<String> produccionesEliminadas;
    List<String> produccionesAgregadas;
    Grammar gramaticaResultante;

    PasoHistorial(String etapa, Grammar gramaticaAntes, List<String> elementosIdentificados,
                  List<String> produccionesEliminadas, List<String> produccionesAgregadas,
                  Grammar gramaticaResultante) {
        this.etapa = etapa;
        this.gramaticaAntes = gramaticaAntes;
        this.elementosIdentificados = elementosIdentificados;
        this.produccionesEliminadas = produccionesEliminadas;
        this.produccionesAgregadas = produccionesAgregadas;
        this.gramaticaResultante = gramaticaResultante;
    }

    @Override
    public String toString() {
        String linea = String.join("", Collections.nCopies(70, "=")); // separador bien visible
        StringBuilder sb = new StringBuilder();

        sb.append(linea).append("\n");
        sb.append("ETAPA: ").append(etapa).append("\n");
        sb.append(linea).append("\n");

        sb.append("-- Gramatica antes de la transformacion --\n");
        sb.append(gramaticaAntes).append("\n\n");

        sb.append("-- Elementos identificados --\n");
        if (elementosIdentificados.isEmpty()) {
            sb.append("  (ninguno)\n");
        } else {
            for (String e : elementosIdentificados) {
                sb.append("  * ").append(e).append("\n");
            }
        }

        sb.append("\n-- Producciones eliminadas --\n");
        if (produccionesEliminadas.isEmpty()) {
            sb.append("  (ninguna)\n");
        } else {
            for (String e : produccionesEliminadas) {
                sb.append("  - ").append(e).append("\n");
            }
        }

        sb.append("\n-- Producciones agregadas --\n");
        if (produccionesAgregadas.isEmpty()) {
            sb.append("  (ninguna)\n");
        } else {
            for (String e : produccionesAgregadas) {
                sb.append("  + ").append(e).append("\n");
            }
        }

        sb.append("\n-- Gramatica resultante --\n");
        sb.append(gramaticaResultante).append("\n");

        return sb.toString();
    }
}
