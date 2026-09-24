import os
import json
import struct
import zlib
import hashlib
import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

ECM_PATH = r"C:\Users\dendo\Documents\Spun\EcM_Fungi_Richness_Predicted.tif"
HYPHAL_PATH = r"C:\Users\dendo\Documents\Spun\hyphal_density_m_cm3_Classified_mean.tif"
OUTPUT_DIR = r"c:\Users\dendo\Documents\GitHub\myco\app\src\main\assets\spun"
OUTPUT_BIN = os.path.join(OUTPUT_DIR, "spun_italy.bin")
OUTPUT_MANIFEST = os.path.join(OUTPUT_DIR, "SPUN_MANIFEST.json")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# Bounding box for Italy and surrounding Alpine / Mediterranean zone
MIN_LON, MAX_LON = 6.0, 19.0
MIN_LAT, MAX_LAT = 35.0, 47.5
STEP = 1.0 / 120.0  # 30 arc-seconds = 0.008333... deg

WIDTH = int(round((MAX_LON - MIN_LON) / STEP))   # 1560
HEIGHT = int(round((MAX_LAT - MIN_LAT) / STEP))  # 1500

print(f"Target dimensions: {WIDTH} cols x {HEIGHT} rows (STEP = {STEP})")

def compute_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

print("Calculating source SHA256 checksums...")
ecm_sha256 = compute_sha256(ECM_PATH)
hyp_sha256 = compute_sha256(HYPHAL_PATH)
print(f"  EcM Richness TIFF SHA256: {ecm_sha256}")
print(f"  Hyphal Density TIFF SHA256: {hyp_sha256}")

# 1. EcM Richness (Ectomycorrhizal fungi richness)
# Resolves F10: Mask NoData (-3.4e38) BEFORE bilinear resampling using normalized convolution
# to prevent ocean/NoData values from bleeding into coastal and border land pixels.
print("\n[1/3] Processing EcM Richness TIFF...")
im_ecm = Image.open(ECM_PATH)
scale_x = im_ecm.tag_v2[33550][0]
scale_y = im_ecm.tag_v2[33550][1]
tie_x = im_ecm.tag_v2[33922][3]
tie_y = im_ecm.tag_v2[33922][4]

x0 = int((MIN_LON - tie_x) / scale_x)
x1 = int((MAX_LON - tie_x) / scale_x)
y0 = int((tie_y - MAX_LAT) / scale_y)
y1 = int((tie_y - MIN_LAT) / scale_y)

print(f"  EcM crop window: ({x0}, {y0}) to ({x1}, {y1})")
crop_ecm = im_ecm.crop((x0, y0, x1, y1))
arr_raw_ecm = np.array(crop_ecm, dtype=np.float32)

# Mask valid pixels: positive values and not NaN
valid_mask_ecm = (arr_raw_ecm >= 0.0) & (arr_raw_ecm < 1000.0) & (~np.isnan(arr_raw_ecm))
data_plane_ecm = np.where(valid_mask_ecm, arr_raw_ecm, 0.0).astype(np.float32)
mask_plane_ecm = valid_mask_ecm.astype(np.float32)

# Normalized convolution bilinear resize
im_data_ecm = Image.fromarray(data_plane_ecm)
im_mask_ecm = Image.fromarray(mask_plane_ecm)
interp_data_ecm = np.array(im_data_ecm.resize((WIDTH, HEIGHT), Image.Resampling.BILINEAR))
interp_mask_ecm = np.array(im_mask_ecm.resize((WIDTH, HEIGHT), Image.Resampling.BILINEAR))

with np.errstate(divide='ignore', invalid='ignore'):
    res_ecm = np.where(interp_mask_ecm > 0.05, interp_data_ecm / interp_mask_ecm, 0.0)
res_ecm = np.nan_to_num(res_ecm, nan=0.0, posinf=0.0, neginf=0.0)
u8_ecm = np.clip(np.round(res_ecm), 0, 255).astype(np.uint8)

valid_ecm_count = np.count_nonzero(u8_ecm)
print(f"  EcM processed: {valid_ecm_count} valid pixels (>0), Max: {u8_ecm.max()}")

# 2. Hyphal Density (Arbuscular Mycorrhizal - AM hyphae)
# Note: F10 clarifies this dataset measures AM hyphae and is treated as experimental covariate.
print("\n[2/3] Processing Hyphal Density TIFF...")
im_hyp = Image.open(HYPHAL_PATH)
scale_x2 = im_hyp.tag_v2[33550][0]
scale_y2 = im_hyp.tag_v2[33550][1]
tie_x2 = im_hyp.tag_v2[33922][3]
tie_y2 = im_hyp.tag_v2[33922][4]

x0_2 = int((MIN_LON - tie_x2) / scale_x2)
x1_2 = int((MAX_LON - tie_x2) / scale_x2)
y0_2 = int((tie_y2 - MAX_LAT) / scale_y2)
y1_2 = int((tie_y2 - MIN_LAT) / scale_y2)

