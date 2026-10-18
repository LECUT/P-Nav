# Screenshots

Interface screenshots referenced by the top-level `README.md`.

| Filename | Page | Content |
|---|---|---|
| `1-collect.png` | Data collection | Recording state, elapsed time and buffered epoch count, per-constellation satellite counts, per-signal strength bars, recording info, stop control |
| `2-skyview.png` | Satellite status | Location info (dual-frequency count, position, DOP, accuracy), skyplot with satellite detail popup |
| `3-quality.png` | Quality analysis | Satellite totals, per-constellation data completeness, C/N0 timeline with per-constellation filters and L1/L5 band switch |
| `4-positioning.png` | Positioning | Ephemeris and correction status, CORS / RTCM indicators, SPP–DGNSS–RTK selector, map view, position summary |

## Notes

- The four images were cropped from the original 4134×3071 captures down to
  the device frame, then stored as PNG.
- The screenshots were taken during a separate session (DOY 278) and are
  intended as interface documentation only; they are not the dataset used for
  the validation results under `results/`.
- The positioning page is shown in SPP mode. DGNSS and RTK use the same layout.
