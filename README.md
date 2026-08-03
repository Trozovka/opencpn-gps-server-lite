# OpenCPN GPS Server Free

Turns an Android phone into a Wi-Fi NMEA 0183 GPS server for [OpenCPN](https://opencpn.org/) running on a laptop, so a phone's GNSS chip can stand in for a USB GPS puck as OpenCPN's position source. The phone reads its own location and re-broadcasts fixes as `$GPGGA`/`$GPRMC`/`$GPVTG`/`$GPGSA` sentences over a TCP server socket; on the laptop, OpenCPN connects to it as a Network -> TCP client. There's no map or chart display on the phone itself -- the phone is purely the GPS relay, OpenCPN on the laptop is the actual ECDIS.

**This is a backup/testing GNSS source, not a certified primary navigation input.** Don't rely on it as your only means of position-fixing underway, and it isn't a type-approved ECDIS input.

This is the free **Free** tier: fully functional, but each server session auto-stops after 1 minute and needs a manual restart. A [Pro version](#pro-version) with unlimited runtime is sold separately.

Developed by [Trozovka](https://github.com/Trozovka).

## Status

Under active development. This README will be filled in further (setup instructions, features list, screenshots) as milestones land; see the project's task list for current progress. Not yet ready to install and rely on.

## Tech stack

- Kotlin + Jetpack Compose, MVVM
- Gradle multi-module: `:core` (service, NMEA formatting, UI - shared with the Pro tier) + `:app` (thin Free launcher)
- minSdk 26, target latest stable Android API
- No third-party open-source project was forked for this app; a couple of existing Android GPS-relay apps (e.g. [gpsdRelay](https://github.com/project-kaat/gpsdRelay)) were surveyed for approach but use a different network architecture (client to a gpsd server, rather than hosting a TCP server socket) and weren't reused.

## Why background location

The server has to keep streaming fixes to OpenCPN while the phone's screen is locked at the helm - that's its entire job. This requires `ACCESS_BACKGROUND_LOCATION`, a foreground service, and a partial wake lock while the server is running.

## Pro version

A Pro version with unlimited server runtime is available separately. Link will be added here once it's live on Gumroad. The Pro app's source is private; this Free repo has the full free-tier source, openly available under the MIT license below.

## License

MIT - see [LICENSE](LICENSE).
