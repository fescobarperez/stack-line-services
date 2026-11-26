#!/usr/bin/env python3
"""
Importa al ERP un catálogo en JSON: categorías, productos con su descripción y
la foto de cada uno. El formato es el del catálogo del sitio de Madera Viva
({"productos": [{"id", "nombre", "revista", "familia", "descripcion", ...}]}).

    # Simulación (por omisión): lee el ERP y revisa las fotos, no escribe nada.
    python3 -I scripts/importar_catalogo_json.py productos.json

    # De verdad, contra la empresa 2. --solo sirve para probar con uno primero.
    python3 -I scripts/importar_catalogo_json.py productos.json --aplicar --empresa 2 --solo P1

    # Quitar lo que creó una corrida (lee el detalle que dejó --aplicar junto al catálogo).
    python3 -I scripts/importar_catalogo_json.py --deshacer importados-productos.json --empresa 2

Credenciales en el .env del directorio (o en el entorno), nunca en la línea de
comandos ni en el repositorio:

    ERP_URL=https://...                    (por omisión http://localhost:8080)
    ERP_TOKEN=...                          o bien las tres siguientes:
    ERP_COMPANY_CODE=...  ERP_EMAIL=...  ERP_PASSWORD=...

Qué hace con cada producto:
- SKU = prefijo + id (MV-C3). Si ese SKU ya existe no se toca; solo se le sube
  la foto si no tiene. Volver a correrlo no duplica nada.
- Categoría = la revista; la familia, si viene, es una subcategoría.
- Precio 0: el ERP lo marca «por confirmar» hasta que la empresa ponga el suyo.
  El precio aproximado del catálogo va como texto en la descripción.
- Bajo pedido (no controla existencias), unidad «Unidad», vendible.
- Foto: se descarga, se reduce como en maya-ui (1000 px y miniatura de 240 px,
  JPEG 85) con `sips` (macOS) y se sube a PUT /api/products/{id}/image.

Solo usa la biblioteca estándar de Python.
"""
import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

# Los mismos topes que el ERP (ProductRequest, ProductImageService) y maya-ui (productImage.js).
MAX_DESCRIPTION = 4000
MAX_FULL_BYTES = 3 * 1024 * 1024
MAX_THUMB_BYTES = 512 * 1024
MAX_DOWNLOAD_BYTES = 15 * 1024 * 1024
FULL_PX = 1000
THUMB_PX = 240
JPEG_QUALITY = 85
USER_AGENT = "stackline-importador-catalogo/1"


class ErpError(Exception):
    def __init__(self, status, message):
        super().__init__(f"HTTP {status}: {message}")
        self.status = status


# ── Configuración ────────────────────────────────────────────────────────


def load_env(path):
    """KEY=VALUE del .env; lo que ya está en el entorno gana (igual que el ERP)."""
    values = {}
    if path and os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                values[k.strip()] = v.strip().strip('"').strip("'")
    for k in ("ERP_URL", "ERP_TOKEN", "ERP_COMPANY_CODE", "ERP_EMAIL", "ERP_PASSWORD"):
        if os.environ.get(k):
            values[k] = os.environ[k]
    return values


def jwt_company(token):
    """companyId del token, sin validar la firma (eso lo hace el ERP): solo para no equivocarse de empresa."""
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return json.loads(base64.urlsafe_b64decode(payload)).get("companyId")
    except (IndexError, ValueError):
        return None


# ── Cliente del ERP ──────────────────────────────────────────────────────


class Erp:
    def __init__(self, base_url, token=None):
        self.base = base_url.rstrip("/")
        self.token = token

    def call(self, method, path, body=None):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Accept", "application/json")
        req.add_header("User-Agent", USER_AGENT)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if self.token:
            req.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(req, timeout=60) as res:
                raw = res.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                message = json.loads(raw).get("message") or raw.decode("utf-8", "replace")
            except ValueError:
                message = raw.decode("utf-8", "replace")[:300]
            raise ErpError(e.code, message) from None

    def login(self, company_code, email, password):
        r = self.call("POST", "/api/auth/login", {"companyCode": company_code, "email": email, "password": password})
        self.token = r["token"]
        return r.get("user") or {}

    def categories(self):
        return self.call("GET", "/api/categories") or []

    def create_category(self, name, parent_id):
        return self.call("POST", "/api/categories", {"name": name, "icon": None, "parentId": parent_id})

    def product_by_sku(self, sku):
        q = urllib.parse.urlencode({"search": sku, "size": 50})
        page = self.call("GET", "/api/products?" + q) or {}
        rows = page.get("content", page) if isinstance(page, dict) else page
        return next((p for p in rows or [] if p.get("sku") == sku), None)

    def create_product(self, body):
        return self.call("POST", "/api/products", body)

    def upload_image(self, product_id, full, thumb):
        return self.call("PUT", f"/api/products/{product_id}/image", {
            "imageBase64": base64.b64encode(full).decode("ascii"), "imageMime": "image/jpeg",
            "thumbBase64": base64.b64encode(thumb).decode("ascii"), "thumbMime": "image/jpeg",
        })

    def delete(self, path):
        return self.call("DELETE", path)


