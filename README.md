# AccessibilityBridge

App Android que expone AccessibilityService vía HTTP local (127.0.0.1:8080) para que Termux pueda leer y controlar la UI de otras apps.

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│  AccessibilityBridgeService (AccessibilityService)         │
│  ├── rootInActiveWindow → AccessibilityNodeInfo tree       │
│  ├── onAccessibilityEvent → auto-refresh                   │
│  └── performAction / dispatchGesture → execute actions     │
├─────────────────────────────────────────────────────────────┤
│  UiTreeSerializer                                           │
│  └── AccessibilityNodeInfo → JSON estructurado             │
├─────────────────────────────────────────────────────────────┤
│  LocalHttpServer (puerto 8080)                              │
│  ├── GET  /ui       → árbol UI completo                    │
│  ├── GET  /status   → estado del servicio                  │
│  ├── POST /tap      → click por ID o coordenadas           │
│  ├── POST /text     → escribir en nodo editable            │
│  ├── POST /swipe    → gesto deslizamiento                  │
│  └── POST /refresh  → forzar relectura                     │
├─────────────────────────────────────────────────────────────┤
│  GestureExecutor                                            │
│  └── GestureDescription API (Android 7+)                   │
└─────────────────────────────────────────────────────────────┘
```

## Compilar e instalar

### Opción A: Android Studio
1. Abrir `Projects/AccessibilityBridge` en Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. Instalar APK en dispositivo

### Opción B: Gradle en Termux (requiere Android SDK)
```bash
cd ~/Projects/AccessibilityBridge
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

### Opción C: ADB install directo (si tienes el APK)
```bash
adb install -r app-debug.apk
```

## Activar AccessibilityService

1. Abrir la app "AccessibilityBridge"
2. Tocar **"Activar AccessibilityService"**
3. En configuración de accesibilidad, buscar **"AccessibilityBridge Service"**
4. Activar el interruptor
5. Confirmar permisos
6. Volver a la app → debería mostrar **"Servicio activo - Puerto 8080"**

## Probar desde Termux

### Instalar dependencias Python
```bash
pkg install python requests
# o
pip install requests
```

### Copiar cliente
```bash
cp ~/Projects/AccessibilityBridge/bridge_client.py ~/bridge_client.py
chmod +x ~/bridge_client.py
```

### Comandos de prueba

```bash
# Estado del servicio
python3 ~/bridge_client.py status

# Ver árbol UI (primeros 3 niveles)
python3 ~/bridge_client.py ui

# Buscar nodos por texto
python3 ~/bridge_client.py find "Botón"
python3 ~/bridge_client.py find "Iniciar sesión"

# Listar elementos clickeables
python3 ~/bridge_client.py clickable

# Tocar por ID (obtenido del árbol)
python3 ~/bridge_client.py tap 123456789

# Tocar por coordenadas
python3 ~/bridge_client.py tapxy 500 1000

# Escribir texto en nodo editable
python3 ~/bridge_client.py text 123456789 "Hola mundo"

# Deslizar (swipe)
python3 ~/bridge_client.py swipe 500 1500 500 500 500

# Forzar refresh del árbol
python3 ~/bridge_client.py refresh
```

## Endpoints API

| Método | Endpoint | Body | Descripción |
|--------|----------|------|-------------|
| GET | `/ui` | - | Árbol UI completo + timestamp |
| GET | `/status` | - | Estado del servicio |
| POST | `/tap` | `{"id": int}` o `{"x": float, "y": float}` | Click nativo o por coords |
| POST | `/text` | `{"id": int, "text": "string"}` | Escribir en nodo editable |
| POST | `/swipe` | `{"startX": float, "startY": float, "endX": float, "endY": float, "duration": int}` | Gesture swipe |
| POST | `/refresh` | - | Forzar relectura árbol |

## Formato JSON nodo UI

```json
{
  "id": 123456789,
  "className": "android.widget.Button",
  "packageName": "com.example.app",
  "text": "Entrar",
  "contentDescription": "Botón entrar",
  "viewIdResourceName": "com.example.app:id/btn_login",
  "clickable": true,
  "enabled": true,
  "focusable": true,
  "editable": false,
  "selected": false,
  "checked": false,
  "scrollable": false,
  "longClickable": false,
  "focused": false,
  "visibleToUser": true,
  "bounds": [100, 200, 300, 280],
  "center": [200, 240],
  "actions": ["click", "long_click", "focus"],
  "children": [...]
}
```

## Respuesta estándar

```json
// Éxito
{"success": true, "action": "tap"}

// Error
{"success": false, "action": "tap", "error": "Node not found"}
```

## Notas importantes

- **Sin root**: Usa AccessibilityService estándar
- **Puerto fijo**: 8080 en 127.0.0.1 (solo localhost)
- **Auto-refresh**: El árbol se actualiza solo en cambios de ventana/contenido
- **Acciones nativas**: `/tap` con `id` usa `performAction(CLICK)`; sin ID usa gestos
- **Compatible Python**: Cliente incluido con helpers de búsqueda (`find`, `clickable`, `find_by_text`, etc.)

## Solución de problemas

| Problema | Solución |
|----------|----------|
| "No se puede conectar" | Verificar que la app está abierta y servicio activado |
| Árbol vacío `{}` | Abrir otra app, volver, o ejecutar `refresh` |
| Tap no funciona | Probar coordenadas (`tapxy`) o verificar que nodo es clickable |
| Text no escribe | Verificar `editable: true` en el nodo objetivo |
| Puerto ocupado | Cambiar `PORT` en `AccessibilityBridgeService.kt` y recompilar |

## Logs de depuración

```bash
# Ver logs del servicio
adb logcat -s AccessibilityBridgeService LocalHttpServer UiTreeSerializer GestureExecutor
```