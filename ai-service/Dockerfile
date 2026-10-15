# Hygiene analysis service (FastAPI + Ultralytics YOLO + OpenCV), CPU only.
FROM python:3.11-slim AS base
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 YOLO_CONFIG_DIR=/tmp/ultralytics
WORKDIR /app
# CPU-only PyTorch (the default wheels pull CUDA, several GB).
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --index-url https://download.pytorch.org/whl/cpu torch==2.14.1 torchvision==0.29.1
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

# `docker build --target test .` runs the test suite.
FROM base AS test
COPY requirements-dev.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements-dev.txt
COPY app app
COPY scripts scripts
COPY models/class_map.yaml models/class_map.yaml
COPY tests tests
RUN python -m pytest -q

FROM base AS runtime
COPY app app
COPY scripts scripts
COPY models/class_map.yaml models/class_map.yaml
RUN useradd --system --uid 10001 --home-dir /app app && mkdir -p /tmp/ultralytics && chown app /tmp/ultralytics
USER app
ENV LOG_FORMAT=json
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status == 200 else 1)"
# Weights are mounted at /app/models (see docker-compose.yml); without them /analyze answers 503.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
