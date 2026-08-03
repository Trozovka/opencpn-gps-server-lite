# Gumroad Listing Copy — OpenCPN GPS Server Free

## Title
OpenCPN GPS Server Free — Wi-Fi GPS for OpenCPN

## Short description (one line, shows in search/cards)
Turn your Android phone into a Wi-Fi GPS source for OpenCPN — free, 1-minute sessions.

## Price
$0 (or "pay what you want" — Gumroad supports both; $0 is simplest if you just want it freely downloadable)

## Long description

**Turn your Android phone into a Wi-Fi GPS dongle for OpenCPN.**

If you navigate with OpenCPN on a laptop and don't have (or don't want to dig out) a USB GPS puck, this app turns any Android phone into one. It reads your phone's own GNSS chip and streams standard NMEA 0183 sentences ($GPGGA, $GPRMC, $GPVTG, $GPGSA) over your boat's Wi-Fi network. OpenCPN connects to it exactly the way it would connect to any other network GPS source: Network → TCP.

The phone doesn't try to be a chartplotter — it has no map of its own. It's purely a GPS relay. OpenCPN on your laptop remains the actual navigation display.

**What's free:**
- Full NMEA 0183 output (GGA/RMC/VTG/GSA) at 1Hz, correct checksums
- Runs as a real foreground service — keeps working with the screen locked, survives Doze/battery optimization once you grant the exemption
- Live telemetry: GPS status, lat/lon, speed, altitude, heading, sent sentence/byte counts, and fix-to-wire latency so you can see the feed is healthy
- Network interface picker if your phone has more than one active connection (Wi-Fi, hotspot, USB tether)
- Each session runs for **1 minute**, then auto-stops — a real trial, not a crippled demo. Just tap Start again.

**Not included in Free:** unlimited session runtime. That's the entire difference — see OpenCPN GPS Server Pro for continuous, uninterrupted operation.

**Important:** this is a backup/testing GNSS source, not a certified primary navigation input. Don't rely on it as your only means of position-fixing underway.

**Requirements:** Android 8.0 (Oreo) or newer. Installs via sideload (this isn't distributed through Google Play) — your phone will need "install from unknown sources" enabled for this app; that's expected for anything not from a store, not a sign of a problem.

**Source code:** fully open, MIT licensed — [github.com/Trozovka/opencpn-gps-server-lite](https://github.com/Trozovka/opencpn-gps-server-lite)

## Screenshots to upload (in `gumroad-assets/`, in this order)
1. `01_free_main_stopped.png` — main screen, idle, showing the clean UI and IP picker
2. `02_free_main_running.png` — main screen while running, live telemetry populated, a client connected
3. `03_free_settings_about.png` — Settings screen showing the About section and credit

*(Recommend capturing a fresh outdoor set before publishing — the ones here were captured indoors, where GPS fix latency and heading naturally look worse than they will underway.)*

## File to upload
The signed release APK once built (Section 8 below in the main handoff) — `app-release.apk`, renamed to something like `OpenCPN-GPS-Server-Free-v1.0.0.apk` for clarity in the downloads folder.
