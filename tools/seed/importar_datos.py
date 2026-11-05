#!/usr/bin/env python3
"""Carga controlada de datos semilla exclusivamente mediante la API pública.

No almacena credenciales. Requiere un JWT de una sesión ADMIN en SEED_TOKEN o
en --token y recibe el archivo Excel original únicamente en modo lectura.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import uuid
from collections import defaultdict
from datetime import date, datetime
from pathlib import Path
from typing import Any
from urllib import error, request
from urllib.parse import urlparse

from openpyxl import load_workbook


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "tools" / "seed" / "assets"

# Datos ficticios, exclusivos para esta carga. No hay datos de personas o empresas reales.
WORKSHOP_ADJUSTMENTS = {
    "Taller Morado Norte": {
        "legalName": "Operadora Talleres Boreal Ficticia, S.A. de C.V.",
        "rfc": "OTB260115A1B", "personalPhone": "8001234511",
        "photo": ASSETS / "taller-generico.jpg",
    },
    "Taller Morado Sur": {
        "legalName": "Mecánica Meridiana Simulada, S.A. de C.V.",
        "rfc": "MMS260216C3D", "personalPhone": "8001234512",
        "photo": ASSETS / "taller-generico.jpg",
    },
    "Taller Morado Poniente": {
        "legalName": "Servicio Poniente Imaginario, S.A. de C.V.",
        "rfc": "SPI260317E4F", "personalPhone": "8001234513",
        "photo": ASSETS / "taller-generico.jpg",
        "neighborhood": "Puebla Centro",
    },
}

SERIALS = {
    "JM1NU07292T1WFUGK": "SEED260001", "KNAFN057SH75136G0": "SEED260002",
    "3HG8C420WFCBR19VA": "SEED260003", "3KPYU0J5285M41WJ7": "SEED260004",
    "KNAN02U43C9BA8B6B": "SEED260007", "1FMLEMR3RPMGX1YY9": "SEED260008",
}

# La llave es Cliente #. Las sustituciones conservan el CP y la calle.
ADDRESS_ADJUSTMENTS = {
    "26": {"neighborhood": "Hermosillo Centro"},
    "27": {"neighborhood": "Aguascalientes Centro"},
    "30": {"neighborhood": "Zona Central"},
    "31": {"state": "Coahuila de Zaragoza", "neighborhood": "Torreón Centro"},
    "32": {"neighborhood": "Guanajuato Centro"},
    "34": {"neighborhood": "Tepic Centro"},
    "35": {"neighborhood": "Colima Centro"},
    "36": {"neighborhood": "Tepatitlán de Morelos Centro"},
    "37": {"neighborhood": "Zacatecas Centro"},
    "38": {"neighborhood": "Tlaxcala Centro"},
    "40": {"neighborhood": "Nuevo Laredo Centro"},
    "41": {"neighborhood": "Ciudad Victoria Centro"},
    "42": {"neighborhood": "Chetumal Centro"},
    "45": {"neighborhood": "Puebla Centro"},
    "46": {"neighborhood": "Mérida Centro"},
}
# El CP 45000 no existe en el catálogo local, por lo que no puede sustituirse
# por una colonia/municipio del mismo CP sin alterar el CP (no autorizado).
UNRESOLVABLE_CLIENTS = {"25": "El código postal 45000 no existe en el catálogo SEPOMEX local"}


class ApiError(RuntimeError):
    def __init__(self, status: int, message: str):
        self.status, self.message = status, message
        super().__init__(f"HTTP {status}: {message}")


class Api:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def call(self, method: str, path: str, payload: dict[str, Any] | None = None, multipart: dict[str, Any] | None = None) -> Any:
        headers = {"Authorization": f"Bearer {self.token}"}
        data = None
        if multipart is not None:
            boundary = f"----seed-{uuid.uuid4().hex}"
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
            data = self._multipart(boundary, multipart)
        elif payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode("utf-8")
        req = request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with request.urlopen(req, timeout=30) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else None
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            try:
                message = json.loads(raw).get("message") or raw
            except json.JSONDecodeError:
                message = raw or exc.reason
            raise ApiError(exc.code, message) from None

    @staticmethod
    def _multipart(boundary: str, fields: dict[str, Any]) -> bytes:
        chunks: list[bytes] = []
        for name, value in fields.items():
            chunks.append(f"--{boundary}\r\n".encode())
            if isinstance(value, Path):
                content_type = "image/jpeg" if value.suffix.lower() in {".jpg", ".jpeg"} else "image/png"
                chunks.append((f'Content-Disposition: form-data; name="{name}"; filename="{value.name}"\r\n'
                               f"Content-Type: {content_type}\r\n\r\n").encode())
                chunks.append(value.read_bytes())
                chunks.append(b"\r\n")
            else:
                chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8"))
        chunks.append(f"--{boundary}--\r\n".encode())
        return b"".join(chunks)


def rows_from_sheet(sheet) -> list[dict[str, Any]]:
    values = list(sheet.iter_rows(values_only=True))
    headers = [str(item).strip() if item is not None else "" for item in values[0]]
    return [{headers[index]: value for index, value in enumerate(row)} for row in values[1:] if any(value is not None for value in row)]


def str_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def postal(value: Any) -> str:
    return str_value(value).zfill(5)


def iso_date(value: Any) -> str:
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    return datetime.strptime(str_value(value), "%d/%m/%Y").date().isoformat()


def normal_phone(value: Any) -> str:
    return "".join(char for char in str_value(value) if char.isdigit())


def normal_plate(value: Any) -> str:
    return "".join(char for char in str_value(value).upper() if char.isalnum())


def load_data(path: Path) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]], list[dict[str, Any]]]:
    workbook = load_workbook(path, read_only=True, data_only=True)
    workshops = [row for row in rows_from_sheet(workbook["Talleres"])
                 if str_value(row.get("Nombre del taller")) in WORKSHOP_ADJUSTMENTS]
    raw_rows = rows_from_sheet(workbook["Clientes y vehículos"])
    clients: dict[str, dict[str, Any]] = {}
    vehicles: list[dict[str, Any]] = []
    for raw in raw_rows:
        client_number = str_value(raw["Cliente #"])
        if client_number not in clients:
            client = dict(raw)
            client.pop("Año", None); client.pop("Marca", None); client.pop("Modelo", None)
            client.pop("VIN", None); client.pop("Número de serie", None); client.pop("Placa (como la escribes)", None)
            client.pop("Placa esperada en BD", None); client.pop("Versión", None); client.pop("Color", None); client.pop("Kilometraje", None)
            clients[client_number] = client
        if str_value(raw.get("VIN")):
            vehicle = dict(raw)
            vehicle["Cliente #"] = client_number
            vehicles.append(vehicle)
    return workshops, clients, vehicles


def workshop_form(row: dict[str, Any]) -> dict[str, Any]:
    name = str_value(row["Nombre del taller"])
    extra = WORKSHOP_ADJUSTMENTS[name]
    return {
        "name": name, "legalName": extra["legalName"], "rfc": extra["rfc"],
        "phone": normal_phone(row["Teléfono"]), "personalPhone": extra["personalPhone"],
        "email": str_value(row["Correo"]).lower(), "street": str_value(row["Calle"]),
        "neighborhood": extra.get("neighborhood", str_value(row["Colonia"])),
        "municipality": str_value(row["Municipio"]), "state": str_value(row["Estado"]),
        "postalCode": postal(row["Código postal"]), "photo": extra["photo"],
    }


def client_form(number: str, row: dict[str, Any], workshop_id: int) -> dict[str, Any]:
    adjusted = ADDRESS_ADJUSTMENTS.get(number, {})
    photo = ASSETS / ("cliente-hombre.jpg" if str_value(row["Foto (según género)"]).lower() == "hombre" else "cliente-mujer.jpg")
    return {
        "fullName": str_value(row["Nombre completo"]), "alternativeContactName": str_value(row["Contacto alternativo"]),
        "age": str_value(row["Edad"]), "birthDate": iso_date(row["Fecha de nacimiento"]),
        "phone": normal_phone(row["Teléfono personal"]), "occupation": str_value(row["Trabajo"]),
        "email": str_value(row["Email personal o laboral"]).lower(), "street": str_value(row["Calle"]),
        "neighborhood": adjusted.get("neighborhood", str_value(row["Colonia"])),
        "municipality": adjusted.get("municipality", str_value(row["Municipio"])),
        "state": adjusted.get("state", str_value(row["Estado"])), "postalCode": postal(row["Código postal"]),
        "workshopId": str(workshop_id), "photo": photo,
    }


def vehicle_form(row: dict[str, Any], client_id: int) -> dict[str, Any]:
    number = str_value(row["Cliente #"])
    return {
        "vin": str_value(row["VIN"]), "numeroSerie": SERIALS.get(str_value(row["VIN"]), str_value(row["Número de serie"])),
        "placa": str_value(row["Placa (como la escribes)"]), "marca": str_value(row["Marca"]),
        "modelo": str_value(row["Modelo"]), "anio": int(str_value(row["Año"])),
        "version": str_value(row["Versión"]), "color": str_value(row["Color"]),
        "kilometraje": int(str_value(row["Kilometraje"])) if str_value(row["Kilometraje"]) else None,
        "clienteId": client_id,
    }


def list_existing(api: Api) -> tuple[dict[str, Any], dict[str, Any], list[dict[str, Any]]]:
    workshops = {str_value(item["name"]).casefold(): item for item in api.call("GET", "/api/talleres")}
    clients = api.call("GET", "/api/clientes")
    return workshops, {str_value(item["email"]).lower(): item for item in clients}, api.call("GET", "/api/vehiculos")


def preflight(workshops: list[dict[str, Any]], clients: dict[str, dict[str, Any]], vehicles: list[dict[str, Any]], api: Api) -> dict[str, Any]:
    existing_workshops, existing_clients, existing_vehicles = list_existing(api)
    report = {"workshops_to_create": [], "workshops_skip": [], "clients_to_create": [], "clients_skip": [], "vehicles_to_create": [], "vehicles_skip": []}
    for row in workshops:
        name = str_value(row["Nombre del taller"])
        (report["workshops_skip"] if name.casefold() in existing_workshops else report["workshops_to_create"]).append(name)
    by_phone = {normal_phone(item.get("phone")): item for item in existing_clients.values()}
    for number, row in clients.items():
        name = str_value(row["Nombre completo"])
        if number in UNRESOLVABLE_CLIENTS:
            report["clients_skip"].append({"client": name, "reason": UNRESOLVABLE_CLIENTS[number]})
        elif str_value(row["Email personal o laboral"]).lower() in existing_clients or normal_phone(row["Teléfono personal"]) in by_phone:
            report["clients_skip"].append({"client": name, "reason": "correo o teléfono ya existente"})
        else:
            report["clients_to_create"].append(name)
    existing_vins = {str_value(item.get("vin")).upper() for item in existing_vehicles}
    existing_plates = {normal_plate(item.get("placa")) for item in existing_vehicles}
    for row in vehicles:
        label = f'{row["Cliente #"]}: {row["VIN"]}'
        if str_value(row["VIN"]).upper() in existing_vins or normal_plate(row["Placa (como la escribes)"]) in existing_plates:
            report["vehicles_skip"].append({"vehicle": label, "reason": "VIN o placa ya existente"})
        else:
            report["vehicles_to_create"].append(label)
    return report


def create_workshops(api: Api, workshops: list[dict[str, Any]], stats: dict[str, Any]) -> dict[str, int]:
    existing, _, _ = list_existing(api)
    result = {entry["name"]: int(entry["id"]) for entry in existing.values()}
    for row in workshops:
        form = workshop_form(row)
        if form["name"].casefold() in existing:
            stats["skipped"].append(("taller", form["name"], "ya existe")); continue
        try:
            response = api.call("POST", "/api/talleres", multipart=form)
            result[form["name"]] = int(response["id"])
            stats["created"]["workshops"] += 1
        except ApiError as exc:
            if 400 <= exc.status < 500:
                stats["rejected"].append(("taller", form["name"], exc.status, exc.message)); continue
            raise
    return result


def create_client(api: Api, number: str, row: dict[str, Any], workshop_ids: dict[str, int], stats: dict[str, Any]) -> int | None:
    name = str_value(row["Nombre completo"])
    if number in UNRESOLVABLE_CLIENTS:
        stats["skipped"].append(("cliente", name, UNRESOLVABLE_CLIENTS[number])); return None
    _, existing_clients, _ = list_existing(api)
    email = str_value(row["Email personal o laboral"]).lower()
    if email in existing_clients:
        stats["skipped"].append(("cliente", name, "correo ya existe")); return int(existing_clients[email]["id"])
    workshop_id = workshop_ids[str_value(row["Taller"])]
    try:
        response = api.call("POST", "/api/clientes", multipart=client_form(number, row, workshop_id))
        stats["created"]["clients"] += 1
        return int(response["id"])
    except ApiError as exc:
        if 400 <= exc.status < 500:
            stats["rejected"].append(("cliente", name, exc.status, exc.message)); return None
        raise


def create_vehicles(api: Api, number: str, rows: list[dict[str, Any]], client_id: int | None, stats: dict[str, Any], stop_on_error: bool = False) -> None:
    if client_id is None:
        return
    _, _, existing = list_existing(api)
    existing_vins = {str_value(item.get("vin")).upper() for item in existing}
    existing_plates = {normal_plate(item.get("placa")) for item in existing}
    for row in rows:
        body = vehicle_form(row, client_id)
        label = body["vin"]
        if body["vin"].upper() in existing_vins or normal_plate(body["placa"]) in existing_plates:
            stats["skipped"].append(("vehículo", label, "VIN o placa ya existe")); continue
        try:
            api.call("POST", "/api/vehiculos", payload=body)
            stats["created"]["vehicles"] += 1
            existing_vins.add(body["vin"].upper()); existing_plates.add(normal_plate(body["placa"]))
        except ApiError as exc:
            if stop_on_error or exc.status >= 500:
                raise
            stats["rejected"].append(("vehículo", label, exc.status, exc.message))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", type=Path, required=True)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--token", default=os.getenv("SEED_TOKEN", ""))
    parser.add_argument("--mode", choices=("preflight", "test", "import"), required=True)
    parser.add_argument("--client", default="22", help="Cliente # de la prueba")
    args = parser.parse_args()
    if not args.token:
        print("Falta SEED_TOKEN o --token.", file=sys.stderr); return 2
    parsed_url = urlparse(args.base_url)
    if parsed_url.hostname not in {"localhost", "127.0.0.1", "::1"}:
        print("La URL base debe apuntar exclusivamente a localhost.", file=sys.stderr); return 2
    workshops, clients, vehicles = load_data(args.file)
    api = Api(args.base_url, args.token)
    if args.mode == "preflight":
        print(json.dumps(preflight(workshops, clients, vehicles, api), ensure_ascii=False, indent=2)); return 0
    stats = {"created": defaultdict(int), "skipped": [], "rejected": []}
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for vehicle in vehicles:
        grouped[str_value(vehicle["Cliente #"])].append(vehicle)
    if args.mode == "test":
        client = clients[args.client]
        _, existing_clients, _ = list_existing(api)
        workshop_ids = {item["name"]: int(item["id"]) for item in api.call("GET", "/api/talleres")}
        client_id = create_client(api, args.client, client, workshop_ids, stats)
        create_vehicles(api, args.client, grouped[args.client], client_id, stats, stop_on_error=True)
    else:
        workshop_ids = create_workshops(api, workshops, stats)
        for number, client in clients.items():
            client_id = create_client(api, number, client, workshop_ids, stats)
            create_vehicles(api, number, grouped[number], client_id, stats)
    print(json.dumps({"created": dict(stats["created"]), "skipped": stats["skipped"], "rejected": stats["rejected"]}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
