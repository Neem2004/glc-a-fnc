/*
 * transformations.js
 * Algoritmos de depuracion y conversion a Forma Normal de Chomsky.
 * Traduccion fiel de Transformations.java.
 *
 * Cada funcion recibe una gramatica y el historial, y devuelve una
 * gramatica NUEVA (nunca modifica la que llega), para que el historial
 * pueda mostrar el "antes" y el "despues" de cada paso.
 */

// Diferencias entre dos mapeos de producciones: [eliminadas, agregadas].
function diffProducciones(antes, despues) {
  const eliminadas = [];
  const agregadas = [];

  const variables = new Set();
  for (const k of antes.keys()) variables.add(k);
  for (const k of despues.keys()) variables.add(k);
  const vars = Array.from(variables).sort(cmpStr);

  for (const A of vars) {
    const a = antes.get(A) || new ProdSet();
    const d = despues.get(A) || new ProdSet();

    const soloAntes = [];
    for (const p of a) if (!d.has(p)) soloAntes.push(p);
    soloAntes.sort((x, y) => cmpStr(formatoProduccion(x), formatoProduccion(y)));
    for (const p of soloAntes) eliminadas.push(A + ' -> ' + formatoProduccion(p));

    const soloDespues = [];
    for (const p of d) if (!a.has(p)) soloDespues.push(p);
    soloDespues.sort((x, y) => cmpStr(formatoProduccion(x), formatoProduccion(y)));
    for (const p of soloDespues) agregadas.push(A + ' -> ' + formatoProduccion(p));
  }

  return [eliminadas, agregadas];
}

// ---------------------------------------------------------------
// producciones nulas (epsilon)
// ---------------------------------------------------------------

// La eliminacion de producciones nulas combina cada subconjunto de los
// simbolos anulables de una produccion (2^n combinaciones). Con muchas
// posiciones anulables eso crece muy rapido y congelaria el navegador.
// Este tope solo actua en casos patologicos; no cambia el resultado de
// las gramaticas normales.
const MAX_POSICIONES_ANULABLES = 20;

function calcularAnulables(g) {
  const anulables = new Set();

  for (const A of g.V) {
    for (const p of (g.P.get(A) || [])) {
      if (p.length === 0) {
        anulables.add(A);
        break;
      }
    }
  }

  let cambio = true;
  while (cambio) {
    cambio = false;
    for (const A of g.V) {
      if (anulables.has(A)) continue;
      for (const p of (g.P.get(A) || [])) {
        if (p.length > 0 && p.every((s) => anulables.has(s))) {
          anulables.add(A);
          cambio = true;
          break;
        }
      }
    }
  }
  return anulables;
}

function eliminarProduccionesNulas(g, historial) {
  const antes = g.copia();
  const anulables = calcularAnulables(g);
  const nueva = g.copia();

  let nuevoInicial = null;
  if (anulables.has(g.S)) {
    const s0 = nueva.nuevaVariable('S');
    nueva.V.add(s0);
    const prodsS0 = new ProdSet();
    prodsS0.add([g.S]);
    prodsS0.add([]); // epsilon
    nueva.P.set(s0, prodsS0);
    nueva.S = s0;
    nuevoInicial = s0;
  }

  const nuevaP = new Map();
  for (const A of nueva.V) nuevaP.set(A, new ProdSet());
  if (nuevoInicial !== null) {
    const ps = new ProdSet();
    for (const p of nueva.P.get(nuevoInicial)) ps.add(p);
    nuevaP.set(nuevoInicial, ps);
  }

  for (const A of g.V) {
    for (const p of (g.P.get(A) || [])) {
      if (p.length === 0) continue; // la epsilon original se maneja arriba

      const posiciones = [];
      for (let i = 0; i < p.length; i++) {
        if (anulables.has(p[i])) posiciones.push(i);
      }

      const n = posiciones.length;
      if (n > MAX_POSICIONES_ANULABLES) {
        throw new Error('La producción ' + A + ' -> ' + formatoProduccion(p) + ' tiene ' + n
          + ' símbolos anulables y generaría más de '
          + Math.pow(2, n).toLocaleString('es') + ' combinaciones. '
          + 'Se alcanzó el límite seguro del navegador; simplifica esa producción o divídela.');
      }
      for (let mascara = 0; mascara < (1 << n); mascara++) {
        const aQuitar = new Set();
        for (let bit = 0; bit < n; bit++) {
          if (mascara & (1 << bit)) aQuitar.add(posiciones[bit]);
        }
        const nuevaProd = [];
        for (let i = 0; i < p.length; i++) {
          if (!aQuitar.has(i)) nuevaProd.push(p[i]);
        }
        if (nuevaProd.length === 0) continue; // no reinsertamos epsilon
        if (!nuevaP.has(A)) nuevaP.set(A, new ProdSet());
        nuevaP.get(A).add(nuevaProd);
      }
    }
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);

  const elementos = [];
  if (anulables.size === 0) {
    elementos.push('No se identificaron variables anulables.');
  } else {
    elementos.push('Variables anulables identificadas: {'
      + Array.from(anulables).sort(cmpStr).join(', ') + '}');
  }
  if (nuevoInicial !== null) {
    elementos.push("El símbolo inicial '" + g.S + "' es anulable (epsilon pertenece al lenguaje). "
      + "Se crea el nuevo símbolo inicial '" + nuevoInicial + "' con " + nuevoInicial + " -> "
      + g.S + " | epsilon, para que el lenguaje no cambie y el símbolo inicial no quede "
      + "apareciendo en el lado derecho de otras producciones.");
  }

  historial.agregar(new PasoHistorial('Eliminación de producciones nulas', antes, elementos,
    diff[0], diff[1], nueva.copia()));
  return nueva;
}

