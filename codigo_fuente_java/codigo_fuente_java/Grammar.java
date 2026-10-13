import java.util.*;

/*
 * Esta clase representa la gramatica libre de contexto como tal:
 * G = (V, T, P, S). Aqui se guardan las variables, los terminales,
 * el simbolo inicial y las producciones, y tambien se hace el parseo
 * del texto que escribe el usuario y la validacion inicial.
 *
 * Ojo con esto: para que el programa pueda procesar cualquier gramatica
 * (y no solo las que uno ya probo a mano), cada variable y cada terminal
 * tiene que ser de UN SOLO CARACTER. Es la misma convencion que se usa
 * casi siempre en los ejercicios de la materia (S, A, B... para variables,
 * a, b, 0, 1... para terminales). Con esa regla, una produccion como "ASA"
 * se puede separar sin ambiguedad en los simbolos A, S, A.
 *
 * Para la cadena vacia se escribe la palabra "epsilon" (o "landa", o
 * simplemente "e") del lado derecho de una produccion. Por dentro se
 * guarda como una lista vacia.
 */
public class Grammar {

    // palabras que el usuario puede escribir para representar epsilon
    static final Set<String> EPSILON_TOKENS =
            new HashSet<>(Arrays.asList("epsilon", "landa", "e"));

    public Set<String> V = new LinkedHashSet<>();
    public Set<String> T = new LinkedHashSet<>();
    // cada variable apunta a un conjunto de producciones; cada produccion
    // es una lista de simbolos (si la lista esta vacia, es epsilon)
    public Map<String, Set<List<String>>> P = new LinkedHashMap<>();
    public String S;

    public Grammar() {
    }

    public Grammar(Set<String> v, Set<String> t, String s) {
        this.V = new LinkedHashSet<>(v);
        this.T = new LinkedHashSet<>(t);
        this.S = s;
        for (String var : this.V) {
            P.put(var, new LinkedHashSet<>());
        }
    }

    // convierte algo como "S,A, B" o "S A B" en un set {"S","A","B"}
    static Set<String> parseListaSimbolos(String texto) {
        Set<String> out = new LinkedHashSet<>();
        String[] tokens = texto.replace(",", " ").trim().split("\\s+");
        for (String tok : tokens) {
            if (!tok.isEmpty()) {
                out.add(tok);
            }
        }
        return out;
    }

    // construye la gramatica a partir de lo que el usuario escribe en pantalla
    public static Grammar desdeEntrada(String variablesTxt, String terminalesTxt,
                                        String inicialTxt, String produccionesTxt) throws GrammarError {
        Set<String> V = parseListaSimbolos(variablesTxt);
        Set<String> T = parseListaSimbolos(terminalesTxt);
        String S = inicialTxt.trim();

        Grammar g = new Grammar(V, T, S);

        String[] lineas = produccionesTxt.split("\n");
        for (String lineaRaw : lineas) {
            String linea = lineaRaw.trim();
            if (linea.isEmpty()) {
                continue;
            }

            int idx = linea.indexOf("->");
            if (idx < 0) {
                // si no tiene la flecha, no hay forma de saber donde termina
                // el lado izquierdo, asi que tocaria avisarle al usuario
                throw new GrammarError("Produccion con estructura invalida (falta '->'): '" + linea + "'");
            }

            String izq = linea.substring(0, idx).trim();
            String der = linea.substring(idx + 2).trim();

            if (izq.isEmpty()) {
                throw new GrammarError("Produccion sin variable en el lado izquierdo: '" + linea + "'");
            }

            if (!g.P.containsKey(izq)) {
                g.P.put(izq, new LinkedHashSet<>());
            }

            // las alternativas van separadas por "|"
            String[] alternativas = der.split("\\|");
            for (String altRaw : alternativas) {
                String alt = altRaw.trim();

                if (alt.isEmpty() || EPSILON_TOKENS.contains(alt.toLowerCase())) {
                    g.P.get(izq).add(new ArrayList<>()); // epsilon
                    continue;
                }

                List<String> simbolos = new ArrayList<>();
                if (alt.contains(" ")) {
                    // el usuario separo con espacios (util si algun simbolo
                    // tuviera mas de un caracter, aunque aqui no se use)
                    for (String s : alt.split("\\s+")) {
                        simbolos.add(s);
                    }
                } else {
                    // caso normal: cada caracter es un simbolo distinto
                    for (int i = 0; i < alt.length(); i++) {
                        simbolos.add(String.valueOf(alt.charAt(i)));
                    }
                }
                g.P.get(izq).add(simbolos);
            }
        }
        return g;
    }

