# Versión web — Depuración y conversión de GLC a FNC

Esta carpeta contiene la versión **página web** del proyecto (Microproyecto #1).
Reimplementa toda la lógica del programa Java en JavaScript, para que corra
directamente en el navegador, **sin instalar nada y sin conexión**.

## Cómo abrirla

- **Opción 1 (la más simple):** haz doble clic en `index.html`
  (o en `abrir.bat`). Se abre en tu navegador predeterminado.
- **Opción 2 (servidor local, opcional):** si tu navegador pusiera reparos al
  abrir archivos locales, ejecuta en esta carpeta:
  ```
  python -m http.server 8000
  ```
  y entra a `http://localhost:8000`.

> No usa módulos ES ni peticiones de red, por eso funciona incluso abriendo el
> archivo directamente (`file://`). Todo el procesamiento ocurre en tu equipo.

## Qué incluye (igual que el programa Java)

- Ingresar la gramática (variables, terminales, símbolo inicial, producciones).
- Validar la gramática y mostrar los errores encontrados.
- Transformaciones **paso a paso**:
  1. Eliminar producciones nulas (ε).
  2. Eliminar producciones unitarias.
  3. Eliminar variables inútiles (no generadoras).
  4. Eliminar variables inalcanzables.
  5. Sustituir terminales en producciones largas.
  6. Reducir las producciones largas a forma binaria.
- **Ejecutar proceso completo** (modo automático, HFC completo).
- Historial detallado de cada etapa: gramática antes, elementos identificados,
  producciones eliminadas/agregadas y gramática resultante.
- Gramática original y gramática final en FNC, con verificación de FNC.

## Edición paso a paso (corregir errores de digitación)

La gramática actual vive en el **editor** de la parte superior. Después de cada
transformación, el resultado aparece ahí y puedes **editarlo**.

- Antes de aplicar cualquier paso, la app vuelve a leer y **validar** lo que hay
  en el editor.
- Si hay un error (por ejemplo, falta `->`, un símbolo no declarado, el símbolo
  inicial no es una variable…), **no transforma nada** y te muestra qué corregir.
- Corriges el texto y aplicas el siguiente paso.

## Notación

- Cada **variable** y cada **terminal** es de **un solo carácter** (S, A, B… / a, b, 0, 1…).
- La **cadena vacía** se escribe `epsilon` (también se aceptan `landa` o `e`).
- Las alternativas van separadas por `|` y cada producción en una línea:
  ```
  S -> ASA | aB | epsilon
  A -> B | S
  B -> b | epsilon
  ```
- Si escribes los símbolos separados por espacios (por ejemplo `A X2`), se
  respetan; si no, se interpretan carácter por carácter. Las variables
  auxiliares que crea el programa (`S1`, `X1`, `X2`…) se muestran siempre
  separadas por espacios para que no haya ambigüedad.

## Estructura de archivos

```
pagina_web/
├── index.html          Interfaz
├── styles.css          Estilos
├── abrir.bat           Abre la página (doble clic)
└── js/
    ├── grammar.js          Clase Grammar (traducción de Grammar.java)
    ├── transformations.js  Algoritmos (traducción de Transformations.java)
    ├── historial.js        Historial (traducción de Historial.java)
    └── app.js              Lógica de la interfaz (equivale a Main.java)
```

La salida para la gramática de ejemplo es idéntica a la de la versión Java.