// ---------------------------------------------------------------
// producciones unitarias (A -> B)
// ---------------------------------------------------------------

function eliminarProduccionesUnitarias(g, historial) {
  const antes = g.copia();
  const nueva = g.copia();

  const paresUnitarios = new Map();
  for (const A of g.V) paresUnitarios.set(A, new Set([A]));

  let cambio = true;
  while (cambio) {
    cambio = false;
    for (const A of g.V) {
      for (const p of (g.P.get(A) || [])) {
        if (p.length === 1 && g.V.has(p[0])) {
          const B = p[0];
          const actual = paresUnitarios.get(A);
          const nuevos = new Set(actual);
          const deB = paresUnitarios.get(B) || new Set([B]);
          for (const x of deB) nuevos.add(x);
          if (nuevos.size !== actual.size) {
            paresUnitarios.set(A, nuevos);
            cambio = true;
          }
        }
      }
    }
  }

  const identificadas = [];
  for (const A of g.V) {
    for (const p of (g.P.get(A) || [])) {
      if (p.length === 1 && g.V.has(p[0])) {
        identificadas.push(A + ' -> ' + p[0]);
      }
    }
  }

  const nuevaP = new Map();
  for (const A of g.V) {
    const prods = new ProdSet();
    for (const B of paresUnitarios.get(A)) {
      for (const p of (g.P.get(B) || [])) {
        if (p.length === 1 && g.V.has(p[0])) continue; // sigue siendo unitaria
        prods.add(p);
      }
    }
    nuevaP.set(A, prods);
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);
  const elementos = [];
  elementos.push(identificadas.length === 0
    ? 'No se identificaron producciones unitarias.'
    : 'Producciones unitarias identificadas: ' + identificadas.join(', '));

  historial.agregar(new PasoHistorial('Eliminación de producciones unitarias', antes, elementos,
    diff[0], diff[1], nueva.copia()));
  return nueva;
}

// ---------------------------------------------------------------
// variables que no generan nada
// ---------------------------------------------------------------

