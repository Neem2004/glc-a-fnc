import java.util.*;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/*
 * Punto de entrada del programa. Aqui esta el menu por consola, tal
 * como lo pide el enunciado: se puede ir corriendo cada transformacion
 * por separado (para ver bien el paso a paso) o correr todo de una vez
 * con el modo automatico.
 *
 * Para compilar:  javac *.java
 * Para correr:    java Main
 */
public class Main {

    static final String SEPARADOR = linea('-', 70);
    static final Scanner scanner = new Scanner(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    // Java 8 no tiene String.repeat (se agrego en Java 11), asi que armo la
    // linea repitiendo el caracter a mano para que el programa corra tambien
    // en versiones antiguas del runtime
    static String linea(char caracter, int cantidad) {
        char[] arreglo = new char[cantidad];
        Arrays.fill(arreglo, caracter);
        return new String(arreglo);
    }

    static Grammar gramaticaOriginal = null;
    static Grammar gramaticaActual = null;
    static Grammar gramaticaFinal = null;
    static Historial historial = new Historial();

    public static void main(String[] args) {
        // fuerzo la salida a UTF-8 para que no salgan simbolos raros en
        // consolas que por defecto no usan esa codificacion
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 siempre esta disponible, esto no deberia pasar
        }

        System.out.println(linea('=', 70));
        System.out.println(" APLICATIVO PARA DEPURACION Y CONVERSION DE GLC A FNC ");
        System.out.println(" Teoria de la Computacion - Microproyecto #1 ");
        System.out.println(linea('=', 70));

        while (true) {
            String opcion = menu();

            switch (opcion) {
                case "1":
                    ingresarNuevaGramatica();
                    break;

                case "2":
                    if (gramaticaOriginal == null) {
                        avisoSinGramatica();
                    } else {
                        mostrarGramatica(gramaticaOriginal, "Gramatica original");
                    }
                    break;

                case "3":
                    if (gramaticaActual == null) {
                        avisoSinGramatica();
                    } else {
                        validarGramatica(gramaticaActual);
                    }
                    break;

                case "4":
                case "5":
                case "6":
                case "7":
                    ejecutarPasoIndividual(opcion);
                    break;

                case "8":
                    ejecutarConversionFnc();
                    break;

                case "9":
                    ejecutarProcesoCompleto();
                    break;

                case "10":
                    if (historial.pasos.isEmpty()) {
                        System.out.println("\nAun no se ha ejecutado ninguna transformacion.\n");
                    } else {
                        for (PasoHistorial paso : historial.pasos) {
                            System.out.println(paso);
                        }
                    }
                    break;

                case "11":
                    mostrarGramaticaFinal();
                    break;

                case "12":
                    reiniciarTodo();
                    break;

                case "13":
                    cargarEjemplo();
                    break;

                case "14":
                    System.out.println("\nFinalizando el aplicativo. Hasta luego!");
                    return;

                default:
                    System.out.println("\nOpcion no valida. Intente nuevamente.\n");
            }
        }
    }

    static String menu() {
        System.out.println(SEPARADOR);
        System.out.println("DEPURACION Y FORMA NORMAL DE CHOMSKY");
        System.out.println(SEPARADOR);
        System.out.println("1.  Ingresar gramatica");
        System.out.println("2.  Mostrar gramatica original");
        System.out.println("3.  Validar gramatica");
        System.out.println("4.  Eliminar producciones nulas");
        System.out.println("5.  Eliminar producciones unitarias");
        System.out.println("6.  Eliminar variables inutiles");
        System.out.println("7.  Eliminar variables inalcanzables");
        System.out.println("8.  Convertir a Forma Normal de Chomsky (paso restante: terminales/binarizacion)");
        System.out.println("9.  Ejecutar proceso completo (modo automatico)");
        System.out.println("10. Mostrar historial de transformaciones");
        System.out.println("11. Mostrar gramatica final");
        System.out.println("12. Ingresar nueva gramatica (reiniciar)");
        System.out.println("13. Cargar gramatica de ejemplo");
        System.out.println("14. Salir");
        System.out.print("\nSeleccione una opcion: ");
        return scanner.nextLine().trim();
    }

    static void avisoSinGramatica() {
        System.out.println("\nPrimero debe ingresar una gramatica (opcion 1).\n");
    }

    static void ingresarNuevaGramatica() {
        Grammar g = ingresarGramatica();
        if (g != null) {
            gramaticaOriginal = g;
            gramaticaActual = g;
            gramaticaFinal = null;
            historial.limpiar();
        }
    }

    static void ejecutarPasoIndividual(String opcion) {
        if (gramaticaActual == null) {
            avisoSinGramatica();
            return;
        }
        if (!gramaticaActual.esValida()) {
            System.out.println("\nLa gramatica actual tiene errores. Validela y corrijala primero.\n");
            return;
        }

        switch (opcion) {
            case "4":
                gramaticaActual = Transformations.eliminarProduccionesNulas(gramaticaActual, historial);
                break;
            case "5":
                gramaticaActual = Transformations.eliminarProduccionesUnitarias(gramaticaActual, historial);
                break;
            case "6":
                gramaticaActual = Transformations.eliminarVariablesInutiles(gramaticaActual, historial);
                break;
            case "7":
                gramaticaActual = Transformations.eliminarVariablesInalcanzables(gramaticaActual, historial);
                break;
        }
        System.out.println(historial.pasos.get(historial.pasos.size() - 1));
    }