# ── Catálogo ─────────────────────────────────────────────────────────────


def load_catalog(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    items = data.get("productos") if isinstance(data, dict) else data
    if not isinstance(items, list) or not items:
        sys.exit(f"{path}: no trae una lista «productos»")
    seen = set()
    for i, p in enumerate(items):
        for k in ("id", "nombre", "revista"):
            if not str(p.get(k) or "").strip():
                sys.exit(f"{path}: el producto #{i + 1} no trae «{k}»")
        if p["id"] in seen:
            sys.exit(f"{path}: el id {p['id']} está repetido")
        seen.add(p["id"])
    return items


def description(p):
    """Lo que el agente necesita para hablar del modelo, en un solo texto."""
    parts = [str(p.get(k) or "").strip() for k in ("descripcion", "beneficio")]
    extra = [("Materiales", p.get("materiales")), ("Estilo", p.get("estilo")),
             ("Precio de referencia", p.get("precio")), ("Ficha", p.get("enlace"))]
    lines = [f"{label}: {str(v).strip()}" for label, v in extra if str(v or "").strip()]
    text = "\n\n".join([x for x in parts if x] + (["\n".join(lines)] if lines else []))
    return text[:MAX_DESCRIPTION].rstrip() or None


def product_body(p, sku, category_id):
    return {
        "sku": sku, "name": p["nombre"].strip()[:200], "categoryId": category_id,
        "price": 0, "cost": 0, "unit": "Unidad", "purchaseUnit": None, "purchaseFactor": 1,
        "itemType": "sellable", "tracksStock": False, "minStock": 0, "status": "active",
        "supplierId": None, "supplierCost": None,
        "height": None, "width": None, "depth": None, "dimensionUnit": None,
        "description": description(p),
    }


# ── Categorías ───────────────────────────────────────────────────────────


class Categories:
    """Busca por nombre (sin distinguir mayúsculas) y padre; crea lo que falte si se aplica."""

    def __init__(self, erp, apply):
        self.erp, self.apply = erp, apply
        self.rows = erp.categories() if erp.token else []
        self.created = []

    def resolve(self, name, parent_id):
        key = name.strip().casefold()
        for c in self.rows:
            if c["name"].strip().casefold() == key and c.get("parentId") == parent_id:
                return c["id"], False
        if not self.apply:
            # Id ficticio y estable para que la simulación agrupe igual.
            fake = f"nueva:{parent_id}:{key}"
            self.rows.append({"id": fake, "name": name, "parentId": parent_id})
            return fake, True
        c = self.erp.create_category(name.strip(), parent_id)
        self.rows.append(c)
        self.created.append({"id": c["id"], "name": c["name"], "parentId": parent_id})
        return c["id"], True

    def for_item(self, p):
        root, new_root = self.resolve(p["revista"], None)
        family = str(p.get("familia") or "").strip()
        if not family:
            return root, p["revista"].strip(), new_root
        child, new_child = self.resolve(family, root)
        return child, f"{p['revista'].strip()} / {family}", new_root or new_child


# ── Fotos ────────────────────────────────────────────────────────────────


def image_kind(data):
    if data[:3] == b"\xff\xd8\xff":
        return "jpeg"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def head_image(url):
    """Revisión sin descargar: estado, tipo y tamaño."""
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            kind = res.headers.get("Content-Type", "")
            size = int(res.headers.get("Content-Length") or 0)
    except (urllib.error.URLError, ValueError) as e:
        return f"no responde ({e})"
    if not kind.startswith("image/"):
        return f"no es imagen ({kind})"
    if size > MAX_DOWNLOAD_BYTES:
        return f"pesa {size // 1024} KB, más de {MAX_DOWNLOAD_BYTES // 1048576} MB"
    return None


def download(url):
    if urllib.parse.urlparse(url).scheme not in ("https", "http"):
        raise ValueError("la foto no es una URL http(s)")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as res:
        data = res.read(MAX_DOWNLOAD_BYTES + 1)
    if len(data) > MAX_DOWNLOAD_BYTES:
        raise ValueError(f"la foto pesa más de {MAX_DOWNLOAD_BYTES // 1048576} MB")
    if not image_kind(data):
        raise ValueError("lo descargado no es una imagen JPEG, PNG ni WebP")
    return data


def sips_size(path):
    out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", path],
                         capture_output=True, text=True, check=True).stdout
    vals = dict(line.strip().split(": ", 1) for line in out.splitlines()[1:] if ": " in line)
    return int(vals["pixelWidth"]), int(vals["pixelHeight"])


