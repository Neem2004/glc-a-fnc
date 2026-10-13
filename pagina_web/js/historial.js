/*
 * historial.js
 * Historial de transformaciones. Traduccion fiel de Historial.java
 * (incluye la clase PasoHistorial).
 */

class Historial {
  constructor() {
    this.pasos = [];
  }

  agregar(paso) {
    this.pasos.push(paso);
  }

  limpiar() {
    this.pasos = [];
  }
}

// Un paso del historial: como estaba la gramatica antes, que se detecto,
// que se quito, que se agrego y como quedo despues.
class PasoHistorial {
  constructor(etapa, gramaticaAntes, elementosIdentificados, produccionesEliminadas,
              produccionesAgregadas, gramaticaResultante) {
    this.etapa = etapa;
    this.gramaticaAntes = gramaticaAntes;
    this.elementosIdentificados = elementosIdentificados;
    this.produccionesEliminadas = produccionesEliminadas;
    this.produccionesAgregadas = produccionesAgregadas;
    this.gramaticaResultante = gramaticaResultante;
  }
}
