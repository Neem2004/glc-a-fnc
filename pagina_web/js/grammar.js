/*
 * grammar.js
 * Representacion de una gramatica libre de contexto G = (V, T, P, S).
 * Es una traduccion fiel de la clase Grammar.java del proyecto original.
 *
 * Convencion: cada variable y cada terminal es de UN SOLO CARACTER
 * (S, A, B... / a, b, 0, 1...). La cadena vacia se escribe "epsilon"
 * (o "landa", o "e"). Por dentro se guarda como una produccion vacia [].
 *
 * Este archivo NO usa modulos ES a proposito, para que la pagina funcione
 * incluso abriendola directamente con doble clic (file://).
 */

// Palabras que el usuario puede escribir para representar epsilon.
const EPSILON_TOKENS = new Set(['epsilon', 'landa', 'e']);

// Error de estructura (falta la flecha, lado izquierdo vacio, etc.).
class GrammarError extends Error {}

// Comparador de cadenas con el orden natural (igual que String.compareTo en Java).
function cmpStr(a, b) {
  return a < b ? -1 : (a > b ? 1 : 0);
}

// Clave interna para comparar producciones (listas de simbolos) por valor.
function prodKey(prod) {
  return prod.join('\u0000');
}

/*
 * Conjunto de producciones "con unicidad", imitando el Set<List<String>>
 * de Java (que deduplica listas por su contenido, no por referencia).
 */
class ProdSet {
  constructor(items) {
    this.items = [];
    if (items) {
      for (const it of items) this.add(it);
    }
  }
  add(prod) {
    const k = prodKey(prod);
    for (const it of this.items) {
      if (prodKey(it) === k) return false;
    }
    this.items.push(prod.slice());
    return true;
  }
  has(prod) {
    const k = prodKey(prod);
    return this.items.some((it) => prodKey(it) === k);
  }
  get size() {
    return this.items.length;
  }
  toArray() {
    return this.items.slice();
  }
  [Symbol.iterator]() {
    return this.items[Symbol.iterator]();
  }
}

// Convierte "S,A, B" o "S A B" en un conjunto {"S","A","B"}.
function parseListaSimbolos(texto) {
  const out = new Set();
  const tokens = String(texto == null ? '' : texto).replace(/,/g, ' ').trim().split(/\s+/);
  for (const tok of tokens) {
    if (tok !== '') out.add(tok);
  }
  return out;
}

/*
 * Tokeniza el lado derecho de una produccion.
 * - Si hay espacios, se respeta la separacion escrita.
 * - Si no hay espacios, se intenta emparejar de forma "greedy" contra los
 *   simbolos ya declarados (variables + terminales), de mas largo a mas
 *   corto. Asi "ASA" -> A,S,A y "X1X2" -> X1,X2 sin ambiguedad.
 *   Si un caracter no coincide con nada declarado, se toma como simbolo
 *   suelto (y luego la validacion avisara que no fue declarado).
 */
function tokenizarRHS(alt, conocidosOrdenados) {
  if (/\s/.test(alt)) {
    return alt.split(/\s+/).filter((s) => s !== '');
  }
  const tokens = [];
  let i = 0;
  while (i < alt.length) {
    let matched = null;
    for (const s of conocidosOrdenados) {
      if (s.length > 0 && alt.startsWith(s, i)) {
        matched = s;
        break;
      }
    }
    if (matched !== null) {
      tokens.push(matched);
      i += matched.length;
    } else {
      tokens.push(alt.charAt(i));
      i += 1;
    }
  }
  return tokens;
}

// Da formato a una produccion: "epsilon", "aB" o "A X1" si hay multi-caracter.
function formatoProduccion(p) {
  if (p.length === 0) return 'epsilon';
  let todosUnCaracter = true;
  for (const s of p) {
    if (s.length !== 1) {
      todosUnCaracter = false;
      break;
    }
  }
  return todosUnCaracter ? p.join('') : p.join(' ');
}

class Grammar {
  constructor() {
    this.V = new Set();
    this.T = new Set();
    this.P = new Map(); // nombre -> ProdSet
    this.S = '';
  }

