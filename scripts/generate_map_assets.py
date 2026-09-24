#!/usr/bin/env python3
"""Generate offline map assets for Null0x Chat.

The script intentionally uses only Python's standard library so the map assets
can be refreshed on a clean developer machine without GDAL, QGIS or pip.
"""

from __future__ import annotations

import json
import math
import argparse
import struct
import tempfile
import urllib.request
import urllib.error
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "map"

IBGE_UF_2025_URL = (
    "https://geoftp.ibge.gov.br/organizacao_do_territorio/"
    "malhas_territoriais/malhas_municipais/municipio_2025/"
    "Brasil/BR_UF_2025.zip"
)
NE_110M_LAND_URL = "https://naciscdn.org/naturalearth/110m/physical/ne_110m_land.zip"
NE_50M_LAND_URL = "https://naciscdn.org/naturalearth/50m/physical/ne_50m_land.zip"
NE_50M_COUNTRY_BOUNDARIES_URL = (
    "https://naciscdn.org/naturalearth/50m/cultural/"
    "ne_50m_admin_0_boundary_lines_land.zip"
)
NE_10M_POPULATED_PLACES_URL = (
    "https://naciscdn.org/naturalearth/10m/cultural/"
    "ne_10m_populated_places_simple.zip"
)
GEONAMES_CITIES_URL = "https://download.geonames.org/export/dump/cities500.zip"
US_CENSUS_PLACES_URL = (
    "https://www2.census.gov/geo/docs/maps-data/data/gazetteer/"
    "2025_Gazetteer/2025_Gaz_place_national.zip"
)
ECONOMIC_COUNTRY_CODES = {"US", "CN", "DE", "JP", "IN", "GB", "FR", "IT", "CA"}

COORD_PRECISION = 5
BRAZIL_STATE_TOLERANCE_DEGREES = 0.01
COUNTRY_BOUNDARY_TOLERANCE_DEGREES = 0.015
MAX_CITY_ASSET_BYTES = 15 * 1024 * 1024

Point = tuple[float, float]
Ring = list[Point]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cities-only", action="store_true")
    args = parser.parse_args()
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="nullchat-map-") as temp_dir_raw:
        temp_dir = Path(temp_dir_raw)

        populated_places_zip = download(NE_10M_POPULATED_PLACES_URL, temp_dir)
        geonames_zip = download(GEONAMES_CITIES_URL, temp_dir)
        us_places_zip = download(US_CENSUS_PLACES_URL, temp_dir)
        natural_earth_places = populated_places_for_json(read_zipped_dbf(populated_places_zip))
        economic_places = economic_places_for_json(read_geonames_cities(geonames_zip))
        official_places = us_census_places_for_json(read_zipped_tabular(us_places_zip))
        city_asset = ASSET_DIR / "ne_populated_places.json"
        write_compact_json(
            city_asset,
            merge_populated_places(natural_earth_places, economic_places, official_places),
        )
        if city_asset.stat().st_size > MAX_CITY_ASSET_BYTES:
            raise RuntimeError("Generated city asset exceeds the 15 MB project limit")
        if args.cities_only:
            return

        ibge_shapes = read_zipped_shapefile(download(IBGE_UF_2025_URL, temp_dir))
        write_compact_json(
            ASSET_DIR / "br_state_boundaries.json",
            rings_to_boundary_features(internal_shared_lines(ibge_shapes)),
        )

        ne_110m_land = read_zipped_shapefile(download(NE_110M_LAND_URL, temp_dir))
        write_compact_json(
            ASSET_DIR / "ne_110m_land.min.geojson",
            rings_to_geojson_features(ne_110m_land),
        )

        ne_50m_land = read_zipped_shapefile(download(NE_50M_LAND_URL, temp_dir))
        write_compact_json(
            ASSET_DIR / "ne_50m_land.min.geojson",
            rings_to_geojson_features(ne_50m_land),
        )

        ne_country_boundaries = read_zipped_shapefile(
            download(NE_50M_COUNTRY_BOUNDARIES_URL, temp_dir)
        )
        country_rings = simplify_rings(
            flatten_shapes(ne_country_boundaries),
            tolerance=COUNTRY_BOUNDARY_TOLERANCE_DEGREES,
        )
        write_compact_json(
            ASSET_DIR / "ne_country_boundaries_50m.json",
            rings_to_boundary_features(country_rings),
        )

    old_country_asset = ASSET_DIR / "ne_country_boundaries_110m.json"
    if old_country_asset.exists():
        old_country_asset.unlink()


