Braille Display Support
=======================

What Was Added
--------------

The project now bundles the historical BrailleBack display backend:

* `DisplayService`
* `DisplayClient` and related display AIDL interfaces
* BRLTTY-based native display drivers
* bundled key tables in `app/src/main/res/raw/keytables.zip`

This gives the app a modernized backend for detecting and connecting supported
Bluetooth and USB braille displays on current Android versions.

Recognized Device Families
--------------------------

Historically supported families still present in `DeviceFinder`:

* BraillePen
* EuroBraille Esys
* Freedom Scientific Focus
* HumanWare Brailliant
* HIMS / BrailleSense
* APH Refreshabraille
* Orbit Reader
* Baum VarioConnect / VarioUltra
* HWG Brailliant
* Papenmeier Braillex Trio
* Alva BC
* HandyTech
* Seika

Additional Model Patterns Added
-------------------------------

The detection layer was extended with newer model names inside already
supported driver families:

* Freedom Scientific:
  `Focus 80 BT`, `Focus Blue 14/40/80`, `Focus Blue 5th Generation`,
  `Focus 40 Blue 5`
* HumanWare:
  `Brailliant BI 14/20/40`, `Brailliant BI 20X`, `Brailliant BI 40X`,
  `Mantis Q40`
* HIMS:
  `BrailleSense 6`, `BrailleSense 6 MINI`
* Orbit:
  `Orbit Reader 20`, `Orbit Reader 20 Plus`, `Orbit Reader 40`

Current Capability
------------------

The project now includes a first usable accessibility integration layer on top
of the backend:

* `BrailleAccessibilityService` mirrors the focused Android UI text to the
  connected braille display,
* routing, panning, focus navigation, scrolling and global actions are handled
  through accessibility APIs,
* semantic navigation for sections, lists and controls now works both through
  accessibility HTML-element actions in web content and through native Android
  fallback traversal for headings, lists, tables and form controls,
* basic editable text commands are supported for braille key input, delete,
  forward delete and enter,
* the BRLTTY Android USB helper expected by `usb_android.c` is now implemented
  in Java, including USB permission requests,
* USB diagnostics now expose transport, VID/PID, interface count and exact
  profile matches for tuned models such as Focus, Brailliant, BrailleSense,
  Orbit and Refreshabraille families,
* raw physical key transitions now flow from native BRLTTY into diagnostics as
  `group/number/press` events,
* per-device binding-aware remapping is available, with runtime activation when
  a binding maps uniquely to one command,
* native remap now supports argument-aware targets, including preserve/fixed
  argument policies for route-style commands,
* a first pre-recognition raw-chord overlay now holds candidate raw presses and
  emits matched remapped commands before standard BRLTTY chord translation,
* the diagnostics UI includes command interpretation, the last rendered screen
  content, per-device table/profile controls and export of the current trace to
  a text file,
* the main screen and settings now include direct shortcuts to Android
  accessibility settings so the braille accessibility service can be enabled
  without hunting through system menus,
* the app bundles an offline quick start guide for setup and troubleshooting so
  core instructions do not depend on old external URLs.

Remaining Gaps
--------------

This is still not a full parity rebuild of historical BrailleBack:

* no verified hardware matrix yet on current physical displays,
* USB support has not yet been validated on real hardware,
* the new pre-recognition rewrite path still needs physical validation across
  different keymaps and firmware quirks,
* no deeper per-key rewrite layer yet for cases where a raw chord still needs
  to be decomposed below the current chord-level interception,
* semantics are richer now, but this is still not a full TalkBack-class screen
  reader with broader structural navigation, announcements and heuristics.

Modern Android Context
----------------------

Current Android accessibility guidance from Google is that braille displays
connect through TalkBack and no longer require BrailleBack as a separate app.
That means the highest-value future direction for this project is likely
interop, diagnostics and specialized input workflows rather than rebuilding an
entire parallel screen-reader stack from scratch.

Android Requirements
--------------------

* Android 12 and newer require `BLUETOOTH_CONNECT` runtime permission.
* The current build keeps legacy Bluetooth permissions for Android 11 and
  earlier.
