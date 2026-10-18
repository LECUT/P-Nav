# Third-Party Notices

P-Nav incorporates or links against the following third-party components.
Each component remains under its own license; the notices below are reproduced
or referenced as required.

---

## 1. RTKLIB v2.4.3 — BSD 2-Clause

The positioning core in `app/src/main/java/com/rtklib/` is a Java implementation
derived from RTKLIB 2.4.3 b34.

```
Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
```

Licensed under the BSD 2-Clause License. The full text is reproduced verbatim in
[`LICENSE-RTKLIB`](LICENSE-RTKLIB).

> Previous versions of RTKLIB until ver. 2.4.1 were distributed under GPLv3.

Source: <https://github.com/tomojitakasu/RTKLIB>

**Note.** The Java sources are rounded through a decompilation/porting step and
therefore do not carry the original per-file headers; the copyright notice is
instead asserted in the header block of every file under `com/rtklib/`.

---

## 2. myGNSS — BSD 2-Clause

P-Nav is a derivative work of the open-source myGNSS Android application.

> Jin, Y., Yan, W., Zhu, J., and Bastos, L. (2025). "myGNSS: A real-time
> high-accuracy positioning app with android smartphones." *SoftwareX*, 31,
> 102326. <https://doi.org/10.1016/j.softx.2025.102326>

Licensed under the BSD 2-Clause License (see the project's SoftwareX
specification table: "BSD-2-Clause License").

Source: <https://github.com/z1Yang-1/myGNSS>

Portions of the application layer (for example, the database helper, map view,
and NTRIP client) originate from myGNSS and have been substantially modified
and extended.

---

## 3. Apache Commons Net — Apache License 2.0

Used for FTP transfer of broadcast and precise ephemeris files.

Source: <https://commons.apache.org/proper/commons-net/>
License: <https://www.apache.org/licenses/LICENSE-2.0>

---

## 4. MPAndroidChart — Apache License 2.0

Used for charting in the quality-analysis interface.

- Author: Philipp Jahoda
- Source: <https://github.com/PhilJay/MPAndroidChart>
- License: <https://www.apache.org/licenses/LICENSE-2.0>

---

## 5. AndroidX / Material Components — Apache License 2.0

Includes `androidx.appcompat`, `androidx.constraintlayout`,
`androidx.lifecycle`, `androidx.navigation`, `androidx.recyclerview`,
`androidx.legacy`, `androidx.activity`, and `com.google.android.material`.

Source: <https://developer.android.com/jetpack/androidx>
License: <https://www.apache.org/licenses/LICENSE-2.0>

---

## 6. Baidu LBS Android SDK — Proprietary

Used for the map view on the positioning page. The SDK binaries
(`app/libs/BaiduLBS_Android.jar` and `app/src/main/jniLibs/**`) are bundled with
this repository so that the project builds out of the box, following the
practice of the upstream myGNSS project. The SDK package itself ships without a
license file; users who redistribute their own builds should verify that their
use complies with Baidu's terms.

API key: the value in `AndroidManifest.xml` is bound to package
`com.ecut.pnav` and the maintainers' signing certificate. Builds signed with a
different key require a separately obtained AK.

Source: <https://lbsyun.baidu.com/>

---

## 7. Reference implementations consulted

The RINEX 3.04 observation-code mapping and the Android observation-adapter
logic were informed by publicly available GnssLogger-to-RINEX converters and by
Google's GNSS Logger conventions. No code from these projects is redistributed
here; they are acknowledged as reference points.

- Google GNSS Logger / GNSS Analysis tools — <https://github.com/google/gps-measurement-tools>

---

## Compliance summary

| Component | License | Redistributed in this repo? |
|---|---|---|
| RTKLIB (Java port) | BSD 2-Clause | Yes — source, with copyright notice restored |
| myGNSS | BSD 2-Clause | Yes — derivative source |
| Apache Commons Net | Apache 2.0 | No — resolved via Gradle |
| MPAndroidChart | Apache 2.0 | No — resolved via Gradle |
| AndroidX / Material | Apache 2.0 | No — resolved via Gradle |
| Baidu LBS SDK | Proprietary | Yes — jar and native libs bundled |