def read_zipped_dbf(zip_path: Path) -> list[dict[str, str]]:
    with zipfile.ZipFile(zip_path) as archive:
        dbf_name = next((name for name in archive.namelist() if name.lower().endswith(".dbf")), None)
        if dbf_name is None:
            raise RuntimeError(f"No .dbf file found in {zip_path}")
        data = archive.read(dbf_name)
    if len(data) < 32:
        raise RuntimeError(f"Invalid DBF file in {zip_path}")

    record_count = struct.unpack("<I", data[4:8])[0]
    header_size = struct.unpack("<H", data[8:10])[0]
    record_size = struct.unpack("<H", data[10:12])[0]
    fields: list[tuple[str, int]] = []
    offset = 32
    while offset + 32 <= header_size and data[offset] != 0x0D:
        descriptor = data[offset : offset + 32]
        name = descriptor[:11].split(b"\0", 1)[0].decode("ascii").strip().upper()
        fields.append((name, descriptor[16]))
        offset += 32

    records: list[dict[str, str]] = []
    for index in range(record_count):
        record = data[header_size + index * record_size : header_size + (index + 1) * record_size]
        if len(record) != record_size or record[:1] == b"*":
            continue
        values: dict[str, str] = {}
        field_offset = 1
        for name, length in fields:
            raw = record[field_offset : field_offset + length]
            values[name] = raw.decode("utf-8", errors="replace").strip()
            field_offset += length
        records.append(values)
    return records


def populated_places_for_json(records: list[dict[str, str]]) -> list[dict[str, object]]:
    places: list[dict[str, object]] = []
    for record in records:
        name = record.get("NAME", "").strip()
        try:
            latitude = float(record.get("LATITUDE", ""))
            longitude = float(record.get("LONGITUDE", ""))
        except ValueError:
            continue
        if not name:
            continue
        feature = record.get("FEATURECLA", "").lower()
        places.append(
            {
                "n": name,
                "lat": round(latitude, COORD_PRECISION),
                "lon": round(longitude, COORD_PRECISION),
                "rank": int(float(record.get("SCALERANK", "9") or 9)),
                "pop": int(float(record.get("POP_MAX", "0") or 0)),
                "cap": 1 if record.get("ADM0CAP", "0") == "1" or "admin-0 capital" in feature else 0,
                "min": float(record.get("MIN_ZOOM", "9") or 9),
            }
        )
    return places


def read_geonames_cities(zip_path: Path) -> list[list[str]]:
    with zipfile.ZipFile(zip_path) as archive:
        text_name = next((name for name in archive.namelist() if name.endswith(".txt")), None)
        if text_name is None:
            raise RuntimeError(f"No city text file found in {zip_path}")
        with archive.open(text_name) as source:
            return [
                line.decode("utf-8").rstrip("\n").split("\t")
                for line in source
            ]


def read_zipped_tabular(zip_path: Path) -> list[dict[str, str]]:
    with zipfile.ZipFile(zip_path) as archive:
        text_name = next((name for name in archive.namelist() if name.lower().endswith(".txt")), None)
        if text_name is None:
            raise RuntimeError(f"No tabular text file found in {zip_path}")
        with archive.open(text_name) as source:
            lines = (line.decode("utf-8-sig").rstrip("\r\n") for line in source)
            header = next(lines).split("\t")
            return [dict(zip(header, line.split("\t"))) for line in lines if line]


def economic_places_for_json(records: list[list[str]]) -> list[dict[str, object]]:
    places: list[dict[str, object]] = []
    for fields in records:
        if len(fields) < 15 or fields[8] not in ECONOMIC_COUNTRY_CODES:
            continue
        try:
            latitude = float(fields[4])
            longitude = float(fields[5])
            population = int(fields[14] or 0)
        except ValueError:
            continue
        name = fields[1].strip()
        if not name:
            continue
        feature_code = fields[7]
        is_capital = feature_code == "PPLC"
        places.append(
            {
                "n": name,
                "lat": round(latitude, COORD_PRECISION),
                "lon": round(longitude, COORD_PRECISION),
                "rank": economic_place_rank(population, is_capital),
                "pop": population,
                "cap": 1 if is_capital else 0,
                "min": economic_place_min_zoom(population, is_capital),
            }
        )
    return places


def us_census_places_for_json(records: list[dict[str, str]]) -> list[dict[str, object]]:
    places: list[dict[str, object]] = []
    for record in records:
        name = record.get("NAME", "").strip()
        try:
            latitude = float(record.get("INTPTLAT", ""))
            longitude = float(record.get("INTPTLONG", ""))
        except ValueError:
            continue
        if not name:
            continue
        places.append(
            {
                "n": name,
                "lat": round(latitude, COORD_PRECISION),
                "lon": round(longitude, COORD_PRECISION),
                "rank": 2,
                "pop": 0,
                "cap": 0,
                "min": 10.0,
            }
        )
    return places


def economic_place_rank(population: int, is_capital: bool) -> int:
    if is_capital or population >= 5_000_000:
        return 0
    if population >= 500_000:
        return 1
    return 2