def reduce(src, dst, max_px):
    """JPEG del lado mayor ≤ max_px; no agranda una foto chica."""
    w, h = sips_size(src)
    cmd = ["sips", "-s", "format", "jpeg", "-s", "formatOptions", str(JPEG_QUALITY)]
    if max(w, h) > max_px:
        cmd += ["-Z", str(max_px)]
    subprocess.run(cmd + [src, "--out", dst], capture_output=True, check=True)
    with open(dst, "rb") as f:
        return f.read()


def prepare_image(url, workdir, name):
    """(completa, miniatura) en JPEG, dentro de los topes del ERP."""
    data = download(url)
    folder = tempfile.mkdtemp(prefix="foto-", dir=workdir)
    name = re.sub(r"[^A-Za-z0-9_-]", "_", name)[:40] or "foto"  # el id viene del JSON
    src = os.path.join(folder, "original")
    with open(src, "wb") as f:
        f.write(data)
    full = reduce(src, os.path.join(folder, f"{name}.jpg"), FULL_PX)
    thumb = reduce(src, os.path.join(folder, f"{name}-mini.jpg"), THUMB_PX)
    if len(full) > MAX_FULL_BYTES or len(thumb) > MAX_THUMB_BYTES:
        raise ValueError(f"reducida sigue pesando de más ({len(full) // 1024} KB / {len(thumb) // 1024} KB)")
    return full, thumb


# ── Corridas ─────────────────────────────────────────────────────────────


def connect(env, empresa, writes):
    erp = Erp(env.get("ERP_URL") or "http://localhost:8080")
    who = None
    if env.get("ERP_TOKEN"):
        erp.token = env["ERP_TOKEN"]
    elif env.get("ERP_COMPANY_CODE") and env.get("ERP_EMAIL") and env.get("ERP_PASSWORD"):
        user = erp.login(env["ERP_COMPANY_CODE"], env["ERP_EMAIL"], env["ERP_PASSWORD"])
        who = user.get("companyName")
    elif writes:
        sys.exit("Faltan credenciales del ERP: ERP_TOKEN, o ERP_COMPANY_CODE + ERP_EMAIL + ERP_PASSWORD en el .env")
    else:
        print("Sin credenciales: simulación sin consultar el ERP (todo saldría como nuevo).\n")
        return erp
    company = jwt_company(erp.token)
    print(f"ERP {erp.base} · empresa {company}" + (f" ({who})" if who else ""))
    if writes and empresa is None:
        sys.exit(f"Para escribir hay que decir --empresa (el token es de la empresa {company}).")
    if writes and str(company) != str(empresa):
        sys.exit(f"El token es de la empresa {company} y se pidió --empresa {empresa}: no se escribe nada.")
    return erp


