import java.util.*;

/*
 * Aca estan todos los algoritmos para depurar la gramatica y llevarla
 * a Forma Normal de Chomsky:
 *
 *   - quitar las producciones que generan epsilon
 *   - quitar las producciones unitarias (A -> B)
 *   - quitar las variables que no generan nada (no sirven para nada)
 *   - quitar las variables a las que nunca se puede llegar desde el
 *     simbolo inicial
 *   - sacar los terminales que esten mezclados en producciones largas
 *   - partir en pedacitos binarios las producciones de mas de 2 simbolos
 *
 * Cada metodo recibe una gramatica y el historial, y devuelve una
 * gramatica NUEVA (nunca se modifica la que llega), para que el
 * historial pueda mostrar bien claro como estaba antes y como quedo
 * despues en cada paso.
 */
public class Transformations {

    // -----------------------------------------------------------------
    // un par de ayudas para comparar producciones y armar el historial
    // -----------------------------------------------------------------

    private static List<List<String>> diffProducciones(Map<String, Set<List<String>>> antes,
                                                         Map<String, Set<List<String>>> despues) {
        List<String> eliminadas = new ArrayList<>();
        List<String> agregadas = new ArrayList<>();

        Set<String> variables = new TreeSet<>();
        variables.addAll(antes.keySet());
        variables.addAll(despues.keySet());

        for (String A : variables) {
            Set<List<String>> a = antes.getOrDefault(A, Collections.emptySet());
            Set<List<String>> d = despues.getOrDefault(A, Collections.emptySet());

            List<List<String>> soloAntes = new ArrayList<>();
            for (List<String> p : a) {
                if (!d.contains(p)) {
                    soloAntes.add(p);
                }
            }
            soloAntes.sort(Comparator.comparing(Grammar::formatoProduccion));
            for (List<String> p : soloAntes) {
                eliminadas.add(A + " -> " + Grammar.formatoProduccion(p));
            }

            List<List<String>> soloDespues = new ArrayList<>();
            for (List<String> p : d) {
                if (!a.contains(p)) {
                    soloDespues.add(p);
                }
            }
            soloDespues.sort(Comparator.comparing(Grammar::formatoProduccion));
            for (List<String> p : soloDespues) {
                agregadas.add(A + " -> " + Grammar.formatoProduccion(p));
            }
        }

        List<List<String>> resultado = new ArrayList<>();
        resultado.add(eliminadas);
        resultado.add(agregadas);
        return resultado;
    }

    // -----------------------------------------------------------------
    // producciones que generan epsilon
    // -----------------------------------------------------------------