def economic_place_min_zoom(population: int, is_capital: bool) -> float:
    if is_capital or population >= 5_000_000:
        return 4.5
    if population >= 1_000_000:
        return 6.0
    if population >= 200_000:
        return 7.0
    if population >= 50_000:
        return 8.0
    if population >= 10_000:
        return 9.0
    return 10.0


def merge_populated_places(
    natural_earth_places: list[dict[str, object]],
    economic_places: list[dict[str, object]],
    official_places: list[dict[str, object]],
) -> list[dict[str, object]]:
    merged = list(natural_earth_places)
    seen = {
        (str(place["n"]).casefold(), round(float(place["lat"]), 2), round(float(place["lon"]), 2))
        for place in natural_earth_places
    }
    for place in economic_places + official_places:
        key = (str(place["n"]).casefold(), round(float(place["lat"]), 2), round(float(place["lon"]), 2))
        if key not in seen:
            merged.append(place)
            seen.add(key)
    return merged


def download(url: str, temp_dir: Path) -> Path:
    target = temp_dir / Path(url).name
    partial = target.with_suffix(target.suffix + ".partial")
    request = urllib.request.Request(url, headers={"User-Agent": "Null0xChat-map-assets/1.0"})
    last_error: Exception | None = None
    for attempt in range(1, 4):
        print(f"Downloading {url} (attempt {attempt}/3)")
        try:
            with urllib.request.urlopen(request, timeout=300) as response, partial.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            partial.replace(target)
            return target
        except (OSError, TimeoutError, urllib.error.URLError) as error:
            last_error = error
            if partial.exists():
                partial.unlink()
    raise RuntimeError(f"Failed to download {url} after 3 attempts") from last_error


def read_zipped_shapefile(zip_path: Path) -> list[list[Ring]]:
    extract_dir = zip_path.parent / zip_path.stem
    extract_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(extract_dir)

    shp_files = sorted(extract_dir.rglob("*.shp"))
    if not shp_files:
        raise RuntimeError(f"No .shp file found in {zip_path}")
    return read_shapefile(shp_files[0])


def read_shapefile(shp_path: Path) -> list[list[Ring]]:
    data = shp_path.read_bytes()
    if len(data) < 100:
        raise RuntimeError(f"Invalid shapefile: {shp_path}")

    offset = 100
    shapes: list[list[Ring]] = []
    while offset + 8 <= len(data):
        _record_number, content_words = struct.unpack(">2i", data[offset : offset + 8])
        offset += 8
        content_bytes = content_words * 2
        record = data[offset : offset + content_bytes]
        offset += content_bytes
        if len(record) < 4:
            continue

        shape_type = struct.unpack("<i", record[:4])[0]
        if shape_type == 0:
            continue
        if shape_type not in (3, 5):
            raise RuntimeError(f"Unsupported shape type {shape_type} in {shp_path}")
        if len(record) < 44:
            continue

        num_parts, num_points = struct.unpack("<2i", record[36:44])
        parts_offset = 44
        points_offset = parts_offset + num_parts * 4
        if len(record) < points_offset + num_points * 16:
            continue

        parts = list(struct.unpack(f"<{num_parts}i", record[parts_offset:points_offset]))
        points = [
            struct.unpack("<2d", record[points_offset + index * 16 : points_offset + index * 16 + 16])
            for index in range(num_points)
        ]

        rings: list[Ring] = []
        for part_index, start in enumerate(parts):
            end = parts[part_index + 1] if part_index + 1 < len(parts) else num_points
            ring = [round_point(point) for point in points[start:end]]
            ring = remove_consecutive_duplicates(ring)
            if len(ring) >= 2:
                rings.append(ring)
        if rings:
            shapes.append(rings)
    return shapes


def internal_shared_lines(shapes: list[list[Ring]]) -> list[Ring]:
    edge_counts: dict[tuple[Point, Point], int] = defaultdict(int)
    edge_directions: dict[tuple[Point, Point], tuple[Point, Point]] = {}

    for shape in shapes:
        seen_in_shape: set[tuple[Point, Point]] = set()
        for ring in shape:
            if len(ring) < 3:
                continue
            closed = ring if ring[0] == ring[-1] else ring + [ring[0]]
            for start, end in zip(closed, closed[1:]):
                if start == end:
                    continue
                key = unordered_edge(start, end)
                if key in seen_in_shape:
                    continue
                seen_in_shape.add(key)
                edge_counts[key] += 1
                edge_directions.setdefault(key, (start, end))

    shared_edges = [
        edge_directions[key]
        for key, count in edge_counts.items()
        if count > 1
    ]
    chains = chain_edges(shared_edges)
    return simplify_rings(chains, tolerance=BRAZIL_STATE_TOLERANCE_DEGREES)