print(f"  Hyphal crop window: ({x0_2}, {y0_2}) to ({x1_2}, {y1_2})")
crop_hyp = im_hyp.crop((x0_2, y0_2, x1_2, y1_2))
if crop_hyp.size != (WIDTH, HEIGHT):
    crop_hyp = crop_hyp.resize((WIDTH, HEIGHT), Image.Resampling.BILINEAR)

arr_raw_hyp = np.array(crop_hyp, dtype=np.float32)
valid_mask_hyp = (~np.isnan(arr_raw_hyp)) & (arr_raw_hyp >= 0.0) & (arr_raw_hyp < 1000.0)
arr_clean_hyp = np.where(valid_mask_hyp, arr_raw_hyp, 0.0)

# Multiply by 20 so 1 uint8 step = 0.05 m/cm3 (range: 0 to 12.75 m/cm3)
u8_hyp = np.clip(np.round(arr_clean_hyp * 20.0), 0, 255).astype(np.uint8)
valid_hyp_count = np.count_nonzero(u8_hyp)
print(f"  Hyphal processed: {valid_hyp_count} valid pixels (>0), Max raw: {u8_hyp.max() / 20.0:.2f} m/cm3")

# 3. Create Binary File
print("\n[3/3] Creating and compressing binary atlas...")
header = struct.pack(
    ">4sH4sffffHHH",
    b"SPUN",
    1,
    b"ITA\x00",
    float(MIN_LAT),
    float(MAX_LAT),
    float(MIN_LON),
    float(MAX_LON),
    WIDTH,
    HEIGHT,
    30
)

assert len(header) == 32, f"Header size is {len(header)}, expected 32"

payload = u8_ecm.tobytes() + u8_hyp.tobytes()
compressed_payload = zlib.compress(payload, level=9)

with open(OUTPUT_BIN, "wb") as f:
    f.write(header)
    f.write(compressed_payload)

bin_size = os.path.getsize(OUTPUT_BIN)
bin_sha256 = compute_sha256(OUTPUT_BIN)
print(f"  Generated binary: {OUTPUT_BIN}")
print(f"  Binary size: {bin_size:,} bytes ({bin_size / 1024 / 1024:.2f} MB)")
print(f"  Binary SHA256: {bin_sha256}")

# 4. Generate Official Provenance Manifest (Resolves F10 requirement)
manifest_data = {
    "manifest_version": "1.0.0",
    "dataset_provider": "Society for the Protection of Underground Networks (SPUN)",
    "provider_url": "https://spun.earth",
    "doi_citation": "https://doi.org/10.1038/s41559-023-02118-2",
    "description": "Global mycorrhizal maps of ectomycorrhizal (EcM) fungal species richness and hyphal density (AM).",
    "layers": [
        {
            "id": "ecm_fungi_richness",
            "name": "Ectomycorrhizal Fungal Richness",
            "guild": "ECTOMYCORRHIZAL",
            "ecological_relevance": "Direct symbiont network richness for ectomycorrhizal fungi (Boletus, Cantharellus, Amanita, Lactarius)",
            "source_filename": os.path.basename(ECM_PATH),
            "source_filesize_bytes": os.path.getsize(ECM_PATH),
            "source_sha256": ecm_sha256,
            "unit": "predicted_species_count",
            "valid_pixels": int(valid_ecm_count),
            "resampling": "normalized_convolution_bilinear",
            "nodata_masking_applied": True,
            "max_value_raw": float(u8_ecm.max())
        },
        {
            "id": "hyphal_density",
            "name": "Hyphal Density Mean",
            "guild": "ARBUSCULAR_MYCORRHIZAL",
            "ecological_relevance": "Arbuscular Mycorrhizal (AM) hyphal network biomass. Treated as experimental research covariate; isolated from operational EcM/saprotrophic fruiting probability models (F10).",
            "source_filename": os.path.basename(HYPHAL_PATH),
            "source_filesize_bytes": os.path.getsize(HYPHAL_PATH),
            "source_sha256": hyp_sha256,
            "unit": "meters_per_cm3_soil",
            "scale_factor": 0.05,
            "max_representable": 12.75,
            "valid_pixels": int(valid_hyp_count),
            "nodata_masking_applied": True,
            "max_value_raw": float(u8_hyp.max() / 20.0)
        }
    ],
    "spatial_coverage": {
        "region_code": "ITA",
        "region_name": "Italia e arco alpino/mediterraneo",
        "min_latitude": MIN_LAT,
        "max_latitude": MAX_LAT,
        "min_longitude": MIN_LON,
        "max_longitude": MAX_LON,
        "step_arcseconds": 30,
        "step_degrees": STEP,
        "grid_width": WIDTH,
        "grid_height": HEIGHT,
        "total_cells": WIDTH * HEIGHT
    },
    "binary_asset": {
        "filename": os.path.basename(OUTPUT_BIN),
        "filesize_bytes": bin_size,
        "sha256": bin_sha256,
        "compression": "zlib_level_9",
        "header_size_bytes": 32
    }
}

with open(OUTPUT_MANIFEST, "w", encoding="utf-8") as f:
    json.dump(manifest_data, f, indent=2, ensure_ascii=False)

print(f"  Generated manifest: {OUTPUT_MANIFEST}")
print("\nBuild completed successfully!")