function calcularGeneradoras(g) {
  const generadoras = new Set();
  let cambio = true;

  while (cambio) {
    cambio = false;
    for (const A of g.V) {
      if (generadoras.has(A)) continue;
      for (const p of (g.P.get(A) || [])) {
        let sirve = true;
        for (const s of p) {
          if (!(g.T.has(s) || generadoras.has(s))) {
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

function eliminarVariablesInutiles(g, historial) {
  const antes = g.copia();
  const generadoras = calcularGeneradoras(g);
  const noGeneradoras = [];
  for (const v of g.V) if (!generadoras.has(v)) noGeneradoras.push(v);

  const nueva = g.copia();
  const nuevasVars = new Set();
  for (const v of g.V) if (generadoras.has(v)) nuevasVars.add(v);
  if (!nuevasVars.has(g.S)) nuevasVars.add(g.S);
  nueva.V = nuevasVars;
  nueva.T = new Set(g.T);
  nueva.S = g.S;

  const nuevaP = new Map();
  for (const A of nueva.V) {
    const validas = new ProdSet();
    for (const p of (g.P.get(A) || [])) {
      let ok = true;
      for (const s of p) {
        if (!(nueva.V.has(s) || nueva.T.has(s))) {
          ok = false;
          break;
        }
      }
      if (ok) validas.add(p);
    }
    nuevaP.set(A, validas);
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);

  const genOrd = Array.from(generadoras).sort(cmpStr);
  const noGenOrd = noGeneradoras.slice().sort(cmpStr);

  const elementos = [];
  elementos.push(generadoras.size === 0
    ? 'No se identificaron variables generadoras.'
    : 'Variables generadoras: {' + genOrd.join(', ') + '}');
  elementos.push(noGeneradoras.length === 0
    ? 'No se identificaron variables inútiles.'
    : 'Variables inútiles (no generadoras) eliminadas: {' + noGenOrd.join(', ') + '}');

  historial.agregar(new PasoHistorial('Eliminación de variables inútiles (no generadoras)', antes,
    elementos, diff[0], diff[1], nueva.copia()));
  return nueva;
}

// ---------------------------------------------------------------
// variables inalcanzables desde el simbolo inicial
// ---------------------------------------------------------------

function calcularAlcanzables(g) {
  const alcanzables = new Set();
  const pendientes = [];

  if (g.S) {
    alcanzables.add(g.S);
    pendientes.push(g.S);
  }

  while (pendientes.length) {
    const A = pendientes.pop();
    for (const p of (g.P.get(A) || [])) {
      for (const s of p) {
        if (g.V.has(s) && !alcanzables.has(s)) {
          alcanzables.add(s);
          pendientes.push(s);
        }
      }
    }
  }
  return alcanzables;
}

function eliminarVariablesInalcanzables(g, historial) {
  const antes = g.copia();
  const alcanzables = calcularAlcanzables(g);
  const inalcanzables = [];
  for (const v of g.V) if (!alcanzables.has(v)) inalcanzables.push(v);

  const nueva = g.copia();
  const nuevasVars = new Set();
  for (const v of g.V) if (alcanzables.has(v)) nuevasVars.add(v);
  nueva.V = nuevasVars;
  nueva.T = new Set(g.T);
  nueva.S = g.S;

  const nuevaP = new Map();
  for (const A of nueva.V) {
    const ps = new ProdSet();
    for (const p of (g.P.get(A) || [])) ps.add(p);
    nuevaP.set(A, ps);
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);

  const alcOrd = Array.from(alcanzables).sort(cmpStr);
  const inalcOrd = inalcanzables.slice().sort(cmpStr);

  const elementos = [];
  elementos.push('Variables alcanzables desde ' + g.S + ': {' + alcOrd.join(', ') + '}');
  elementos.push(inalcanzables.length === 0
    ? 'No se identificaron variables inalcanzables.'
    : 'Variables inalcanzables eliminadas: {' + inalcOrd.join(', ') + '}');

  historial.agregar(new PasoHistorial('Eliminación de variables inalcanzables', antes, elementos,
    diff[0], diff[1], nueva.copia()));
  return nueva;
}

// ---------------------------------------------------------------
// sustitucion de terminales en producciones largas
// ---------------------------------------------------------------

function sustituirTerminales(g, historial) {
  const antes = g.copia();
  const nueva = g.copia();

  const mapaTerminalVariable = new Map();
  const nuevasVariables = [];

  const nuevaP = new Map();
  for (const A of nueva.V) nuevaP.set(A, new ProdSet());

  for (const A of g.V) {
    if (!nuevaP.has(A)) nuevaP.set(A, new ProdSet());
    for (const p of (g.P.get(A) || [])) {
      if (p.length <= 1) {
        nuevaP.get(A).add(p);
        continue;
      }

      const nuevaSimbolos = [];
      for (const s of p) {
        if (g.T.has(s)) {
          let vari = mapaTerminalVariable.get(s);
          if (vari === undefined) {
            vari = nueva.nuevaVariable('X');
            nueva.V.add(vari);
            mapaTerminalVariable.set(s, vari);
            const ps = new ProdSet();
            ps.add([s]);
            nuevaP.set(vari, ps);
            nuevasVariables.push(vari + ' -> ' + s);
          }
          nuevaSimbolos.push(vari);
        } else {
          nuevaSimbolos.push(s);
        }
      }
      nuevaP.get(A).add(nuevaSimbolos);
    }
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);

  const terminalesOrd = Array.from(mapaTerminalVariable.keys()).sort(cmpStr);

  const elementos = [];
  elementos.push(mapaTerminalVariable.size === 0
    ? 'No había terminales en producciones de longitud mayor a uno.'
    : 'Terminales sustituidos en producciones de longitud > 1: {' + terminalesOrd.join(', ') + '}');
  if (nuevasVariables.length) {
    elementos.push('Nuevas variables creadas: ' + nuevasVariables.join(', '));
  }

  historial.agregar(new PasoHistorial('Sustitución de terminales en producciones largas', antes,
    elementos, diff[0], diff[1], nueva.copia()));
  return nueva;
}

// ---------------------------------------------------------------
// reducir a binaria las producciones de mas de 2 simbolos
// ---------------------------------------------------------------

function reducirProduccionesLargas(g, historial) {
  const antes = g.copia();
  const nueva = g.copia();
  const nuevasVariables = [];
  const cacheColas = new Map();

  const nuevaP = new Map();
  for (const A of nueva.V) nuevaP.set(A, new ProdSet());

  for (const A of g.V) {
    if (!nuevaP.has(A)) nuevaP.set(A, new ProdSet());
    for (const p of (g.P.get(A) || [])) {
      if (p.length <= 2) {
        nuevaP.get(A).add(p);
        continue;
      }
      const varResto = variableParaCola(p.slice(1), nueva, nuevaP, cacheColas, nuevasVariables);
      nuevaP.get(A).add([p[0], varResto]);
    }
  }
  nueva.P = nuevaP;

  const diff = diffProducciones(g.P, nueva.P);

  let habiaLargas = false;
  for (const prods of g.P.values()) {
    for (const p of prods) if (p.length > 2) habiaLargas = true;
  }

  const elementos = [];
  elementos.push(habiaLargas
    ? 'Producciones con más de dos símbolos en el lado derecho fueron convertidas a producciones binarias.'
    : 'No había producciones con más de dos símbolos en el lado derecho.');

  historial.agregar(new PasoHistorial('Reducción de producciones largas a forma binaria', antes,
    elementos, diff[0], diff[1], nueva.copia()));
  return nueva;
}

// Devuelve (creandola si hace falta) la variable que representa una "cola".
function variableParaCola(cola, nueva, nuevaP, cache, nuevasVariables) {
  const clave = prodKey(cola);
  const yaExiste = cache.get(clave);
  if (yaExiste !== undefined) return yaExiste;

  if (cola.length === 2) {
    const nuevaVar = nueva.nuevaVariable('X');
    nueva.V.add(nuevaVar);
    const prod = new ProdSet();
    prod.add(cola);
    nuevaP.set(nuevaVar, prod);
    nuevasVariables.push(nuevaVar + ' -> ' + cola.join(' '));
    cache.set(clave, nuevaVar);
    return nuevaVar;
  }

  const restoVar = variableParaCola(cola.slice(1), nueva, nuevaP, cache, nuevasVariables);
  const nuevaVar = nueva.nuevaVariable('X');
  nueva.V.add(nuevaVar);
  const prod = new ProdSet();
  prod.add([cola[0], restoVar]);
  nuevaP.set(nuevaVar, prod);
  nuevasVariables.push(nuevaVar + ' -> ' + cola[0] + ' ' + restoVar);
  cache.set(clave, nuevaVar);
  return nuevaVar;
}

// ---------------------------------------------------------------
// chequeo final: ¿la gramatica esta en FNC?
// ---------------------------------------------------------------

function esFnc(g) {
  for (const [A, prods] of g.P) {
    for (const p of prods) {
      if (p.length === 0) {
        if (A === g.S && !apareceEnLadoDerecho(g, A)) continue;
        return false;
      } else if (p.length === 1) {
        if (!g.T.has(p[0])) return false;
      } else if (p.length === 2) {
        if (!g.V.has(p[0]) || !g.V.has(p[1])) return false;
      } else {
        return false;
      }
    }
  }
  return true;
}

function apareceEnLadoDerecho(g, simbolo) {
  for (const prods of g.P.values()) {
    for (const p of prods) {
      if (p.includes(simbolo)) return true;
    }
  }
  return false;
}

// ---------------------------------------------------------------
// proceso completo, en orden
// ---------------------------------------------------------------

function convertirAFnc(g, historial) {
  const g1 = eliminarProduccionesNulas(g, historial);
  const g2 = eliminarProduccionesUnitarias(g1, historial);
  const g3 = eliminarVariablesInutiles(g2, historial);
  const g4 = eliminarVariablesInalcanzables(g3, historial);
  const g5 = sustituirTerminales(g4, historial);
  return reducirProduccionesLargas(g5, historial);
}