def chain_edges(edges: Iterable[tuple[Point, Point]]) -> list[Ring]:
    adjacency: dict[Point, set[Point]] = defaultdict(set)
    unused: set[tuple[Point, Point]] = set()
    for start, end in edges:
        key = unordered_edge(start, end)
        unused.add(key)
        adjacency[start].add(end)
        adjacency[end].add(start)

    chains: list[Ring] = []
    starts = sorted(adjacency.keys(), key=lambda point: (len(adjacency[point]) == 2, point[0], point[1]))

    for start in starts:
        while True:
            next_point = next_unused_neighbor(start, adjacency, unused)
            if next_point is None:
                break
            chains.append(walk_chain(start, next_point, adjacency, unused))

    while unused:
        start, next_point = next(iter(unused))
        chains.append(walk_chain(start, next_point, adjacency, unused))

    return [chain for chain in chains if len(chain) >= 2]


def walk_chain(
    start: Point,
    next_point: Point,
    adjacency: dict[Point, set[Point]],
    unused: set[tuple[Point, Point]],
) -> Ring:
    chain = [start, next_point]
    unused.discard(unordered_edge(start, next_point))
    previous = start
    current = next_point

    while len(adjacency[current]) == 2:
        candidates = [candidate for candidate in adjacency[current] if candidate != previous]
        if not candidates:
            break
        candidate = candidates[0]
        key = unordered_edge(current, candidate)
        if key not in unused:
            break
        chain.append(candidate)
        unused.remove(key)
        previous, current = current, candidate

    return chain


def next_unused_neighbor(
    point: Point,
    adjacency: dict[Point, set[Point]],
    unused: set[tuple[Point, Point]],
) -> Point | None:
    for candidate in sorted(adjacency[point]):
        if unordered_edge(point, candidate) in unused:
            return candidate
    return None


def flatten_shapes(shapes: list[list[Ring]]) -> list[Ring]:
    return [ring for shape in shapes for ring in shape if len(ring) >= 2]


def rings_to_geojson_features(shapes: list[list[Ring]]) -> dict[str, object]:
    features = []
    for shape in shapes:
        polygon = []
        for ring in shape:
            if len(ring) < 3:
                continue
            closed = ring if ring[0] == ring[-1] else ring + [ring[0]]
            polygon.append(points_for_json(closed))
        if polygon:
            features.append(
                {
                    "type": "Feature",
                    "geometry": {"type": "Polygon", "coordinates": polygon},
                }
            )
    return {"type": "FeatureCollection", "features": features}


def rings_to_boundary_features(rings: list[Ring]) -> list[dict[str, object]]:
    return [{"rings": [points_for_json(ring)]} for ring in rings if len(ring) >= 2]


def points_for_json(points: Ring) -> list[list[float]]:
    return [[round(lon, COORD_PRECISION), round(lat, COORD_PRECISION)] for lon, lat in points]


def simplify_rings(rings: list[Ring], tolerance: float) -> list[Ring]:
    simplified = []
    for ring in rings:
        clean = remove_consecutive_duplicates(ring)
        if len(clean) < 2:
            continue
        simplified_ring = douglas_peucker(clean, tolerance)
        simplified_ring = remove_consecutive_duplicates(simplified_ring)
        if len(simplified_ring) >= 2:
            simplified.append(simplified_ring)
    return simplified


def douglas_peucker(points: Ring, tolerance: float) -> Ring:
    if len(points) <= 2:
        return points

    first = points[0]
    last = points[-1]
    max_distance = -1.0
    max_index = 0

    for index in range(1, len(points) - 1):
        distance = perpendicular_distance(points[index], first, last)
        if distance > max_distance:
            max_distance = distance
            max_index = index

    if max_distance <= tolerance:
        return [first, last]

    left = douglas_peucker(points[: max_index + 1], tolerance)
    right = douglas_peucker(points[max_index:], tolerance)
    return left[:-1] + right


def perpendicular_distance(point: Point, line_start: Point, line_end: Point) -> float:
    x, y = point
    x1, y1 = line_start
    x2, y2 = line_end
    dx = x2 - x1
    dy = y2 - y1
    if dx == 0 and dy == 0:
        return math.hypot(x - x1, y - y1)
    return abs(dy * x - dx * y + x2 * y1 - y2 * x1) / math.hypot(dx, dy)


def remove_consecutive_duplicates(points: Ring) -> Ring:
    clean: Ring = []
    for point in points:
        if not clean or clean[-1] != point:
            clean.append(point)
    return clean


def unordered_edge(start: Point, end: Point) -> tuple[Point, Point]:
    return (start, end) if start <= end else (end, start)


def round_point(point: tuple[float, float]) -> Point:
    return (round(point[0], COORD_PRECISION), round(point[1], COORD_PRECISION))


def write_compact_json(path: Path, value: object) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
