# SoundGuard — Backend

This folder will contain the server-side logic for the SoundGuard assistive alert system.

## Planned Structure

```
backend/
├── api/
│   ├── classify.py       # Inference endpoint (EfficientNet-B0 / MobileNetV3)
│   ├── history.py        # Alert history CRUD
│   └── device.py         # Wearable device status
├── models/
│   ├── efficientnet_b0/  # Trained model weights
│   └── mobilenetv3/      # Trained model weights
├── preprocessing/
│   ├── spectrogram.py    # Log-Mel spectrogram conversion
│   └── augmentation.py   # Pitch shift, time stretch, noise addition
├── utils/
│   └── alert.py          # Alert notification logic
├── app.py                # Flask/FastAPI entry point
└── requirements.txt
```

## Stack (Planned)
- Python 3.10+
- FastAPI or Flask
- PyTorch / TensorFlow
- Librosa (audio preprocessing)
- ONNX Runtime (edge inference)