    // una variable es "anulable" si puede llegar a producir la cadena
    // vacia, ya sea directo (A -> epsilon) o pasando por otras variables
    // que tambien sean anulables
    public static Set<String> calcularAnulables(Grammar g) {
        Set<String> anulables = new LinkedHashSet<>();

        for (String A : g.V) {
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                if (p.isEmpty()) {
                    anulables.add(A);
                    break;
                }
            }
        }

        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (String A : g.V) {
                if (anulables.contains(A)) {
                    continue;
                }
                for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                    if (!p.isEmpty() && anulables.containsAll(p)) {
                        anulables.add(A);
                        cambio = true;
                        break;
                    }
                }
            }
        }
        return anulables;
    }

    public static Grammar eliminarProduccionesNulas(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Set<String> anulables = calcularAnulables(g);
        Grammar nueva = g.copia();

        // caso especial: si el propio simbolo inicial es anulable, quiere
        // decir que epsilon pertenece al lenguaje. En ese caso no se puede
        // simplemente borrar la epsilon de S porque se perderia esa parte
        // del lenguaje, y tampoco conviene que S siga apareciendo del lado
        // derecho de otras producciones. La solucion de siempre es crear
        // un nuevo simbolo inicial (S1, por ejemplo) con S1 -> S | epsilon
        String nuevoInicial = null;
        if (anulables.contains(g.S)) {
            String s0 = nueva.nuevaVariable("S");
            nueva.V.add(s0);
            Set<List<String>> prodsS0 = new LinkedHashSet<>();
            prodsS0.add(new ArrayList<>(Collections.singletonList(g.S)));
            prodsS0.add(new ArrayList<>()); // epsilon
            nueva.P.put(s0, prodsS0);
            nueva.S = s0;
            nuevoInicial = s0;
        }

        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : nueva.V) {
            nuevaP.put(A, new LinkedHashSet<>());
        }
        if (nuevoInicial != null) {
            nuevaP.put(nuevoInicial, new LinkedHashSet<>(nueva.P.get(nuevoInicial)));
        }

        for (String A : g.V) {
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                if (p.isEmpty()) {
                    continue; // la epsilon original se descarta, ya se manejo arriba
                }

                List<Integer> posicionesAnulables = new ArrayList<>();
                for (int i = 0; i < p.size(); i++) {
                    if (anulables.contains(p.get(i))) {
                        posicionesAnulables.add(i);
                    }
                }

                // por cada subconjunto de posiciones anulables, se genera una
                // version de la produccion sin esos simbolos. Uso una mascara
                // de bits para recorrer todos los subconjuntos posibles
                int n = posicionesAnulables.size();
                for (int mascara = 0; mascara < (1 << n); mascara++) {
                    Set<Integer> aQuitar = new HashSet<>();
                    for (int bit = 0; bit < n; bit++) {
                        if ((mascara & (1 << bit)) != 0) {
                            aQuitar.add(posicionesAnulables.get(bit));
                        }
                    }
                    List<String> nuevaProd = new ArrayList<>();
                    for (int i = 0; i < p.size(); i++) {
                        if (!aQuitar.contains(i)) {
                            nuevaProd.add(p.get(i));
                        }
                    }
                    if (nuevaProd.isEmpty()) {
                        continue; // no volvemos a meter epsilon (salvo el caso especial de arriba)
                    }
                    nuevaP.computeIfAbsent(A, k -> new LinkedHashSet<>()).add(nuevaProd);
                }
            }
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);

        List<String> elementos = new ArrayList<>();
        if (anulables.isEmpty()) {
            elementos.add("No se identificaron variables anulables.");
        } else {
            List<String> ord = new ArrayList<>(anulables);
            Collections.sort(ord);
            elementos.add("Variables anulables identificadas: {" + String.join(", ", ord) + "}");
        }
        if (nuevoInicial != null) {
            elementos.add("El simbolo inicial '" + g.S + "' es anulable (epsilon pertenece al lenguaje). "
                    + "Se crea el nuevo simbolo inicial '" + nuevoInicial + "' con " + nuevoInicial + " -> "
                    + g.S + " | epsilon, para que el lenguaje no cambie y el simbolo inicial no quede "
                    + "apareciendo en el lado derecho de otras producciones.");
        }

        historial.agregar(new PasoHistorial("Eliminacion de producciones nulas", antes, elementos,
                diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // -----------------------------------------------------------------
    // producciones unitarias (A -> B)
    // -----------------------------------------------------------------

    public static Grammar eliminarProduccionesUnitarias(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Grammar nueva = g.copia();

        // para cada variable A, se calcula el conjunto de variables a las
        // que se puede llegar solo con cadenas de producciones unitarias
        // (A => B => C ...). Esto incluye a la propia A.
        Map<String, Set<String>> paresUnitarios = new LinkedHashMap<>();
        for (String A : g.V) {
            paresUnitarios.put(A, new LinkedHashSet<>(Collections.singleton(A)));
        }

        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (String A : g.V) {
                for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                    if (p.size() == 1 && g.V.contains(p.get(0))) {
                        String B = p.get(0);
                        Set<String> nuevos = new LinkedHashSet<>(paresUnitarios.get(A));
                        nuevos.addAll(paresUnitarios.getOrDefault(B, Collections.singleton(B)));
                        if (!nuevos.equals(paresUnitarios.get(A))) {
                            paresUnitarios.put(A, nuevos);
                            cambio = true;
                        }
                    }
                }
            }
        }

        List<String> identificadas = new ArrayList<>();
        for (String A : g.V) {
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                if (p.size() == 1 && g.V.contains(p.get(0))) {
                    identificadas.add(A + " -> " + p.get(0));
                }
            }
        }

        // ahora, las producciones de A pasan a ser la union de todas las
        // producciones "de verdad" (no unitarias) de cada B alcanzado
        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : g.V) {
            Set<List<String>> prods = new LinkedHashSet<>();
            for (String B : paresUnitarios.get(A)) {
                for (List<String> p : g.P.getOrDefault(B, Collections.emptySet())) {
                    if (p.size() == 1 && g.V.contains(p.get(0))) {
                        continue; // sigue siendo unitaria, no se copia
                    }
                    prods.add(p);
                }
            }
            nuevaP.put(A, prods);
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);
        List<String> elementos = new ArrayList<>();
        elementos.add(identificadas.isEmpty()
                ? "No se identificaron producciones unitarias."
                : "Producciones unitarias identificadas: " + String.join(", ", identificadas));

        historial.agregar(new PasoHistorial("Eliminacion de producciones unitarias", antes, elementos,
                diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // -----------------------------------------------------------------
    // variables que no generan nada
    // -----------------------------------------------------------------

    // una variable "genera" algo si tiene al menos una produccion formada
    // solo por terminales y/o variables que ya sabemos que generan algo
    public static Set<String> calcularGeneradoras(Grammar g) {
        Set<String> generadoras = new LinkedHashSet<>();
        boolean cambio = true;

        while (cambio) {
            cambio = false;
            for (String A : g.V) {
                if (generadoras.contains(A)) {
                    continue;
                }
                for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                    boolean sirve = true;
                    for (String s : p) {
                        if (!(g.T.contains(s) || generadoras.contains(s))) {
                            sirve = false;
                            break;
                        }
                    }
                    if (sirve) {
                        generadoras.add(A);
                        cambio = true;
                        break;
                    }
                }
            }
        }
        return generadoras;
    }

    public static Grammar eliminarVariablesInutiles(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Set<String> generadoras = calcularGeneradoras(g);
        Set<String> noGeneradoras = new LinkedHashSet<>(g.V);
        noGeneradoras.removeAll(generadoras);

        Grammar nueva = g.copia();
        nueva.V = new LinkedHashSet<>(generadoras);
        if (!nueva.V.contains(g.S)) {
            // esto pasaria si ni siquiera el simbolo inicial genera algo
            // (la gramatica genera el lenguaje vacio). Lo dejamos igual
            // para que quede explicito en el resultado
            nueva.V.add(g.S);
        }
        nueva.T = new LinkedHashSet<>(g.T);
        nueva.S = g.S;

        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : nueva.V) {
            Set<List<String>> validas = new LinkedHashSet<>();
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                boolean ok = true;
                for (String s : p) {
                    if (!(nueva.V.contains(s) || nueva.T.contains(s))) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    validas.add(p);
                }
            }
            nuevaP.put(A, validas);
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);

        List<String> genOrd = new ArrayList<>(generadoras);
        Collections.sort(genOrd);
        List<String> noGenOrd = new ArrayList<>(noGeneradoras);
        Collections.sort(noGenOrd);

        List<String> elementos = new ArrayList<>();
        elementos.add(generadoras.isEmpty()
                ? "No se identificaron variables generadoras."
                : "Variables generadoras: {" + String.join(", ", genOrd) + "}");
        elementos.add(noGeneradoras.isEmpty()
                ? "No se identificaron variables inutiles."
                : "Variables inutiles (no generadoras) eliminadas: {" + String.join(", ", noGenOrd) + "}");

        historial.agregar(new PasoHistorial("Eliminacion de variables inutiles (no generadoras)", antes,
                elementos, diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // -----------------------------------------------------------------
    // variables a las que no se puede llegar desde el simbolo inicial
    // -----------------------------------------------------------------

    public static Set<String> calcularAlcanzables(Grammar g) {
        Set<String> alcanzables = new LinkedHashSet<>();
        Deque<String> pendientes = new ArrayDeque<>();

        if (g.S != null) {
            alcanzables.add(g.S);
            pendientes.push(g.S);
        }

        while (!pendientes.isEmpty()) {
            String A = pendientes.pop();
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                for (String s : p) {
                    if (g.V.contains(s) && !alcanzables.contains(s)) {
                        alcanzables.add(s);
                        pendientes.push(s);
                    }
                }
            }
        }
        return alcanzables;
    }

    public static Grammar eliminarVariablesInalcanzables(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Set<String> alcanzables = calcularAlcanzables(g);
        Set<String> inalcanzables = new LinkedHashSet<>(g.V);
        inalcanzables.removeAll(alcanzables);

        Grammar nueva = g.copia();
        nueva.V = new LinkedHashSet<>(alcanzables);
        nueva.T = new LinkedHashSet<>(g.T);
        nueva.S = g.S;

        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : nueva.V) {
            nuevaP.put(A, new LinkedHashSet<>(g.P.getOrDefault(A, Collections.emptySet())));
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);

        List<String> alcOrd = new ArrayList<>(alcanzables);
        Collections.sort(alcOrd);
        List<String> inalcOrd = new ArrayList<>(inalcanzables);
        Collections.sort(inalcOrd);

        List<String> elementos = new ArrayList<>();
        elementos.add("Variables alcanzables desde " + g.S + ": {" + String.join(", ", alcOrd) + "}");
        elementos.add(inalcanzables.isEmpty()
                ? "No se identificaron variables inalcanzables."
                : "Variables inalcanzables eliminadas: {" + String.join(", ", inalcOrd) + "}");

        historial.agregar(new PasoHistorial("Eliminacion de variables inalcanzables", antes, elementos,
                diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // -----------------------------------------------------------------
    // sacar los terminales que esten metidos en producciones largas
    // -----------------------------------------------------------------

    public static Grammar sustituirTerminales(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Grammar nueva = g.copia();

        // aqui se va guardando que variable nueva le toco a cada terminal,
        // para no crear dos variables distintas para el mismo terminal
        Map<String, String> mapaTerminalVariable = new LinkedHashMap<>();
        List<String> nuevasVariables = new ArrayList<>();

        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : nueva.V) {
            nuevaP.put(A, new LinkedHashSet<>());
        }

        for (String A : g.V) {
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                if (p.size() <= 1) {
                    nuevaP.get(A).add(p);
                    continue;
                }

                List<String> nuevaSimbolos = new ArrayList<>();
                for (String s : p) {
                    if (g.T.contains(s)) {
                        String var = mapaTerminalVariable.get(s);
                        if (var == null) {
                            var = nueva.nuevaVariable("X");
                            nueva.V.add(var);
                            mapaTerminalVariable.put(s, var);
                            Set<List<String>> prodVar = new LinkedHashSet<>();
                            prodVar.add(new ArrayList<>(Collections.singletonList(s)));
                            nuevaP.put(var, prodVar);
                            nuevasVariables.add(var + " -> " + s);
                        }
                        nuevaSimbolos.add(var);
                    } else {
                        nuevaSimbolos.add(s);
                    }
                }
                nuevaP.get(A).add(nuevaSimbolos);
            }
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);

        List<String> terminalesOrd = new ArrayList<>(mapaTerminalVariable.keySet());
        Collections.sort(terminalesOrd);

        List<String> elementos = new ArrayList<>();
        elementos.add(mapaTerminalVariable.isEmpty()
                ? "No habia terminales en producciones de longitud mayor a uno."
                : "Terminales sustituidos en producciones de longitud > 1: {" + String.join(", ", terminalesOrd) + "}");
        if (!nuevasVariables.isEmpty()) {
            elementos.add("Nuevas variables creadas: " + String.join(", ", nuevasVariables));
        }

        historial.agregar(new PasoHistorial("Sustitucion de terminales en producciones largas", antes,
                elementos, diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // -----------------------------------------------------------------
    // partir en binario las producciones de mas de 2 simbolos
    // -----------------------------------------------------------------

    public static Grammar reducirProduccionesLargas(Grammar g, Historial historial) {
        Grammar antes = g.copia();
        Grammar nueva = g.copia();
        List<String> nuevasVariables = new ArrayList<>();

        // guardo aqui que variable le toco a cada "cola" de simbolos ya
        // procesada, para no crear una variable auxiliar distinta cuando
        // dos producciones diferentes terminan igual (evita duplicados)
        Map<List<String>, String> cacheColas = new LinkedHashMap<>();

        Map<String, Set<List<String>>> nuevaP = new LinkedHashMap<>();
        for (String A : nueva.V) {
            nuevaP.put(A, new LinkedHashSet<>());
        }

        for (String A : g.V) {
            for (List<String> p : g.P.getOrDefault(A, Collections.emptySet())) {
                if (p.size() <= 2) {
                    nuevaP.get(A).add(p);
                    continue;
                }
                String varResto = variableParaCola(new ArrayList<>(p.subList(1, p.size())),
                        nueva, nuevaP, cacheColas, nuevasVariables);
                List<String> nuevaProd = new ArrayList<>();
                nuevaProd.add(p.get(0));
                nuevaProd.add(varResto);
                nuevaP.get(A).add(nuevaProd);
            }
        }
        nueva.P = nuevaP;

        List<List<String>> diff = diffProducciones(g.P, nueva.P);

        boolean habiaLargas = false;
        for (Set<List<String>> prods : g.P.values()) {
            for (List<String> p : prods) {
                if (p.size() > 2) {
                    habiaLargas = true;
                }
            }
        }

        List<String> elementos = new ArrayList<>();
        elementos.add(habiaLargas
                ? "Producciones con mas de dos simbolos en el lado derecho fueron convertidas a producciones binarias."
                : "No habia producciones con mas de dos simbolos en el lado derecho.");

        historial.agregar(new PasoHistorial("Reduccion de producciones largas a forma binaria", antes,
                elementos, diff.get(0), diff.get(1), nueva.copia()));
        return nueva;
    }

    // dada una lista de 2 o mas simbolos (la "cola" de una produccion
    // larga), devuelve la variable que la representa, creandola si hace
    // falta. Si ya existe una variable para esa misma cola, se reutiliza.
    private static String variableParaCola(List<String> cola, Grammar nueva,
                                            Map<String, Set<List<String>>> nuevaP,
                                            Map<List<String>, String> cache, List<String> nuevasVariables) {
        String yaExiste = cache.get(cola);
        if (yaExiste != null) {
            return yaExiste;
        }

        if (cola.size() == 2) {
            String nuevaVar = nueva.nuevaVariable("X");
            nueva.V.add(nuevaVar);
            Set<List<String>> prod = new LinkedHashSet<>();
            prod.add(new ArrayList<>(cola));
            nuevaP.put(nuevaVar, prod);
            nuevasVariables.add(nuevaVar + " -> " + String.join(" ", cola));
            cache.put(cola, nuevaVar);
            return nuevaVar;
        }

        String restoVar = variableParaCola(new ArrayList<>(cola.subList(1, cola.size())), nueva, nuevaP,
                cache, nuevasVariables);
        String nuevaVar = nueva.nuevaVariable("X");
        nueva.V.add(nuevaVar);
        List<String> prodLista = new ArrayList<>();
        prodLista.add(cola.get(0));
        prodLista.add(restoVar);
        Set<List<String>> prod = new LinkedHashSet<>();
        prod.add(prodLista);
        nuevaP.put(nuevaVar, prod);
        nuevasVariables.add(nuevaVar + " -> " + cola.get(0) + " " + restoVar);
        cache.put(cola, nuevaVar);
        return nuevaVar;
    }

    // -----------------------------------------------------------------
    // chequeo final: la gramatica que quedo, ¿esta realmente en FNC?
    // -----------------------------------------------------------------

    public static boolean esFnc(Grammar g) {
        for (Map.Entry<String, Set<List<String>>> entrada : g.P.entrySet()) {
            String A = entrada.getKey();
            for (List<String> p : entrada.getValue()) {
                if (p.isEmpty()) {
                    // solo se permite si es el simbolo inicial y no aparece
                    // en el lado derecho de ninguna otra produccion
                    if (A.equals(g.S) && !apareceEnLadoDerecho(g, A)) {
                        continue;
                    }
                    return false;
                } else if (p.size() == 1) {
                    if (!g.T.contains(p.get(0))) {
                        return false;
                    }
                } else if (p.size() == 2) {
                    if (!g.V.contains(p.get(0)) || !g.V.contains(p.get(1))) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean apareceEnLadoDerecho(Grammar g, String simbolo) {
        for (Set<List<String>> prods : g.P.values()) {
            for (List<String> p : prods) {
                if (p.contains(simbolo)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // corre todo el proceso completo, en el orden que tiene que ir
    // -----------------------------------------------------------------

    public static Grammar convertirAFnc(Grammar g, Historial historial) {
        Grammar g1 = eliminarProduccionesNulas(g, historial);
        Grammar g2 = eliminarProduccionesUnitarias(g1, historial);
        Grammar g3 = eliminarVariablesInutiles(g2, historial);
        Grammar g4 = eliminarVariablesInalcanzables(g3, historial);
        Grammar g5 = sustituirTerminales(g4, historial);
        return reducirProduccionesLargas(g5, historial);
    }
}
