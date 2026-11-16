# E-ink Apps

Small Android apps for e-ink screens, black and white or colour, that work just as well on normal screens. They are
built to be comfortable from 3.5 inch up. No ads, no accounts, no tracking.

| App | What it does |
| --- | --- |
| **Ink Pad** | Word processor: headings, bold, italic, underline, strike, sizes, fonts, lists, alignment, undo, find and replace. Opens and saves .docx and .odt, exports PDF, HTML and text. Can be locked with a PIN or password, or fingerprint or face. |
| **Weather** | Current conditions, warnings (US), hourly table, daily rows that open a full day with every hour, still satellite pictures, air quality and pollen. |
| **Clock** | Live clock (digital or analog), alarms that ring with snooze, timer, stopwatch, world clock. |
| **Calculator** | Basic and scientific calculator with memory and history, tip calculator, currency converter, unit converter. |
| **Calendar** | Month grid and agenda over the calendars already on the phone. |
| **Files** | File manager: Files, Recents and Storage tabs, favorites, sorting per folder, zip, search, SD cards, optional PIN or fingerprint lock. |

Each app is its own APK. Install the ones you want.

Looking for an e-ink home screen? See [Minimal Launcher Proper](https://github.com/Madmaxx636/minimal-launcher-proper),
made for the same screens.

## E-ink and normal screens

Each app works out whether it is on an e-ink panel and asks you the first time it opens, so it never depends on a guess.
Settings can change it at any time.

On e-ink you get plain black and white, no animation, lists that turn pages (swipe up and down, or Prev and Next, or the
volume keys) instead of scrolling, and a screen flash every few pages to clear ghosting. On a normal screen you get the
same layout with themes and ordinary scrolling. Colour e-ink panels use the normal colours.

Every app has the same Settings: screen type, theme, text size, button style (round, box, underline or none), button
position (top or bottom), icons on buttons, compact or comfortable sizing, and how the page turner behaves. Each app can
also move its own controls, such as Clock's tabs.

## Privacy

Most apps have no network access at all.

- **Weather** sends the coordinates of the cities you pick to open-meteo.com (forecast, air quality, pollen) and, for US
  cities, to api.weather.gov (warnings). Its satellite screen downloads one still picture from NOAA
  (cdn.star.nesdis.noaa.gov); the region is chosen on the phone, so no location is sent for that. It never asks for
  your location and does not update in the background.
- **Calculator** contacts api.frankfurter.dev for European Central Bank exchange rates, only when the currency converter
  opens with no saved rates or when you tap Update rates. Nothing you type is sent.
- **Ink Pad** and **Files** can be locked. Only a salted, slow hash of the PIN or password is stored.

Permissions: Clock (notifications and full-screen alarms), Calendar (read calendars), Files (all-files access, asked on
first use), Ink Pad and Files (biometrics, only if you use fingerprint or face unlock), Weather and Calculator (internet).

## Building

Needs JDK 21 and the Android SDK (platform 34, build-tools 34.0.0 or later).

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleDebug
```

For one app, use `./gradlew :notes:assembleDebug` (or `:weather`, `:clock`, `:calc`, `:calendar`, `:files`).

A release build is signed with a key read from the properties file named by the `MINIMAL_KEYSTORE_PROPERTIES`
environment variable, or `~/.minimal-launcher-keystore/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`). That file is not part of this repository. Without it a release build is unsigned.

## Code layout

`:core` has the shared screen type detection, theme, e-ink page turner, UI kit, app lock and settings screen. Each app
is its own module (`:notes` is Ink Pad, `:calc` is Calculator) and extends `InkActivity`. Built with Android's plain
View system and no third-party libraries.

## Status

Built and tested on a phone and on an e-ink device by their author, not yet on a wide range of devices. Bug reports with a
screenshot are welcome.