def run_import(args, env):
    items = load_catalog(args.catalogo)
    if args.solo:
        wanted = {x.strip() for x in args.solo.split(",") if x.strip()}
        missing = wanted - {p["id"] for p in items}
        if missing:
            sys.exit(f"--solo: no están en el catálogo: {', '.join(sorted(missing))}")
        items = [p for p in items if p["id"] in wanted]
    if args.aplicar and not shutil.which("sips"):
        sys.exit("Hace falta `sips` (macOS) para reducir las fotos.")

    erp = connect(env, args.empresa, args.aplicar)
    cats = Categories(erp, args.aplicar)
    workdir = tempfile.mkdtemp(prefix="importar-catalogo-")
    report, errors = [], 0
    print(("APLICANDO" if args.aplicar else "SIMULACIÓN (nada se escribe; --aplicar para hacerlo)") + f" · {len(items)} productos\n")

    for p in items:
        sku = args.prefijo + p["id"]
        row = {"id": p["id"], "sku": sku, "productId": None, "created": False, "category": None, "photo": None}
        try:
            existing = erp.product_by_sku(sku) if erp.token else None
            cat_id, row["category"], _ = cats.for_item(p)
            url = str(p.get("imagen") or "").strip()
            if existing:
                row["productId"] = existing["id"]
                action = "existe"
                needs_photo = existing.get("imageVersion") is None
            else:
                action = "crear"
                needs_photo = True
                if args.aplicar:
                    created = erp.create_product(product_body(p, sku, cat_id))
                    row["productId"], row["created"] = created["id"], True
            if not url:
                row["photo"] = "sin foto en el catálogo"
            elif not needs_photo:
                row["photo"] = "ya tiene"
            elif not args.aplicar:
                problem = head_image(url)
                row["photo"] = f"ERROR: {problem}" if problem else "se subiría"
                errors += bool(problem)
            else:
                try:
                    full, thumb = prepare_image(url, workdir, p["id"])
                    erp.upload_image(row["productId"], full, thumb)
                    row["photo"] = f"subida ({len(full) // 1024} KB / {len(thumb) // 1024} KB)"
                except (ValueError, OSError, subprocess.CalledProcessError, urllib.error.URLError, ErpError) as e:
                    row["photo"] = f"ERROR: {e}"
                    errors += 1
            row["action"] = action
        except ErpError as e:
            row["action"] = f"ERROR: {e}"
            errors += 1
        report.append(row)
        print(f"  {sku:<10} {row['action']:<8} {str(row['category'] or ''):<40} foto: {row['photo']}")

    shutil.rmtree(workdir, ignore_errors=True)
    new_cats = sum(1 for c in cats.rows if str(c["id"]).startswith("nueva:")) if not args.aplicar else len(cats.created)
    print(f"\n{sum(r['action'] == 'crear' for r in report)} por crear o creados · "
          f"{sum(r['action'] == 'existe' for r in report)} ya existían · {new_cats} categorías nuevas · {errors} errores")

    if args.aplicar:
        out = {"catalogo": os.path.basename(args.catalogo), "empresa": jwt_company(erp.token), "erp": erp.base,
               "fecha": datetime.now(timezone.utc).isoformat(timespec="seconds"),
               "categoriasCreadas": cats.created, "productos": report}
        with open(args.salida, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f"Detalle en {args.salida} (sirve para --deshacer y para configurar los tipos del planificador).")
    return 1 if errors else 0


def run_undo(args, env):
    with open(args.deshacer, encoding="utf-8") as f:
        done = json.load(f)
    erp = connect(env, args.empresa, True)
    if str(done.get("empresa")) != str(args.empresa) or done.get("erp") != erp.base:
        sys.exit(f"{args.deshacer} es de la empresa {done.get('empresa')} en {done.get('erp')}: no coincide.")
    errors = 0
    # Solo lo que esa corrida creó; lo que ya existía no se toca.
    for r in [r for r in done.get("productos", []) if r.get("created")]:
        try:
            erp.delete(f"/api/products/{r['productId']}")
            print(f"  producto {r['sku']} borrado")
        except ErpError as e:
            errors += 1
            print(f"  producto {r['sku']}: {e}")
    # Las subcategorías antes que sus padres.
    for c in sorted(done.get("categoriasCreadas", []), key=lambda c: c.get("parentId") is None):
        try:
            erp.delete(f"/api/categories/{c['id']}")
            print(f"  categoría {c['name']} borrada")
        except ErpError as e:
            errors += 1
            print(f"  categoría {c['name']}: {e}")
    return 1 if errors else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="Importa un catálogo JSON al ERP (categorías, productos y fotos).")
    ap.add_argument("catalogo", nargs="?", help="archivo JSON del catálogo")
    ap.add_argument("--aplicar", action="store_true", help="escribir en el ERP (sin esto es una simulación)")
    ap.add_argument("--empresa", type=int, help="id de la empresa; obligatorio para escribir y debe ser la del token")
    ap.add_argument("--prefijo", default="MV-", help="prefijo del SKU (por omisión MV-)")
    ap.add_argument("--solo", help="ids separados por coma, para probar con unos pocos")
    ap.add_argument("--salida", help="detalle de lo hecho (por omisión importados-<catálogo>.json junto al catálogo)")
    ap.add_argument("--deshacer", metavar="ARCHIVO", help="borra lo que creó la corrida de ese archivo")
    ap.add_argument("--env", default=".env", help="archivo con las credenciales (por omisión .env)")
    args = ap.parse_args(argv)
    env = load_env(args.env)
    if args.deshacer:
        return run_undo(args, env)
    if not args.catalogo:
        ap.error("falta el archivo del catálogo")
    if not args.salida:
        # Junto al catálogo y no en el directorio actual: así no cae dentro del repositorio.
        stem = os.path.splitext(os.path.basename(args.catalogo))[0]
        args.salida = os.path.join(os.path.dirname(os.path.abspath(args.catalogo)), f"importados-{stem}.json")
    return run_import(args, env)


if __name__ == "__main__":
    sys.exit(main())
