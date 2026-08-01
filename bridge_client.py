#!/usr/bin/env python3
"""
Cliente Python para AccessibilityBridge - Termux
Conecta a http://127.0.0.1:8080 para leer UI y ejecutar acciones
"""

import json
import requests
import sys
import time
from typing import Optional, Dict, Any, List

BASE_URL = "http://127.0.0.1:8080"

class AccessibilityBridgeClient:
    def __init__(self, base_url: str = BASE_URL, timeout: int = 5):
        self.base_url = base_url
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def _request(self, method: str, endpoint: str, **kwargs) -> Dict[str, Any]:
        url = f"{self.base_url}{endpoint}"
        try:
            resp = self.session.request(method, url, timeout=self.timeout, **kwargs)
            resp.raise_for_status()
            return resp.json()
        except requests.exceptions.ConnectionError:
            return {"error": "No se puede conectar al servicio. ¿Está la app corriendo y el AccessibilityService activado?"}
        except requests.exceptions.Timeout:
            return {"error": "Timeout en la petición"}
        except requests.exceptions.HTTPError as e:
            return {"error": f"HTTP {e.response.status_code}: {e.response.text}"}
        except Exception as e:
            return {"error": str(e)}

    def get_ui(self) -> Dict[str, Any]:
        """Obtiene el árbol UI actual"""
        return self._request("GET", "/ui")

    def get_status(self) -> Dict[str, Any]:
        """Obtiene el estado del servicio"""
        return self._request("GET", "/status")

    def tap_by_id(self, node_id: int) -> Dict[str, Any]:
        """Toca un nodo por su ID interno"""
        return self._request("POST", "/tap", json={"id": node_id})

    def tap_by_coordinates(self, x: float, y: float) -> Dict[str, Any]:
        """Toca por coordenadas de pantalla"""
        return self._request("POST", "/tap", json={"x": x, "y": y})

    def text(self, node_id: int, text: str) -> Dict[str, Any]:
        """Escribe texto en un nodo editable"""
        return self._request("POST", "/text", json={"id": node_id, "text": text})

    def swipe(self, start_x: float, start_y: float, end_x: float, end_y: float, duration: int = 300) -> Dict[str, Any]:
        """Ejecuta un gesto de deslizamiento"""
        return self._request("POST", "/swipe", json={
            "startX": start_x, "startY": start_y,
            "endX": end_x, "endY": end_y,
            "duration": duration
        })

    def refresh(self) -> Dict[str, Any]:
        """Fuerza relectura del árbol UI"""
        return self._request("POST", "/refresh")

    def find_nodes(self, tree: Dict, **criteria) -> List[Dict]:
        """Busca nodos en el árbol que coincidan con criterios"""
        results = []
        
        def search(node):
            match = True
            for key, value in criteria.items():
                if key not in node or node[key] != value:
                    match = False
                    break
            if match:
                results.append(node)
            for child in node.get("children", []):
                search(child)
        
        search(tree)
        return results

    def find_by_text(self, tree: Dict, text: str, exact: bool = False) -> List[Dict]:
        """Busca nodos por texto (contiene o exacto)"""
        results = []
        def search(node):
            node_text = node.get("text", "")
            if exact:
                if node_text == text:
                    results.append(node)
            else:
                if text.lower() in node_text.lower():
                    results.append(node)
            for child in node.get("children", []):
                search(child)
        search(tree)
        return results

    def find_by_id_resource(self, tree: Dict, resource_id: str) -> List[Dict]:
        """Busca nodos por viewIdResourceName"""
        results = []
        def search(node):
            if resource_id in (node.get("viewIdResourceName") or ""):
                results.append(node)
            for child in node.get("children", []):
                search(child)
        search(tree)
        return results

    def find_clickable(self, tree: Dict) -> List[Dict]:
        """Encuentra todos los nodos clickeables"""
        results = []
        def search(node):
            if node.get("clickable"):
                results.append(node)
            for child in node.get("children", []):
                search(child)
        search(tree)
        return results

    def print_tree_summary(self, tree: Dict, max_depth: int = 3, current_depth: int = 0):
        """Imprime resumen del árbol"""
        if current_depth > max_depth:
            return
        indent = "  " * current_depth
        text = tree.get("text", "")
        desc = tree.get("contentDescription", "")
        cls = tree.get("className", "").split(".")[-1]
        pkg = tree.get("packageName", "").split(".")[-1]
        clickable = "👆" if tree.get("clickable") else "  "
        editable = "✏️" if tree.get("editable") else "  "
        node_id = tree.get("id", 0)
        info = f"{indent}{clickable}{editable} [{node_id}] {cls} ({pkg})"
        if text:
            info += f" | text='{text[:50]}'"
        if desc:
            info += f" | desc='{desc[:50]}'"
        print(info)
        for child in tree.get("children", []):
            self.print_tree_summary(child, max_depth, current_depth + 1)


