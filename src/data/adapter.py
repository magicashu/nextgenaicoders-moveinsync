from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class DatasetProfile:
    source_path: Path
    csv_files: tuple[Path, ...]
    columns_by_file: dict[str, tuple[str, ...]]


class SyntheticDataAdapter:
    """Discovers the development fixture without exposing its schema to UI code."""

    def __init__(self, source_path: Path | None = None) -> None:
        root = Path(__file__).resolve().parents[2]
        self.source_path = source_path or root / "outputs" / "self generated dataset" / "synthetic_dataset_v1"

    def profile(self) -> DatasetProfile:
        files = tuple(sorted(self.source_path.glob("*.csv"))) if self.source_path.exists() else ()
        columns: dict[str, tuple[str, ...]] = {}
        for file in files:
            with file.open(newline="", encoding="utf-8") as handle:
                columns[file.name] = tuple(csv.DictReader(handle).fieldnames or ())
        return DatasetProfile(self.source_path, files, columns)

    def location_samples(self, limit: int = 12) -> list[dict[str, str | float]]:
        """Return normalized GPS samples for a service-owned map payload.

        This does not assign anomaly meaning or calculate severity; those remain
        the responsibility of the service layer.
        """
        gps_files = sorted(self.source_path.glob("*gps*.csv")) if self.source_path.exists() else []
        if not gps_files:
            return []
        samples: list[dict[str, str | float]] = []
        with gps_files[0].open(newline="", encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                try:
                    latitude, longitude = float(row["latitude"]), float(row["longitude"])
                except (KeyError, TypeError, ValueError):
                    continue
                samples.append({"trip_id": row.get("trip_id", "Unknown trip"), "latitude": latitude, "longitude": longitude})
                if len(samples) == limit:
                    break
        return samples
