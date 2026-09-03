import os
import struct
import zlib
import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

ECM_PATH = r"C:\Users\dendo\Documents\Spun\EcM_Fungi_Richness_Predicted.tif"
HYPHAL_PATH = r"C:\Users\dendo\Documents\Spun\hyphal_density_m_cm3_Classified_mean.tif"
OUTPUT_DIR = r"c:\Users\dendo\Documents\GitHub\myco\app\src\main\assets\spun"
OUTPUT_BIN = os.path.join(OUTPUT_DIR, "spun_italy.bin")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# Bounding box for Italy and surrounding Alpine / Mediterranean zone
MIN_LON, MAX_LON = 6.0, 19.0
MIN_LAT, MAX_LAT = 35.0, 47.5
STEP = 1.0 / 120.0  # 30 arc-seconds = 0.008333... deg

WIDTH = int(round((MAX_LON - MIN_LON) / STEP))   # 1560
HEIGHT = int(round((MAX_LAT - MIN_LAT) / STEP))  # 1500

print(f"Target dimensions: {WIDTH} cols x {HEIGHT} rows (STEP = {STEP})")

# 1. EcM Richness
print("Loading EcM Richness TIFF...")
im_ecm = Image.open(ECM_PATH)
scale_x = im_ecm.tag_v2[33550][0]
scale_y = im_ecm.tag_v2[33550][1]
tie_x = im_ecm.tag_v2[33922][3]
tie_y = im_ecm.tag_v2[33922][4]

x0 = int((MIN_LON - tie_x) / scale_x)
x1 = int((MAX_LON - tie_x) / scale_x)
y0 = int((tie_y - MAX_LAT) / scale_y)
y1 = int((tie_y - MIN_LAT) / scale_y)

print(f"EcM crop window: ({x0}, {y0}) to ({x1}, {y1})")
crop_ecm = im_ecm.crop((x0, y0, x1, y1)).resize((WIDTH, HEIGHT), Image.Resampling.BILINEAR)
arr_ecm = np.array(crop_ecm)
# Clean nodata / nan
arr_ecm = np.where(np.isnan(arr_ecm) | (arr_ecm < 0), 0.0, arr_ecm)
# EcM in Italy max is ~104. Round to integer and clip to 0..255
u8_ecm = np.clip(np.round(arr_ecm), 0, 255).astype(np.uint8)
print(f"EcM processed. Valid > 0: {np.count_nonzero(u8_ecm)}, Max: {u8_ecm.max()}")

# 2. Hyphal Density
print("Loading Hyphal Density TIFF...")
im_hyp = Image.open(HYPHAL_PATH)
scale_x2 = im_hyp.tag_v2[33550][0]
scale_y2 = im_hyp.tag_v2[33550][1]
tie_x2 = im_hyp.tag_v2[33922][3]
tie_y2 = im_hyp.tag_v2[33922][4]

x0_2 = int((MIN_LON - tie_x2) / scale_x2)
x1_2 = int((MAX_LON - tie_x2) / scale_x2)
y0_2 = int((tie_y2 - MAX_LAT) / scale_y2)
y1_2 = int((tie_y2 - MIN_LAT) / scale_y2)

print(f"Hyphal crop window: ({x0_2}, {y0_2}) to ({x1_2}, {y1_2})")
crop_hyp = im_hyp.crop((x0_2, y0_2, x1_2, y1_2))
if crop_hyp.size != (WIDTH, HEIGHT):
    crop_hyp = crop_hyp.resize((WIDTH, HEIGHT), Image.Resampling.BILINEAR)

arr_hyp = np.array(crop_hyp)
arr_hyp = np.where(np.isnan(arr_hyp) | (arr_hyp < 0), 0.0, arr_hyp)
# Multiply by 20 so 1 uint8 step = 0.05 m/cm3 (range: 0 to 12.75 m/cm3)
u8_hyp = np.clip(np.round(arr_hyp * 20.0), 0, 255).astype(np.uint8)
print(f"Hyphal processed. Valid > 0: {np.count_nonzero(u8_hyp)}, Max (raw): {u8_hyp.max() / 20.0:.2f} m/cm3")

# 3. Create Binary File
# Header format: 32 bytes
# 0..3:   Magic "SPUN" (4 bytes)
# 4..5:   Version (uint16) = 1
# 6..9:   Region Code (4 bytes ASCII, e.g. "ITA\0")
# 10..13: Min Lat (float32)
# 14..17: Max Lat (float32)
# 18..21: Min Lon (float32)
# 22..25: Max Lon (float32)
# 26..27: Width (uint16)
# 28..29: Height (uint16)
# 30..31: Reserved / StepArcSec (uint16) = 30
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

file_size = os.path.getsize(OUTPUT_BIN)
print(f"Successfully generated: {OUTPUT_BIN}")
print(f"Total file size: {file_size:,} bytes ({file_size / 1024 / 1024:.2f} MB)")
