# Análisis: recarga de la biblioteca en cada arranque

> Documento de entrega para implementación. **Temporal**: eliminar una vez
> consumido. No forma parte del build ni de la documentación del proyecto.

## Síntoma reportado

Al entrar a la app, la biblioteca "vuelve a recargar" las canciones: aparece
el indicador de escaneo y la lista se repinta aunque no haya cambios.

## Causa raíz

Flujo en cada arranque en frío (o cuando el sistema mató el proceso):

1. `MainActivity` crea `MusicViewModel` (scope `viewModel()`: sobrevive
   rotaciones; muere con la Activity/proceso).
2. `MusicViewModel.init` llama **siempre** a `reloadLibrary()`
   (`viewmodel/MusicViewModel.kt:367`).
3. `reloadLibrary()` (`MusicViewModel.kt:828-843`) hace dos cosas:
   - Carga `LibraryCache` y publica las canciones al instante → la lista se
     pinta rápido desde caché. **Esto es correcto y no debe romperse.**
   - Después llama incondicionalmente a `updateLibrary()`.
4. `updateLibrary()` (`MusicViewModel.kt:1137-1202`):
   - Pone `_isScanning = true` (línea 1158) → el header muestra
     "Actualizando tu música…" + `LinearProgressIndicator` **en cada arranque**.
   - Ejecuta `scanner.scan()` completo: travesía recursiva de TODO el árbol
     SAF (`data/repository/SafMusicScanner.kt`), un
     `contentResolver.query()` por directorio (un IPC por carpeta). No existe
     ningún atajo "si nada cambió, no escanees".
   - Al terminar, `setCanonicalSongs()` re-emite la lista completa re-ordenada
     (`publishSortedLibrary()`) → recomposición visible de toda la biblioteca
     aunque el contenido sea idéntico.

Agravante por archivo: `ScanReusePolicy`
(`data/repository/ScanReusePolicy.kt`) reutiliza entradas solo si `size` +
`lastModified` son confiables Y `albumArtScanned == true`. Si el
DocumentsProvider devuelve `lastModified` nulo (común en varios providers),
esos archivos re-extraen metadatos con `MediaMetadataRetriever` en CADA
arranque — el costo real pesado.

## Cambios recomendados (en orden costo/beneficio)

### 1. Ocultar la UI de escaneo cuando la caché ya pintó la lista

El scan de arranque es una *revalidación*, no una carga inicial. Mostrar el
estado "Actualizando tu música…" (y la barra de progreso) solo cuando:
- la caché estaba vacía (importación inicial), o
- el usuario tocó "Actualizar biblioteca" explícitamente.

Diff mínimo, elimina la percepción del problema. Cero riesgo funcional.

### 2. No re-publicar la lista si el scan no cambió nada

En `MusicViewModel.kt:1195` ya existe el guard `if (merged != cachedEntries)`
para la **escritura** a disco; falta el equivalente para la re-emisión en
memoria: una revalidación sin cambios igual dispara `publishSortedLibrary()`
con una instancia nueva de lista (recomposición + posible salto de scroll /
recarga de portadas en pantalla).

### 3. Espaciar las revalidaciones automáticas

Persistir en prefs un "último scan exitoso" y saltar el scan automático si
pasó menos de, p. ej., 24 h. Mantener SIEMPRE:
- refresco manual ("Actualizar biblioteca"),
- scan al añadir/quitar carpeta.

**Tradeoff a decidir**: archivos agregados desde afuera de la app no aparecen
hasta que pase el intervalo o el usuario refresque. Elimina el costo real del
traversal SAF en cada arranque.

### 4. (Opcional, con tradeoff real) Regla de reuso más débil

Para archivos con metadatos no confiables (`lastModified` nulo): reusar la
entrada caché si `size` coincide y `metadataScanned == true`. Evita el
re-extracto per-archivo en cada arranque, pero un archivo modificado que
conserva su tamaño no refrescaría sus metadatos. Solo aplicar si se verifica
(p. ej., un log puntual) que el provider del dispositivo devuelve
`lastModified` nulo.

## Restricciones del proyecto (NO romper)

- `testTag("library_list")` y la señal `rememberScrollIdleSignal` — los usa el
  macrobenchmark `:benchmark` y la carga de portadas durante el scroll.
- `Song.id` es el URI del documento; `song.id.startsWith("$treeUri/")` es
  suposición estructural (caché, portadas, cola de reproducción).
- Tests unitarios son JVM puro; la lógica nueva de decisión (throttle,
  comparación de re-emisión) debe quedar testeable con dependencias
  inyectadas, siguiendo el patrón de `ScanReusePolicy`/`PlaybackRestorePolicy`.
- El check de actualizaciones por GitHub ya tiene su propio throttle (72 h) en
  `data/update`; NO unificar stores de preferencias — cada uno es independiente
  por diseño.

## Validación

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
# Rendimiento (dispositivo físico con biblioteca escaneada):
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cloudjex.jexplayer.benchmark.LibraryScrollBenchmark
```
