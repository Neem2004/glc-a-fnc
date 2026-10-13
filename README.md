# GLC a FNC — Microproyecto #1

Depuración y conversión de gramáticas libres de contexto (GLC) a **Forma Normal de
Chomsky (FNC)**. Teoría de la Computación · Microproyecto #1.

## Versión web (en línea)

La aplicación corre 100% en el navegador (sin servidor ni instalación):

**https://neem2004.github.io/glc-a-fnc/**

Incluye: validación de la gramática, transformaciones paso a paso (nulas,
unitarias, inútiles, inalcanzables, sustitución de terminales y forma binaria),
proceso completo automático e historial detallado de cada etapa.

## Versión Java (consola)

- Código fuente: [`codigo_fuente_java/`](codigo_fuente_java)
- Ejecutable: `MicroproyectoFNC.jar` o `ejecutar.bat`

## Estructura

```
.
├── pagina_web/            Aplicación web (versión en línea)
│   ├── index.html
│   ├── styles.css
│   └── js/                grammar.js · transformations.js · historial.js · app.js
├── codigo_fuente_java/    Código fuente original en Java
├── MicroproyectoFNC.jar   Ejecutable Java
└── ejecutar.bat           Lanza el ejecutable Java
```

El despliegue de la web usa GitHub Pages con el workflow
[`.github/workflows/pages.yml`](.github/workflows/pages.yml), que publica la carpeta
`pagina_web`.
