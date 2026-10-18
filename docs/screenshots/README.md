# Screenshots

Place the four interface screenshots here with exactly these filenames
(referenced by the top-level `README.md`):

| Filename | Page | What should be visible |
|---|---|---|
| `1-collect.png` | Data collection | Recording state, timer and epoch count, four-constellation tiles, per-satellite signal-strength bars, record-info card, stop button |
| `2-skyview.png` | Satellite status | Location-info card (quality/fix, lat/lon/height, speed, PDOP), skyplot, satellite detail table |
| `3-quality.png` | Quality analysis | Satellite total with per-constellation counts, data-completeness bars, CN0 timeline / CN0 vs elevation / ionospheric tabs, ELEV and L1/L5 toggles |
| `4-positioning.png` | Positioning | Ephemeris and correction status, CORS / RTCM toggles, Auto–DGNSS–RTK selector, online map, bottom summary (coordinates, quality, band) |

Guidelines for publication-grade screenshots:

- Use a consistent device frame and resolution across all four.
- Prefer English UI for the international release; consider a second set under
  `docs/screenshots/zh/` for the Chinese readme.
- Strip any personal information from the map view (home/work locations, CORS
  account names, mount point identifiers).
- Keep file sizes reasonable (PNG, target < 500 KB each); the four-image strip
  in the README is rendered at roughly 25% width per pane.