  // Construye la gramatica a partir de lo que escribe el usuario.
  static desdeEntrada(variablesTxt, terminalesTxt, inicialTxt, produccionesTxt) {
    const V = parseListaSimbolos(variablesTxt);
    const T = parseListaSimbolos(terminalesTxt);
    const S = String(inicialTxt == null ? '' : inicialTxt).trim();

    const conocidos = new Set();
    for (const v of V) conocidos.add(v);
    for (const t of T) conocidos.add(t);
    const conocidosOrdenados = Array.from(conocidos)
      .filter((s) => s.length > 0)
      .sort((a, b) => (b.length - a.length) || cmpStr(a, b));

    const g = new Grammar();
    g.V = V;
    g.T = T;
    g.S = S;
    for (const v of V) g.P.set(v, new ProdSet());

    const lineas = String(produccionesTxt == null ? '' : produccionesTxt).split('\n');
    for (const lineaRaw of lineas) {
      const linea = lineaRaw.trim();
      if (linea === '') continue;

      const idx = linea.indexOf('->');
      if (idx < 0) {
        throw new GrammarError("Producción con estructura inválida (falta '->'): '" + linea + "'");
      }

      const izq = linea.substring(0, idx).trim();
      const der = linea.substring(idx + 2).trim();

      if (izq === '') {
        throw new GrammarError("Producción sin variable en el lado izquierdo: '" + linea + "'");
      }

      if (!g.P.has(izq)) g.P.set(izq, new ProdSet());

      const alternativas = der.split('|');
      for (const altRaw of alternativas) {
        const alt = altRaw.trim();
        if (alt === '' || EPSILON_TOKENS.has(alt.toLowerCase())) {
          g.P.get(izq).add([]); // epsilon
          continue;
        }
        g.P.get(izq).add(tokenizarRHS(alt, conocidosOrdenados));
      }
    }
    return g;
  }

  // Devuelve la lista de errores encontrados (vacia si todo esta bien).
  validar() {
    const errores = [];

    if (this.V.size === 0) errores.push('Error: la gramática debe tener al menos una variable.');
    if (this.T.size === 0) errores.push('Error: la gramática debe tener al menos un terminal.');

    if (this.S == null || this.S === '') {
      errores.push('Error: no se definió un símbolo inicial.');
    } else if (!this.V.has(this.S)) {
      errores.push('Error: el símbolo inicial ' + this.S + ' no pertenece al conjunto de variables.');
    }

    const repetidos = [];
    for (const s of this.V) {
      if (this.T.has(s)) repetidos.push(s);
    }
    if (repetidos.length) {
      errores.push('Error: hay símbolos declarados a la vez como variable y como terminal: '
        + repetidos.sort(cmpStr).join(', '));
    }

    for (const [A, prods] of this.P) {
      if (!this.V.has(A)) {
        errores.push("Error: la producción con lado izquierdo '" + A + "' no es una variable válida.");
        continue;
      }
      for (const p of prods) {
        for (const s of p) {
          if (!this.V.has(s) && !this.T.has(s)) {
            errores.push("Error: el símbolo '" + s + "' usado en la producción " + A + " -> "
              + p.join('') + " no fue declarado como variable ni como terminal.");
          }
        }
      }
    }

    return errores;
  }

  esValida() {
    return this.validar().length === 0;
  }

  // Copia completa (profunda).
  copia() {
    const g = new Grammar();
    g.V = new Set(this.V);
    g.T = new Set(this.T);
    g.S = this.S;
    for (const [A, prods] of this.P) {
      const ps = new ProdSet();
      for (const p of prods) ps.add(p);
      g.P.set(A, ps);
    }
    return g;
  }

  // Nombre de variable que todavia no se usa (X1, X2...).
  nuevaVariable(prefijo) {
    let i = 1;
    while (true) {
      const candidato = prefijo + i;
      if (!this.V.has(candidato)) return candidato;
      i++;
    }
  }

  produccionesComoTexto() {
    const lineas = [];
    const variables = Array.from(this.V).sort(cmpStr);

    for (const A of variables) {
      const prods = this.P.get(A);
      if (!prods || prods.size === 0) continue;
      const alternativas = [];
      for (const p of prods) alternativas.push(formatoProduccion(p));
      alternativas.sort(cmpStr);
      lineas.push(A + ' -> ' + alternativas.join(' | '));
    }
    return lineas;
  }

  toString() {
    const vOrd = Array.from(this.V).sort(cmpStr);
    const tOrd = Array.from(this.T).sort(cmpStr);

    let sb = '';
    sb += 'V = {' + vOrd.join(', ') + '}\n';
    sb += 'T = {' + tOrd.join(', ') + '}\n';
    sb += 'S = ' + this.S + '\n';
    sb += 'P:\n';

    const lineas = this.produccionesComoTexto();
    if (lineas.length === 0) {
      sb += '  (sin producciones)';
    } else {
      sb += lineas.map((l) => '  ' + l).join('\n');
    }
    return sb;
  }

  totalProducciones() {
    let total = 0;
    for (const prods of this.P.values()) total += prods.size;
    return total;
  }
}