def main():
    client = AccessibilityBridgeClient()
    
    if len(sys.argv) < 2:
        print("Uso:")
        print("  python3 bridge_client.py ui           # Ver árbol UI")
        print("  python3 bridge_client.py status       # Ver estado")
        print("  python3 bridge_client.py tap <id>     # Tocar por ID")
        print("  python3 bridge_client.py tapxy <x> <y> # Tocar por coords")
        print("  python3 bridge_client.py text <id> <texto> # Escribir")
        print("  python3 bridge_client.py swipe <sx> <sy> <ex> <ey> [dur] # Deslizar")
        print("  python3 bridge_client.py refresh      # Forzar refresh")
        print("  python3 bridge_client.py find <texto> # Buscar por texto")
        print("  python3 bridge_client.py clickable    # Listar clickeables")
        return

    cmd = sys.argv[1]

    if cmd == "ui":
        result = client.get_ui()
        if "error" in result:
            print(f"Error: {result['error']}")
        else:
            tree = result.get("tree", {})
            print(f"Árbol UI (timestamp: {result.get('timestamp', 0)})")
            client.print_tree_summary(tree, max_depth=3)
            
    elif cmd == "status":
        result = client.get_status()
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "tap":
        if len(sys.argv) < 3:
            print("Uso: tap <node_id>")
            return
        node_id = int(sys.argv[2])
        result = client.tap_by_id(node_id)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "tapxy":
        if len(sys.argv) < 4:
            print("Uso: tapxy <x> <y>")
            return
        x, y = float(sys.argv[2]), float(sys.argv[3])
        result = client.tap_by_coordinates(x, y)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "text":
        if len(sys.argv) < 4:
            print("Uso: text <node_id> <texto>")
            return
        node_id = int(sys.argv[2])
        text = " ".join(sys.argv[3:])
        result = client.text(node_id, text)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "swipe":
        if len(sys.argv) < 6:
            print("Uso: swipe <start_x> <start_y> <end_x> <end_y> [duration]")
            return
        sx, sy = float(sys.argv[2]), float(sys.argv[3])
        ex, ey = float(sys.argv[4]), float(sys.argv[5])
        dur = int(sys.argv[6]) if len(sys.argv) > 6 else 300
        result = client.swipe(sx, sy, ex, ey, dur)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "refresh":
        result = client.refresh()
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
    elif cmd == "find":
        if len(sys.argv) < 3:
            print("Uso: find <texto>")
            return
        text = " ".join(sys.argv[2:])
        ui = client.get_ui()
        if "error" in ui:
            print(f"Error: {ui['error']}")
            return
        matches = client.find_by_text(ui.get("tree", {}), text)
        print(f"Encontrados {len(matches)} nodos con texto que contiene '{text}':")
        for m in matches[:10]:
            print(f"  [{m['id']}] {m.get('className','').split('.')[-1]} | text='{m.get('text','')[:60]}' | clickable={m.get('clickable')}")
            
    elif cmd == "clickable":
        ui = client.get_ui()
        if "error" in ui:
            print(f"Error: {ui['error']}")
            return
        matches = client.find_clickable(ui.get("tree", {}))
        print(f"Nodos clickeables ({len(matches)}):")
        for m in matches[:20]:
            print(f"  [{m['id']}] {m.get('className','').split('.')[-1]} | text='{m.get('text','')[:40]}' | desc='{m.get('contentDescription','')[:40]}'")
            
    else:
        print(f"Comando desconocido: {cmd}")


if __name__ == "__main__":
    main()