    static void ejecutarConversionFnc() {
        if (gramaticaActual == null) {
            avisoSinGramatica();
            return;
        }
        gramaticaActual = Transformations.sustituirTerminales(gramaticaActual, historial);
        System.out.println(historial.pasos.get(historial.pasos.size() - 1));

        gramaticaActual = Transformations.reducirProduccionesLargas(gramaticaActual, historial);
        System.out.println(historial.pasos.get(historial.pasos.size() - 1));

        gramaticaFinal = gramaticaActual;
        System.out.println("La gramatica resultante esta en FNC? "
                + (Transformations.esFnc(gramaticaFinal) ? "Si" : "No") + "\n");
    }

    static void ejecutarProcesoCompleto() {
        if (gramaticaOriginal == null) {
            avisoSinGramatica();
            return;
        }
        if (!gramaticaOriginal.esValida()) {
            System.out.println("\nLa gramatica tiene errores. Corrijalos antes de convertir.\n");
            validarGramatica(gramaticaOriginal);
            return;
        }

        historial.limpiar();
        gramaticaFinal = Transformations.convertirAFnc(gramaticaOriginal, historial);
        gramaticaActual = gramaticaFinal;

        for (PasoHistorial paso : historial.pasos) {
            System.out.println(paso);
        }

        System.out.println("GRAMATICA FINAL EN FORMA NORMAL DE CHOMSKY");
        System.out.println(SEPARADOR);
        System.out.println(gramaticaFinal);
        System.out.println("\nEsta en FNC? " + (Transformations.esFnc(gramaticaFinal) ? "Si" : "No") + "\n");
    }

    static void mostrarGramaticaFinal() {
        if (gramaticaFinal == null) {
            System.out.println("\nAun no se ha generado la gramatica final. Use la opcion 8 o 9 primero.\n");
            return;
        }
        mostrarGramatica(gramaticaFinal, "Gramatica final en Forma Normal de Chomsky");
        System.out.println("Esta en FNC? " + (Transformations.esFnc(gramaticaFinal) ? "Si" : "No") + "\n");
    }

    static void reiniciarTodo() {
        gramaticaOriginal = null;
        gramaticaActual = null;
        gramaticaFinal = null;
        historial.limpiar();
        System.out.println("\nSe borro la gramatica actual. Ingrese una nueva con la opcion 1.\n");
    }

    static void cargarEjemplo() {
        gramaticaOriginal = cargarGramaticaEjemplo();
        gramaticaActual = gramaticaOriginal;
        gramaticaFinal = null;
        historial.limpiar();
        System.out.println("\nSe cargo la gramatica de ejemplo:\n");
        mostrarGramatica(gramaticaOriginal, "Gramatica actual");
    }

    static Grammar ingresarGramatica() {
        System.out.println(SEPARADOR);
        System.out.println("INGRESO DE UNA NUEVA GRAMATICA LIBRE DE CONTEXTO");
        System.out.println(SEPARADOR);

        System.out.print("Variables (separadas por coma o espacio), ej. S,A,B: ");
        String variables = scanner.nextLine();
        System.out.print("Terminales (separados por coma o espacio), ej. a,b: ");
        String terminales = scanner.nextLine();
        System.out.print("Simbolo inicial, ej. S: ");
        String inicial = scanner.nextLine();
        String producciones = leerMultilinea("Producciones, formato 'A->BC|a|epsilon' (una por linea):");

        try {
            Grammar g = Grammar.desdeEntrada(variables, terminales, inicial, producciones);
            System.out.println("\nGramatica registrada correctamente.\n");
            return g;
        } catch (GrammarError e) {
            System.out.println("\n" + e.getMessage() + "\n");
            return null;
        }
    }

    static String leerMultilinea(String mensaje) {
        System.out.println(mensaje);
        System.out.println("(Escriba cada produccion en una linea. Termine con una linea vacia)");

        List<String> lineas = new ArrayList<>();
        while (true) {
            String linea = scanner.nextLine();
            if (linea.trim().isEmpty()) {
                break;
            }
            lineas.add(linea);
        }
        return String.join("\n", lineas);
    }

    // gramatica de ejemplo que se usa en el manual de usuario para mostrar
    // el recorrido completo sin tener que digitar todo a mano
    static Grammar cargarGramaticaEjemplo() {
        try {
            return Grammar.desdeEntrada("S,A,B,C", "a,b", "S",
                    "S->ASA|aB|epsilon\nA->B|S\nB->b|epsilon\nC->AB");
        } catch (GrammarError e) {
            // esto no deberia pasar nunca porque la gramatica de ejemplo
            // ya se probo y es valida
            throw new RuntimeException(e);
        }
    }

    static void mostrarGramatica(Grammar g, String titulo) {
        System.out.println(SEPARADOR);
        System.out.println(titulo);
        System.out.println(SEPARADOR);
        System.out.println(g);
        System.out.println();
    }

    static boolean validarGramatica(Grammar g) {
        List<String> errores = g.validar();

        System.out.println(SEPARADOR);
        System.out.println("VALIDACION DE LA GRAMATICA");
        System.out.println(SEPARADOR);

        if (errores.isEmpty()) {
            System.out.println("La gramatica es valida. Puede continuar con el proceso.\n");
        } else {
            for (String e : errores) {
                System.out.println(e);
            }
            System.out.println("\nLa gramatica presenta errores. Corrijalos antes de continuar.\n");
        }
        return errores.isEmpty();
    }
}
