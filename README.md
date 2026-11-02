# Flip Fella Compass

A live compass for the Samsung Galaxy Watch Ultra in the Flip Fella style: a bezel that turns as you do, your heading on flip cards, and Pebble keeping an eye on where you're headed.

## What it does

- **Live bezel.** The ring turns in real time while the orange pointer at 12 stays put.
- **Heading on flip cards.** The digits flip as you turn, with the direction (N, NE and so on) underneath.
- **Bearings.** Tap to lock the way you're facing. A blue marker sits on the bezel and a pill tells you which way to turn ("12° right"). The watch gives a short buzz when you're back on course. Tap again to clear.
- **Pebble follows the way.** His eyes always look towards your bearing, or towards north when no bearing is set. He grins when you're on course.
- **Calibration.** If the compass reading turns unreliable, Pebble goes dizzy and asks for a figure of eight. Tap to skip.

The compass reads magnetic north. It only uses the sensors while it's on screen and lets the screen time out after three minutes without a tap.

## Get the APK

Every push to `main` builds the app on GitHub Actions and publishes a release. Open the [latest release](../../releases/latest) on your phone and download `flip-fella-compass.apk`.

## Install it

Same as the watch face: connect Bugjaeger over wireless debugging (watch Bluetooth off, pair, then connect) and install `flip-fella-compass.apk`.

## Open it from Flip Fella

Long press the Flip Fella face, tap **Customise**, choose **Top shortcut** and pick **Flip Fella Compass** from the app shortcuts.

## Fonts

Roboto Condensed and Saira Condensed, both under the SIL Open Font Licence. Licences are in `licences/`.
