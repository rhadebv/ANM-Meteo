# ANM Meteo v0.3

## Funcții
- GPS proaspăt, cu respingerea locațiilor mai vechi de 5 minute și a preciziei peste 1 km.
- Stația ANM apropiată, stații selectabile și favorite persistente.
- Cache: observații 30 min, prognoză 30 min, avertizări 15 min, fallback offline.
- Prognoză pe zile și ore atunci când elementele sunt disponibile în Prognoza Orașe API.
- Iconițe meteo animate în Jetpack Compose.
- Radar ANM cu WebView cache, indicator de încărcare și refresh explicit.
- Iconiță proprie de aplicație.
- Widget Android redimensionabil cu stația, temperatura, condiția și ultima actualizare.

## Widget
După instalare: apăsare lungă pe ecranul principal > Widgeturi > ANM Meteo.
Widgetul arată ultima stare pe care aplicația a salvat-o. Android controlează actualizările periodice și poate întârzia refresh-ul pentru economisirea bateriei.

## Build
Upload conținutul acestui folder direct în rădăcina repository-ului GitHub. În Codemagic pornește workflow-ul `ANM Meteo v0.3 - Debug APK`.
