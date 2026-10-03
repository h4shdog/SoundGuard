# SoundGuard — Database

This folder will contain the database schema and migration files for the SoundGuard system.

## Planned Structure

```
database/
├── schema.sql          # Table definitions
├── seed.sql            # Sample data for testing
├── migrations/         # Version-controlled schema changes
└── models/
    ├── AlertLog.js     # Alert history model
    ├── Device.js       # Wearable device model
    └── User.js         # User/profile model
```

## Schema Overview (Planned)

### alert_logs
| Column       | Type      | Description                        |
|--------------|-----------|------------------------------------|
| id           | INT PK    | Auto-increment primary key         |
| sound_type   | ENUM      | fire_alarm / siren / background    |
| model_used   | VARCHAR   | EfficientNet-B0 / MobileNetV3      |
| confidence   | FLOAT     | 0.0 – 1.0                          |
| inference_ms | INT       | Inference time in milliseconds     |
| timestamp    | DATETIME  | When the event was detected        |
| status       | ENUM      | confirmed / dismissed              |
| device_id    | FK        | References devices.id              |

### devices
| Column       | Type      | Description                        |
|--------------|-----------|------------------------------------|
| id           | INT PK    |                                    |
| name         | VARCHAR   | User-defined device name           |
| battery_pct  | INT       | 0–100                              |
| firmware     | VARCHAR   | Firmware version                   |
| last_seen    | DATETIME  |                                    |

## Stack (Planned)
- SQLite (prototype / wearable local storage)
- PostgreSQL (server-side production)
- SQLAlchemy ORM