    // revisa que la gramatica este bien formada antes de dejar que se
    // procese. devuelve una lista con los errores encontrados (vacia si
    // todo esta bien)
    public List<String> validar() {
        List<String> errores = new ArrayList<>();

        if (V.isEmpty()) {
            errores.add("Error: la gramatica debe tener al menos una variable.");
        }
        if (T.isEmpty()) {
            errores.add("Error: la gramatica debe tener al menos un terminal.");
        }

        if (S == null || S.isEmpty()) {
            errores.add("Error: no se definio un simbolo inicial.");
        } else if (!V.contains(S)) {
            errores.add("Error: el simbolo inicial " + S + " no pertenece al conjunto de variables.");
        }

        // que no haya un simbolo que este declarado como variable y como
        // terminal al mismo tiempo, eso no tendria sentido
        Set<String> repetidos = new LinkedHashSet<>(V);
        repetidos.retainAll(T);
        if (!repetidos.isEmpty()) {
            errores.add("Error: hay simbolos declarados a la vez como variable y como terminal: "
                    + String.join(", ", new TreeSet<>(repetidos)));
        }

        for (Map.Entry<String, Set<List<String>>> entrada : P.entrySet()) {
            String A = entrada.getKey();
            if (!V.contains(A)) {
                errores.add("Error: la produccion con lado izquierdo '" + A + "' no es una variable valida.");
                continue;
            }
            for (List<String> p : entrada.getValue()) {
                for (String s : p) {
                    if (!V.contains(s) && !T.contains(s)) {
                        errores.add("Error: el simbolo '" + s + "' usado en la produccion " + A + " -> "
                                + String.join("", p) + " no fue declarado como variable ni como terminal.");
                    }
                }
            }
        }

        return errores;
    }

    public boolean esValida() {
        return validar().isEmpty();
    }

    // hace una copia completa (no superficial) de la gramatica, para que
    // al aplicar una transformacion no se altere la original por accidente
    public Grammar copia() {
        Grammar g = new Grammar();
        g.V = new LinkedHashSet<>(this.V);
        g.T = new LinkedHashSet<>(this.T);
        g.S = this.S;

        for (Map.Entry<String, Set<List<String>>> entrada : this.P.entrySet()) {
            Set<List<String>> copiaProds = new LinkedHashSet<>();
            for (List<String> p : entrada.getValue()) {
                copiaProds.add(new ArrayList<>(p));
            }
            g.P.put(entrada.getKey(), copiaProds);
        }
        return g;
    }

    // genera un nombre de variable que todavia no se este usando (X1, X2...)
    public String nuevaVariable(String prefijo) {
        int i = 1;
        while (true) {
            String candidato = prefijo + i;
            if (!V.contains(candidato)) {
                return candidato;
            }
            i++;
        }
    }

    static String formatoProduccion(List<String> p) {
        if (p.isEmpty()) {
            return "epsilon";
        }
        boolean todosUnCaracter = true;
        for (String s : p) {
            if (s.length() != 1) {
                todosUnCaracter = false;
                break;
            }
        }
        return todosUnCaracter ? String.join("", p) : String.join(" ", p);
    }

    public List<String> produccionesComoTexto() {
        List<String> lineas = new ArrayList<>();
        List<String> variables = new ArrayList<>(V);
        Collections.sort(variables);

        for (String A : variables) {
            Set<List<String>> prods = P.get(A);
            if (prods == null || prods.isEmpty()) {
                continue;
            }
            List<String> alternativas = new ArrayList<>();
            for (List<String> p : prods) {
                alternativas.add(formatoProduccion(p));
            }
            Collections.sort(alternativas);
            lineas.add(A + " -> " + String.join(" | ", alternativas));
        }
        return lineas;
    }

    @Override
    public String toString() {
        List<String> vOrd = new ArrayList<>(V);
        Collections.sort(vOrd);
        List<String> tOrd = new ArrayList<>(T);
        Collections.sort(tOrd);

        StringBuilder sb = new StringBuilder();
        sb.append("V = {").append(String.join(", ", vOrd)).append("}\n");
        sb.append("T = {").append(String.join(", ", tOrd)).append("}\n");
        sb.append("S = ").append(S).append("\n");
        sb.append("P:\n");

        List<String> lineas = produccionesComoTexto();
        if (lineas.isEmpty()) {
            sb.append("  (sin producciones)");
        } else {
            for (String l : lineas) {
                sb.append("  ").append(l).append("\n");
            }
            sb.setLength(sb.length() - 1); // le quito el ultimo salto de linea
        }
        return sb.toString();
    }

    public int totalProducciones() {
        int total = 0;
        for (Set<List<String>> prods : P.values()) {
            total += prods.size();
        }
        return total;
    }
}
