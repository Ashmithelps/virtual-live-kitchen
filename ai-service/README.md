# Hygiene analysis service

FastAPI + Ultralytics YOLO + OpenCV. The backend calls it after a clip is READY; it samples one frame per second,
detects people and kitchen PPE, and returns per-frame detections plus a compliance summary.

```
POST /analyze   {"videoUrl": "<presigned clip URL>"}   header X-Api-Key: <AI_SERVICE_API_KEY>
GET  /health    → {"status": "ok" | "no_model", "modelVersion": …}
```

Response (abridged):

```json
{
  "modelVersion": "hygiene.pt@3fa1c2…",
  "durationSeconds": 45.0,
  "frames": [{"t": 0.0, "detections": [{"label": "person", "confidence": 0.91, "box": [0.1, 0.1, 0.6, 0.9]}]}],
  "summary": {
    "sampledFrames": 45, "personFrames": 41,
    "items": {
      "gloves":    {"compliancePct": 87.5, "compliantFrames": 28, "violationFrames": 4, "notEvaluatedFrames": 9, "violationExamples": [12.0, 20.0, 31.0]},
      "headCover": {"compliancePct": 100.0, …},
      "mask":      {"compliancePct": null, …}
    }
  }
}
```

**How compliance is counted.** Only frames with a person count, and a glove, cap or mask detection also implies a
person. For each item, a frame is a *violation* if the "no_…" class was detected and *compliant* if only the positive
class was. Otherwise it is *not evaluated*: hands or face are out of view, or the model can't tell.
`compliancePct = compliant / (compliant + violation)`, or `null` when nothing could be judged. `violationExamples`
holds up to 3 timestamps spread across the violations. The judgement is per frame, not per person: one bare hand among
three cooks makes the frame a violation.

Errors: 400 (URL not from an allowed storage host), 401 (API key), 413 (video too large), 422 (not a readable
video), 502 (download failed), 503 (no model installed). Videos are fetched only from `ALLOWED_VIDEO_HOSTS`, without
following redirects.

## Running

```bash
cp .env.example .env                 # set AI_SERVICE_API_KEY (same value as the backend's)
# With the rest of the stack: from infra/ (needs model weights in ai-service/models/, see below)
docker compose --profile ai up -d --build ai-service
# Tests (run in the Python 3.11 image)
docker build --target test .
```

Without weights the service starts, `/health` says `no_model`, and `/analyze` answers 503. The backend then leaves
clips unscored.

## The model

The service runs any Ultralytics detection model (`.pt`). Point `HYGIENE_MODEL_PATH` at the weights and map the
model's class names onto ours in `models/class_map.yaml`. Our labels are `person`, `gloves`, `no_gloves`,
`head_cover`, `no_head_cover`, `mask` and `no_mask`. Classes not in the map are ignored, and an item with no mapped
classes is always "not evaluated". The version stored with every result is `<file>@<sha256 prefix>`, or
`HYGIENE_MODEL_VERSION`.

**No fine-tuned weights are committed.** Kitchen-PPE weights must be trained (or obtained) and validated first:

1. **Get a dataset** in YOLO format with classes for person, gloves / no-gloves, hairnet / cap / no-head-cover, and
   mask / no-mask. Public kitchen and food-safety PPE datasets exist on Roboflow Universe (search "kitchen hygiene",
   "food safety PPE", "hairnet gloves mask"). Export them as "YOLOv8/YOLO11". Check each dataset's licence before
   commercial use. Mixing a general person/PPE set with real frames from your own kitchens works best. Label 200–500
   of your own frames.
2. **Train** (a GPU makes this hours instead of days):
   ```bash
   pip install -r requirements.txt          # Python 3.11
   python -m scripts.train --data /data/kitchen-ppe/data.yaml --base yolo11n.pt --epochs 80 --imgsz 640 --device 0
   ```
   The script prints the path of `best.pt` and the model's class names.
3. **Map the classes**: edit `models/class_map.yaml` so every class name of the new model maps to one of our labels.
4. **Evaluate** on a held-out labelled folder (YOLO format: `images/`, `labels/`, and a `data.yaml` naming the
   classes), never on training images:
   ```bash
   HYGIENE_MODEL_PATH=runs/hygiene/train/weights/best.pt \
   python -m scripts.eval --images /data/eval/images --labels /data/eval/labels --names /data/eval/data.yaml --json eval.json
   ```
   It prints precision and recall per label at IoU ≥ 0.5. Aim for precision ≥ 0.9 on the `no_*` classes before showing
   scores to the public: a false "no gloves" hurts a restaurant.
5. **Swap**: copy the weights to `models/hygiene.pt` (or set `HYGIENE_MODEL_PATH`) and restart the service. New
   results carry the new `modelVersion`, and the backend keeps old results with the version that produced them.

For a smoke test without PPE weights, the stock COCO `yolo11n.pt` works (`python -c "from ultralytics import YOLO;
YOLO('yolo11n.pt')"` downloads it). It only finds people, so every item is "not evaluated".
