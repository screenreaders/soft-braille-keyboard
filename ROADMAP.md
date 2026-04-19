Suggested Feature Roadmap
=========================

High-value improvements for this application:

* Full braille display setup screen:
  pair, permission check, detect model, show connected cells, test input keys.
* Accessibility service integration:
  mirror focused text and UI content to external braille displays.
  Status: richer semantics now cover headings, landmarks, tables, forms and
  live-region-aware rendering; next step is broader navigation heuristics and
  more command coverage.
* USB braille display support:
  current backend now includes Android USB helper plumbing and permission flow.
  Status: implemented in code with VID/PID-tuned selectors and diagnostics, but
  still needs real hardware validation.
* Upgrade braille translation engine:
  move from old BrailleBack-era integration toward newer liblouis data and a
  clearer table management layer.
* Downloadable table packs:
  add or update braille tables without rebuilding the APK.
* Per-device keymap overrides:
  let users remap display buttons and chords.
  Status: binding-aware remap, argument-aware native remap and a first
  pre-recognition raw-chord interception path are implemented; deeper per-key
  physical-stream rewrite is still pending.
* Diagnostics screen:
  Bluetooth permission state, paired devices, recognized driver, connection
  log, table status, native library status.
  Status: implemented, including native raw key stream logging and exportable
  diagnostics traces; next step is collecting and validating traces on real
  hardware.
* Guided onboarding for blind users:
  first-run voice tutorial, IME enable flow, display pairing flow.
* Better speech and braille sync:
  coordinated TTS, cursor routing, selection announcements and typo feedback.
* Settings backup and restore:
  useful for teachers, institutions and repeated device setup.
* Profile switching:
  separate layouts/settings for phone, tablet and specific external displays.
  Status: basic preferred-device and per-device table override implemented.
* Modern input extras:
  clipboard history, undo/redo, macros, custom braille shortcuts, quick
  language switching.
