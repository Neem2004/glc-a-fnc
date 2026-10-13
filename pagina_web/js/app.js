/*
 * app.js
 * Interfaz de la version web. Equivale al menu de Main.java, pero con
 * un editor que se puede corregir paso por paso (por si hay errores
 * de digitacion): antes de cada transformacion se vuelve a leer y
 * validar lo que hay en el editor.
 *
 * Todo lo que el usuario dispara pasa por manejadores envueltos en
 * try/catch, y ademas hay un capturador global de errores, para que
 * cualquier fallo inesperado se muestre como un mensaje y la pagina
 * nunca se quede "colgada" en silencio.
 */
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);

  // ---- estado ----
  let gramaticaOriginal = null; // la primera gramatica registrada / cargada
  let gramaticaFinal = null;    // resultado cuando ya se logro FNC
  const historial = new Historial();

  const EJEMPLO = {
    variables: 'S, A, B, C',
    terminales: 'a, b',
    inicial: 'S',
    producciones: 'S -> ASA | aB | epsilon\nA -> B | S\nB -> b | epsilon\nC -> AB',
  };

  // Palabras clave que identifican cada etapa del pipeline en el historial.
  const ETAPA_KEYS = [
    { key: 'nulas', needles: ['nulas'] },
    { key: 'unitarias', needles: ['unitarias'] },
    { key: 'inutiles', needles: ['inutiles', 'inútiles'] },
    { key: 'inalcanzables', needles: ['inalcanzables'] },
    { key: 'terminales', needles: ['terminales'] },
    { key: 'binaria', needles: ['binaria'] },
  ];

  function etapaKey(nombre) {
    const n = String(nombre || '').toLowerCase();
    for (const e of ETAPA_KEYS) {
      if (e.needles.some((x) => n.includes(x.toLowerCase()))) return e.key;
    }
    return null;
  }

  // ---------------------------------------------------------------
  // utilidades de interfaz / errores
  // ---------------------------------------------------------------

  function mensajeDeError(e) {
    if (!e) return 'Error desconocido.';
    if (typeof e === 'string') return e;
    return e.message || String(e);
  }

  // Envuelve un manejador para que ningun error se escape sin avisar.
  function safe(etiqueta, fn) {
    return function (evento) {
      try {
        return fn(evento);
      } catch (e) {
        mostrarMensajes('Error inesperado' + (etiqueta ? ' en "' + etiqueta + '"' : ''),
          [mensajeDeError(e)], 'error');
      }
    };
  }

  function mostrarMensajes(titulo, lineas, tipo) {
    const cont = $('mensajes');
    if (!cont) return;
    cont.hidden = false;
    cont.className = 'mensajes ' + (tipo || 'info');
    cont.innerHTML = '';

    const h = document.createElement('strong');
    h.textContent = titulo;
    cont.appendChild(h);

    if (lineas && lineas.length) {
      const ul = document.createElement('ul');
      for (const l of lineas) {
        const li = document.createElement('li');
        li.textContent = l;
        ul.appendChild(li);
      }
      cont.appendChild(ul);
    }
  }

  function ocultarMensajes() {
    const cont = $('mensajes');
    if (cont) cont.hidden = true;
  }

  function actualizarBadge(texto, tipo) {
    const b = $('estadoBadge');
    const box = $('statusBox');
    if (b) b.textContent = texto;
    const estado = tipo === 'ok' ? 'ok'
      : tipo === 'error' ? 'error'
        : tipo === 'warn' ? 'warn'
          : tipo === 'busy' ? 'busy'
            : 'idle';
    if (box) box.dataset.state = estado;
  }

  function gramaticaDesdeEditor() {
    return Grammar.desdeEntrada(
      $('variables').value,
      $('terminales').value,
      $('inicial').value,
      $('producciones').value
    );
  }

  function cargarEnEditor(g) {
    $('variables').value = Array.from(g.V).join(', ');
    $('terminales').value = Array.from(g.T).join(', ');
    $('inicial').value = g.S;
    $('producciones').value = g.produccionesComoTexto().join('\n');
    actualizarGutter();
  }

  // Lee y valida el editor. Devuelve {ok, gramatica, errores}.
  function parsearEditor(silencioso) {
    let g = null;
    let errores = [];
    try {
      g = gramaticaDesdeEditor();
      errores = g.validar();
    } catch (e) {
      errores = [mensajeDeError(e)];
    }

    if (errores.length) {
      actualizarBadge('con errores', 'error');
      if (!silencioso) mostrarMensajes('La gramática tiene errores', errores, 'error');
      return { ok: false, gramatica: null, errores };
    }

    actualizarBadge('válida', 'ok');
    if (!silencioso) {
      mostrarMensajes('Gramática válida',
        ['No se encontraron errores. Puedes continuar con las transformaciones.'], 'ok');
    }
    return { ok: true, gramatica: g, errores };
  }

  // ---------------------------------------------------------------
  // acciones
  // ---------------------------------------------------------------

  function registrar() {
    const res = parsearEditor(false);
    if (!res.ok) return;

    gramaticaOriginal = res.gramatica.copia();
    gramaticaFinal = null;
    historial.limpiar();
    cargarEnEditor(res.gramatica); // normaliza el texto

    renderOriginal();
    renderFinal();
    renderHistorial();
    actualizarBadge('registrada', 'ok');
    mostrarMensajes('Gramática registrada',
      ['La gramática es válida. Ya puedes aplicar las transformaciones paso a paso.'], 'ok');
  }

  function validarEditor() {
    parsearEditor(false);
  }

  function aplicarPaso(nombre) {
    const res = parsearEditor(false);
    if (!res.ok) return; // no transforma si hay errores de digitacion

    let g = res.gramatica;
    const pasosAntes = historial.pasos.length;
    try {
      switch (nombre) {
        case 'nulas':         g = eliminarProduccionesNulas(g, historial); break;
        case 'unitarias':     g = eliminarProduccionesUnitarias(g, historial); break;
        case 'inutiles':      g = eliminarVariablesInutiles(g, historial); break;
        case 'inalcanzables': g = eliminarVariablesInalcanzables(g, historial); break;
        case 'terminales':    g = sustituirTerminales(g, historial); break;
        case 'binaria':       g = reducirProduccionesLargas(g, historial); break;
        default:
          mostrarMensajes('Paso desconocido',
            ['La etapa "' + nombre + '" no existe.'], 'error');
          return;
      }
    } catch (e) {
      mostrarMensajes('No se pudo aplicar el paso',
        [mensajeDeError(e), 'No se modificó la gramática. Corrige y vuelve a intentar.'], 'error');
      return;
    }

    cargarEnEditor(g);
    renderHistorial();
    renderFinal();

    if (historial.pasos.length === pasosAntes) {
      // La etapa no quedó registrada (caso borde): refrescamos sin romper nada.
      mostrarMensajes('Paso aplicado', ['La gramática se actualizó.'], 'ok');
      return;
    }

    const etapa = historial.pasos[historial.pasos.length - 1].etapa;

    if (esFnc(g)) {
      gramaticaFinal = g.copia();
      mostrarMensajes('¡La gramática ya está en FNC!',
        ['Se aplicó: ' + etapa + '. El resultado cumple la Forma Normal de Chomsky.'], 'ok');
    } else {
      gramaticaFinal = null;
      mostrarMensajes('Paso aplicado',
        ['Se aplicó: ' + etapa + '. Aún no está en FNC; continúa con la siguiente etapa.'], 'ok');
    }
    renderFinal();
  }

  function procesoCompleto() {
    const res = parsearEditor(false);
    if (!res.ok) return;

    historial.limpiar();
    let g;
    try {
      g = convertirAFnc(res.gramatica, historial);
    } catch (e) {
      renderHistorial();
      mostrarMensajes('No se pudo completar el proceso',
        [mensajeDeError(e), 'Revisa la gramática y vuelve a intentar.'], 'error');
      return;
    }
    gramaticaFinal = g.copia();

    cargarEnEditor(g);
    renderHistorial();
    renderFinal();

    const ok = esFnc(g);
    mostrarMensajes('Proceso completo ejecutado',
      ['Se corrieron todas las transformaciones en orden. La gramática final '
        + (ok ? 'SÍ' : 'NO') + ' está en FNC.'], ok ? 'ok' : 'warn');
  }

  function marcarFinal() {
    const res = parsearEditor(false);
    if (!res.ok) return;
    gramaticaFinal = res.gramatica.copia();
    renderFinal();
    mostrarMensajes('Gramática final',
      ['Se tomó el contenido del editor como gramática final. '
        + '¿Está en FNC? ' + (esFnc(gramaticaFinal) ? 'Sí' : 'No') + '.'], 'ok');
  }

  function reiniciarProceso() {
    if (!gramaticaOriginal) {
      mostrarMensajes('No hay gramática original',
        ['Registra una gramática o carga el ejemplo primero.'], 'warn');
      return;
    }
    historial.limpiar();
    gramaticaFinal = null;
    cargarEnEditor(gramaticaOriginal);
    renderFinal();
    renderHistorial();
    actualizarBadge('registrada', 'ok');
    mostrarMensajes('Proceso reiniciado',
      ['El editor volvió a la gramática original.'], 'ok');
  }

  function cargarEjemplo() {
    $('variables').value = EJEMPLO.variables;
    $('terminales').value = EJEMPLO.terminales;
    $('inicial').value = EJEMPLO.inicial;
    $('producciones').value = EJEMPLO.producciones;
    actualizarGutter();
    registrar();
  }

  function limpiarTodo() {
    $('variables').value = '';
    $('terminales').value = '';
    $('inicial').value = '';
    $('producciones').value = '';
    actualizarGutter();
    gramaticaOriginal = null;
    gramaticaFinal = null;
    historial.limpiar();
    ocultarMensajes();
    actualizarBadge('sin gramática', '');
    renderOriginal();
    renderFinal();
    renderHistorial();
  }

  // ---------------------------------------------------------------
  // render
  // ---------------------------------------------------------------

  function renderOriginal() {
    const pre = $('gramOriginal');
    if (pre) pre.textContent = gramaticaOriginal ? gramaticaOriginal.toString() : '—';
  }

  function renderFinal() {
    const pre = $('gramFinal');
    const check = $('fncCheck');
    const panel = $('finalPanel');
    if (!pre || !check) return;

    if (!gramaticaFinal) {
      pre.textContent = '—';
      check.textContent = '';
      check.className = 'fnc-check';
      if (panel) panel.classList.remove('is-fnc');
      return;
    }
    pre.textContent = gramaticaFinal.toString();
    const ok = esFnc(gramaticaFinal);
    check.textContent = '¿Está en FNC? ' + (ok ? 'Sí' : 'No');
    check.className = 'fnc-check ' + (ok ? 'ok' : 'error');
    if (panel) panel.classList.toggle('is-fnc', ok);
  }

  function renderHistorial() {
    const cont = $('historial');
    if (!cont) return;
    cont.innerHTML = '';

    if (historial.pasos.length === 0) {
      const p = document.createElement('p');
      p.className = 'vacio';
      p.textContent = 'Aún no se ha ejecutado ninguna transformación.';
      cont.appendChild(p);
    } else {
      historial.pasos.forEach((paso, i) => {
        cont.appendChild(crearTarjetaPaso(paso, i));
      });
    }

    actualizarPipeline();
  }

  function actualizarPipeline() {
    const hechas = new Set();
    for (const paso of historial.pasos) {
      const k = etapaKey(paso.etapa);
      if (k) hechas.add(k);
    }
    let nextAsignado = false;
    document.querySelectorAll('.stage').forEach((li) => {
      const key = li.dataset.etapa;
      const done = hechas.has(key);
      li.classList.toggle('is-done', done);
      let isNext = false;
      if (!done && !nextAsignado) { isNext = true; nextAsignado = true; }
      li.classList.toggle('is-next', isNext);
    });
  }

  function crearTarjetaPaso(paso, i) {
    const card = document.createElement('div');
    card.className = 'paso';

    const head = document.createElement('div');
    head.className = 'paso-head';
    const num = document.createElement('span');
    num.className = 'paso-num';
    num.textContent = 'Paso ' + (i + 1);
    const titulo = document.createElement('span');
    titulo.className = 'paso-titulo';
    titulo.textContent = paso.etapa;
    head.appendChild(num);
    head.appendChild(titulo);
    card.appendChild(head);

    card.appendChild(bloque('Gramática antes de la transformación',
      paso.gramaticaAntes.toString(), 'antes'));
    card.appendChild(lista('Elementos identificados', paso.elementosIdentificados, 'info'));
    card.appendChild(lista('Producciones eliminadas', paso.produccionesEliminadas, 'elim'));
    card.appendChild(lista('Producciones agregadas', paso.produccionesAgregadas, 'agreg'));
    card.appendChild(bloque('Gramática resultante',
      paso.gramaticaResultante.toString(), 'despues'));

    return card;
  }

  function bloque(titulo, texto, clase) {
    const wrap = document.createElement('div');
    wrap.className = 'paso-bloque';
    const h = document.createElement('h4');
    h.textContent = titulo;
    wrap.appendChild(h);
    const pre = document.createElement('pre');
    pre.className = 'code' + (clase ? ' ' + clase : '');
    pre.textContent = texto;
    wrap.appendChild(pre);
    return wrap;
  }

  function lista(titulo, items, clase) {
    const wrap = document.createElement('div');
    wrap.className = 'paso-bloque';
    const h = document.createElement('h4');
    h.textContent = titulo;
    wrap.appendChild(h);

    if (!items || items.length === 0) {
      const p = document.createElement('p');
      p.className = 'vacio';
      p.textContent = '(ninguno)';
      wrap.appendChild(p);
      return wrap;
    }

    const ul = document.createElement('ul');
    ul.className = 'lista' + (clase ? ' ' + clase : '');
    for (const it of items) {
      const li = document.createElement('li');
      li.textContent = it;
      ul.appendChild(li);
    }
    wrap.appendChild(ul);
    return wrap;
  }

  // ---- regla de números de línea del editor ----

  function actualizarGutter() {
    const ta = $('producciones');
    const gutter = $('lineNumbers');
    if (!ta || !gutter) return;
    const total = Math.max(ta.value.split('\n').length, 1);
    let texto = '';
    for (let i = 1; i <= total; i++) texto += (i === 1 ? '' : '\n') + i;
    gutter.textContent = texto;
    gutter.scrollTop = ta.scrollTop;
  }

  // ---------------------------------------------------------------
  // eventos
  // ---------------------------------------------------------------

  function init() {
    $('btnRegistrar').addEventListener('click', safe('Registrar', registrar));
    $('btnValidar').addEventListener('click', safe('Validar', validarEditor));
    $('btnEjemplo').addEventListener('click', safe('Cargar ejemplo', cargarEjemplo));
    $('btnLimpiar').addEventListener('click', safe('Limpiar', limpiarTodo));

    document.querySelectorAll('button[data-paso]').forEach((btn) => {
      btn.addEventListener('click', safe('Aplicar paso', () => aplicarPaso(btn.getAttribute('data-paso'))));
    });

    $('btnCompleto').addEventListener('click', safe('Proceso completo', procesoCompleto));
    $('btnFinal').addEventListener('click', safe('Marcar final', marcarFinal));
    $('btnReiniciar').addEventListener('click', safe('Reiniciar', reiniciarProceso));
    $('btnLimpiarHistorial').addEventListener('click', safe('Vaciar bitácora', () => {
      historial.limpiar();
      renderHistorial();
      mostrarMensajes('Bitácora vaciada', ['Se borraron los pasos registrados.'], 'info');
    }));

    // editor: sincroniza la regla de números y actualiza al escribir
    const ta = $('producciones');
    if (ta) {
      ta.addEventListener('input', actualizarGutter);
      ta.addEventListener('scroll', () => {
        const gutter = $('lineNumbers');
        if (gutter) gutter.scrollTop = ta.scrollTop;
      });
    }

    renderOriginal();
    renderFinal();
    renderHistorial();
    actualizarGutter();
  }

  // Red de seguridad: cualquier error no controlado se muestra al usuario.
  window.addEventListener('error', (ev) => {
    mostrarMensajes('Error inesperado',
      [ev && ev.message ? ev.message : 'Ocurrió un error inesperado.'], 'error');
  });
  window.addEventListener('unhandledrejection', (ev) => {
    mostrarMensajes('Error inesperado',
      [mensajeDeError(ev && ev.reason)], 'error');
  });

  init();
})();
