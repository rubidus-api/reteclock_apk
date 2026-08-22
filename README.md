# reteclock

**English** · [한국어](README.ko.md)

**Turn an old Android phone into a bedside or desk clock.**

Plug the phone into its charger, stand it up, and reteclock fills the screen with the time in big
digits, along with the weekday and the date. It keeps the screen on while it runs, rearranges
itself when you turn the phone sideways, and quietly shifts what it draws by a pixel now and then
so the numbers never burn into an OLED screen.

There is nothing to sign up for and nothing to configure. It needs no account, no network and no
Play Store, so it still works on phones that can no longer install anything else. It is small —
about 317 KB — and asks for two permissions, both granted at install and never at runtime: one to
keep the screen awake, and one to vibrate, which only the timer uses.

> **If the clock does not answer your touch, it is not the clock.** Android does not pass touches to
> a screensaver (Daydream) or to anything showing over the lock screen — the first touch wakes the
> phone or dismisses the screensaver instead, and the app never hears it. So the menu, the timer's
> buttons, the calendar's arrows and the saying all do nothing there. To have a clock you can
> actually touch, run it as an app and turn on **Stay unlocked** in the settings: the screen stays
> on, the lock screen never covers it, and no screensaver takes over — and every touch reaches the
> clock.

## Download

**[⬇ Get it on F-Droid](https://f-droid.org/packages/com.reteclock/)** — recommended.

**[⬇ Download reteclock-0.30.2.apk](https://github.com/rubidus-api/reteclock_apk/releases/download/v0.30.2/reteclock-0.30.2.apk)**
— 317 KB, installs on Android 2.3 and newer. This is the file itself, so an old browser that cannot
render GitHub's release page can still fetch it.

The newest release is always at
[releases/latest](https://github.com/rubidus-api/reteclock_apk/releases/latest), and every release
is listed on the [Releases page](https://github.com/rubidus-api/reteclock_apk/releases).

### Setting it up, step by step

1. **Install it.** In F-Droid, search for *reteclock* and tap Install. If you downloaded the APK
   instead, open the file on the phone and confirm. On Android 4.4 and similar versions you may
   first have to allow installs from unknown sources, under Settings > Security.
2. **Open it** from the app list. The settings open first, with **Start the clock** at the top —
   there is nothing you have to set. If you would rather the button went straight to the clock,
   there is a switch for that on the same card.
3. **Press and hold the clock** whenever you want the settings again: colours, fonts, what is shown,
   and how large the time should be.
4. **Leave it on a stand, plugged in.** If you turn on *Start when the charger is connected*, the
   clock will come up by itself every time you plug the phone in.

To stop it, press Back or Home as with any app.

### Why F-Droid is the safer place to get it

You do not have to read this part to use the app — but if you have ever wondered whether an APK
from the internet is safe, here is the honest answer.

Downloading an APK from anywhere means trusting whoever built it. Nothing inside a finished APK
proves it was really made from the source code you can read here, and nothing proves that the
account which uploaded it had not been taken over by someone else.

F-Droid takes that trust out of the equation. Its listing is a
[reproducible build](https://f-droid.org/docs/Reproducible_Builds): F-Droid fetches this repository
onto its own server, compiles it there, and compares the result byte for byte with the APK
published above. It offers the app only if the two match. So the file F-Droid hands you is one an
independent party has confirmed came from the source in front of you — not merely one that somebody
with access to this repository uploaded. The F-Droid app also checks the signature on every update.

Both places serve the very same signed file, so you can move between them without uninstalling
anything.

The one cost is time. F-Droid rebuilds and republishes on its own schedule, so its copy is
sometimes a release or two behind this page. That wait is what you trade for having somebody else
check the build. If you are ever unsure about the download here, use F-Droid.

How the build recipe works and how it is verified:
[`docs/fdroid/README.md`](docs/fdroid/README.md).

## What it looks like

Screenshots taken on Android 4.4.2.

| Landscape (wide) | Portrait (tall) |
|---|---|
| <img src="docs/screenshots/landscape.png" alt="Landscape: a large bold HH:MM on the left, with seconds, weekday, date and year in a column on the right" width="420"> | <img src="docs/screenshots/portrait.png" alt="Portrait: bold hour and minute stacked, then the weekday with the date, then the year and the seconds" width="210"> |

The hour and the minute take every pixel the other lines do not need, and those lines are then
sized to whatever is left, so nothing is ever cut off, whatever phone you run it on. The text and
background colours are yours to choose. Boldness is one of the decorations you set line by line:
the hour and the minute start out bold, and you can turn that off.

Every line can be drawn in a font of your own, each with its own bold, italic, underline and outline. In the
picture below, the hour uses a TrueType font, the minute a font collection and the weekday and date
an OpenType one; the year is italic and the seconds are underlined. Every size was worked out by
the app.

<img src="docs/screenshots/fonts.png" alt="Portrait clock with a different user font on the hour, the minute and the date line, an italic year and underlined seconds" width="210">

You can also put your own pictures behind the clock — several take turns, and animated GIFs play —
fill the digits themselves with a picture, and decide how much of the screen the time should take.
It is all in [Settings](#settings).

### The timer

Switched on, an interval timer runs beside the clock — a pomodoro, a round of tabata, three minutes
for tea. The strip carries the whole preset: each interval takes the share of the length its
duration is worth, and wears one colour before the bar reaches it and another after, so the shape
of the session is there at a glance. The three times inside the bar are the current interval's
length, how far into it you are, and what is left of it.

| Landscape (wide) | Portrait (tall) |
|---|---|
| <img src="docs/screenshots/timer-landscape.png" alt="Landscape: the timer strip down the left, its first interval part red for the time already spent and part teal for the time to come, a green second interval above it, and the clock beside it" width="420"> | <img src="docs/screenshots/timer-portrait.png" alt="Portrait: the timer strip across the top reading 25:00, 8:21 and 16:39, with the bar part filled, above the clock" width="210"> |

Eight minutes into a twenty-five minute stretch of work, with five minutes of rest waiting behind
it. Switched off, the clock is exactly as it was: no strip, no controls, nothing extra drawn.

## What it is for

An old Android phone in a drawer is still a perfectly good screen with a perfectly good clock
inside it. This app is for exactly that: stand the phone up, plug in the charger, and let it be a
bedside or desk clock.

Old phones are hard to use for much else nowadays. Google has been retiring old Android versions
step by step — signing in to a Google account stopped working on Android 2.3.7 and older in
September 2021, Google Play services stopped being updated for Android 4.4 KitKat in 2023, and most
apps in the Play Store now require a far newer Android. But a phone that can no longer install
anything can still show you the time.

So reteclock asks for nothing: no Play Store, no Google account, no network, and no permission
beyond keeping the screen awake. Copy the APK to the phone, open it, and the phone has a job again.

## Which phones it runs on

Published on F-Droid; the latest release is 0.30.2.

| | |
|---|---|
| Oldest | **Android 2.3 Gingerbread** (2010) |
| Built and tested on | **Android 4.4 KitKat** (2013) — the reference platform |
| Newest | **no limit** — current Android versions (14, 15, 16 …) install it happily |

The settings screen shows this same range on the phone itself, next to the Android version it is
running, so you never have to guess.

## Settings

**The home-screen button opens the settings**, and so does **pressing and holding the clock**. The
clock says so once when you first open it, and stops mentioning it after you have been there — and
it never mentions it when the charger started the clock, so it stays quiet on a bedside stand.

**A tap on the clock opens the main menu**, which is where the settings divide up: *General
settings*, *Timer settings*, *Time and date settings*, *Fonts*, *Pictures*, *Sounds and bells* and
*Import / Export*, each its own screen. The general settings do not link to the others — one list of where things are
is enough, and it is the main menu.

The button opens this screen rather than the clock on purpose. A full-screen clock has no controls
on its face, so if a picture or a font you added ever makes it unusable, this is the way back in.
And if the clock does stop answering, the next start leaves your images and fonts out and says so,
so you can undo whatever caused it.

<img src="docs/screenshots/settings.png" alt="Settings: the screensaver row at the top, then show seconds, the twelve-hour option, the date format side by side, and the switch that lets the clock wander" width="240"> <img src="docs/screenshots/fields.png" alt="The Fonts page: the font library with each font's size and the total, then each field on two lines — its name with bold, italic, underline and outline, and its font — including AM / PM and the calendar's three parts" width="240">

In the order they appear on the screen.

**What is shown**

- **Show seconds** — on or off. With the seconds off, the hour and minute grow into the space they
  leave behind.
- **Date format** — `Jul 12` (short month name) or `07-12` (numbers).
- **12 hours, with AM and PM** — seven in the evening reads `7:19` rather than `19:19` (issue #24).
  Where the hour and the minute share a line, the marker sits small just after the minute on the
  same baseline; where they are stacked, it takes a line of its own under the minute: `12` over `03`
  over `AM`. Either way the layout keeps room for it, so nothing shifts when AM becomes PM.
- **What the marker says** — AM and PM are Latin abbreviations, and much of the world does not
  write them in Latin. Write your own for before noon, after noon, noon itself and midnight — 오전
  and 오후, 午前 and 午後, a dot, an arrow, nothing at all. Nothing is checked, so a long marker takes
  room from the time and a missing glyph comes out as a box. Blank means the built-in AM, PM, NN or
  MN; noon and midnight follow the after-noon and before-noon markers unless given their own; and
  the `00:43` reading stays bare whatever is typed. The options below are labelled with the reading
  your markers produce.
- **Noon is written / Midnight is written** — the one thing a twelve-hour clock cannot say plainly.
  AM means *before* midday and PM *after* it, so noon is neither, and midnight belongs to two days at
  once; American standards go as far as calling `12 a.m.` and `12 p.m.` ambiguous. Every clock uses
  them anyway and so does this one by default, but the alternatives are here because they are all in
  real use somewhere: `12:43 NN` and `12:43 MN` (the Philippines), `0:43 PM` and `0:43 AM` (Japan,
  where the twelfth hour is written zero), and for midnight simply `00:43`, the 24-hour way. Each
  choice is shown as the reading it produces, and applies to that hour only.
- **Let the clock wander, to spare the screen** — on unless you turn it off. Everything drifts a
  few pixels at a time around a circle it takes hours to complete, so no bright shape sits on the
  same pixels all day; it never leaves the margin, so nothing is clipped. Leave it on for a phone
  left showing the time, since an OLED that has drawn the same numerals in the same place for
  months keeps them faintly forever. Turn it off if you would rather the clock held still — an LCD
  has nothing to burn in.
- **A different picture every morning** — set the pictures to *shuffled, a new order each day* and
  the hold to *a day*, and the clock shows one of them from midnight to midnight and another
  tomorrow. The shuffle is decided by the date rather than by chance, so it survives a restart and
  the settings list shows what the clock is actually showing. Asked for in issue #26, and done this
  way because the alternative — reading a folder of yours — would mean a storage permission this
  app does not ask for; the pictures come in through *Import / Export* instead, a zip at a time.
- **Colours** — a text colour and a background colour, each chosen from a palette. The two can
  never be the same (a clock in its own background colour would be no clock at all), and the
  background colour shows wherever no picture covers it, including the bars at the edges when a
  picture is fitted whole.

**Fonts**

- **Font** — the clock uses the phone's own font until you add one of your own. *Add a font from a
  file* opens the usual file picker. The file is copied into the app, which is why reteclock never
  has to ask for permission to your storage, and why your font keeps working even if you later move
  or delete the original. TrueType (`.ttf`), OpenType (`.otf`) and font collections (`.ttc`) all
  work — the collection was really tried on Android 4.4 rather than assumed — and anything the
  phone cannot read is refused straight away instead of sitting broken in the list. The list shows
  how much space each font takes and the total, and any of them can be deleted.
- **Each line: font and decoration** — the hour, the minute, the seconds, the weekday, the month
  and day, and the year each choose their own font and their own **B**old, **I**talic and
  **U**nderline. The hour and minute begin bold; turn it off if you prefer. Sizes remain the app's
  job. If you delete a font that a line was using, that line simply goes back to the phone's font.
  A font file holds one weight and one slant, so bold and italic are drawn from it.

**Size**

- **Room for the time** — two sliders, one for each way you hold the phone: how much of the width
  the hour and minute take when it lies sideways, and how much of the height when it stands
  upright. Everything else sizes itself to fill its share, and stops growing before it would no
  longer fit.

**Pictures**

- **Images** — one pool holds every picture, brought in the same permission-free way as the fonts.
  Each picture has two ticks, and only one of them can be on: **BG** puts it behind the clock, and
  **Text** shows it inside the digits, so the numbers become a window onto the picture. With
  neither ticked it is simply kept, unused, until you want it. Each tick column has a select-all,
  and the first column is for picking several pictures to delete at once.
  Background pictures take turns: an animated GIF plays through once, a still picture stays for a
  time you choose (5 seconds to 5 minutes), each one cross-fades into the next unless you turn the
  fade off, and six fit modes between them handle any shape of picture. The pictures inside the
  digits cycle by the same rules. You can sort the pool by name or by the date each file was added,
  in either direction, arrange it by hand with the arrows, or tap a name to rename it — naming them
  `001 …`, `002 …` turns the A-to-Z sort into your own order. If you import a file the pool already
  has, it is recognised and not stored twice.

**Timer**

The clock can also run an interval timer — a pomodoro, a round of tabata, three minutes for tea. It
is off until you ask for it, and with it off the clock is exactly as it was: no strip, no controls,
nothing extra drawn.

- **Presets** — a preset is a named list of intervals. Each interval has its own length, typed in
  hours, minutes and seconds; two colours; a message spoken aloud at its beginning, if the phone has
  a speech engine; and a warning so many seconds before it ends. A preset can be told to repeat
  forever, which is what turns twenty seconds of work and ten of rest into a tabata.
- **On the clock face** — a strip runs down the left of the screen, or across the top when the phone
  stands upright. It carries the whole preset at once: each interval takes the share of the strip
  its length is worth, drawn in its own two colours — one for before the bar has reached it, one for
  after — so the shape of the session is there at a glance. Either colour may be left empty, and
  then the clock shows through. Three times are written inside the bar, all about the interval you
  are in: its length, how far into it you are, and what is left of it.
- **Counting in** — pressing play starts the preset three seconds later, with a low beep on each of
  those three seconds and a high one landing exactly on the start, so you can put the phone down and
  begin on the beat.
- **Sound, vibration or silence** — one setting covers everything the timer says. Whichever you
  choose, the end of each interval also flashes the screen three times. The tune at the end of a
  preset is the theme from the fourth movement of Schubert's *Trout* Quintet, which the phone plays
  from the notes rather than from a recording.
- **Putting it away** — the hourglass at the end of the strip opens the list of presets, and the
  first item there hides the timer. The strip empties, leaving the hourglass where it was, and the
  clock keeps exactly the shape and size it had. A timer that is running keeps running and keeps
  sounding; the same first item brings it back mid-count.
- **A running timer blinks** — the hourglass goes on and faint, once a second, for as long as
  something is counting. It blinks whether the strip is showing or put away, so a countdown
  somebody else started on a clock left on a shelf says so from across the room. Paused, it holds
  steady.
- **Switching the timer off stops it, rather than parking it** — turning off *Show the timer on
  the clock* takes the hourglass off the screen, and a countdown nobody can see is a countdown
  nobody can stop. So the run is ended and forgotten, not paused: switching the timer back on
  finds it at the beginning, not still going.

**Sounds and bells**

Its own screen, reached from the main menu.

- **Bring in sounds of your own** — the same way fonts and pictures arrive: the phone's own file
  picker hands the file over, and the app keeps a copy of it. **No storage permission is asked for**,
  on any version of Android, and a sound that is in stays in — nothing can take it away later.
- **What will play** — `.mp3` on every phone this app runs on, and beside it M4A/MP4, Ogg, 3GP, AMR,
  MIDI and WAV; FLAC and Opus on newer phones. The screen names the ones *your* phone can play. A
  file is opened as it comes in and one this phone will not play is refused there and then, rather
  than at the moment you needed it.
- **How each sound plays** — press *Clip…* to set where it starts and where it stops, in seconds to
  one decimal, and whether it plays once or over and over until it is stopped. Four seconds out of
  the middle of a song is four seconds, everywhere that song is used.
- **Bells** — a sound at a time of day, on the weekdays you choose. A bell only plays: touch the
  screen while one is ringing and it fades out and stops, and that touch does nothing else. Bells
  ring while the clock or the screensaver is showing.
- **The timer can use them too** — in *Timer settings*, each preset has a sound for its start and one
  for its finish, and each interval has one for its beginning and one for its warning. A message you
  set is still spoken: the sound and the words share the moment. A slot with no sound, or one whose
  file you deleted, beeps exactly as it always did.
- A whole folder of sounds travels in the zip on the *Import / Export* page, like fonts and pictures.

**Import / Export**

Its own screen, reached from the main menu.

- **Export** — two buttons, because there are two errands. **A settings file (`.txt`)** is plain
  text, one key to a line, in sections that match the settings pages: read it, edit it, hand it to
  another program, keep it as a backup. **A package (`.zip`)** is that same file with the fonts, the
  pictures and the sounds beside it under `fonts/`, `img/` and `sounds/` — what you want when the
  other phone has none of them. Either way you tick which pages to carry first, and the package has
  its own three ticks for the fonts, the pictures and the sounds.
- **Import** — the file is **read first and applied second**. You are shown what is in it — how
  many settings for each page, which fonts and pictures came along, and anything that could not be
  understood — and you tick what to bring in. What it shows is not the file's own text but the
  file **as this app read it**: rebuilt from what was understood, with values in the form they will
  be stored in, keys under the page they really belong to, and anything unreadable left out and
  listed separately. Your own comments are kept, above the lines they stood above. A zip written here, a zip you built by hand, or a
  bare `settings.ini` all work, and so does the tab-separated file older versions wrote.
- **What is refused, and why.** Names inside a package come from another machine, so they are
  checked before a byte of the file is read: at most 127 bytes, letters of any script, digits, `_`,
  `-`, `.` and the plain space, at most two combining marks in a row, and nothing invisible — no
  control or formatting characters, no bidi overrides (the trick that makes `evil‮gnp.ttf` read as
  a picture), no annotation characters, no enclosing marks or variation selectors, none of the
  letters that draw nothing at all (the Hangul fillers, the Khmer inherent vowels), and nothing
  outside `fonts/` and `img/`. Two marks is what Vietnamese needs and what the wall of dripping
  accents does not get. A name that fails is
  refused whole and named on screen with the reason. Neither direction asks for a storage
  permission.

**Time and date** — its own screen, reached from the main menu.

- **Count the date in** — the calendar the clock counts in. Fourteen of them, listed below. The
  choice changes every date the app shows: the month grid, the date beside the time, and the
  screensaver. The names that ship are Latin letters and ordinary digits throughout — and every
  month and weekday can be renamed to whatever you like, in any language, under **Your own names**
  on the same screen.
- **Show the Gregorian date as a small badge** — with another calendar counting, the Gregorian month
  and day appear as `08.15` reversed out of a small box at the lower left of the date, **and in the
  corner of every square of the month grid**, which is where the translation is most wanted: it
  answers "and which day of my own month is that" for the whole month at once. Off by default, and
  never shown when the Gregorian calendar is the one counting.
- **Shift the Islamic date by** — −2 to +2 days. An Islamic month begins when the new crescent is
  *seen*, so no computation is the announcement; set this once to match your own community and every
  date after it is right for you.
- **Show a calendar on the clock** — the month under the time, drawn as the clock's own writing: it
  takes the text colour, and the picture filling the digits fills it too. The month and a pair of
  arrows are on the first row, three-letter weekdays on the second, and six week rows always, so
  paging through the months does not make the grid breathe. Today is knocked out of a filled box —
  the numerals are cleared from the colour rather than painted over it, so a picture behind the
  clock shows through the digits themselves. The arrows page back and forth, and it returns to this
  month whenever the clock is opened again.
- **The months are spelled** / **The weekdays are written** — where a calendar is read by people who
  spell its months differently, both spellings are offered rather than one being picked as correct:
  Cairo's Baramhat beside the liturgy's Paremhat, Jakarta's Zulkaedah beside Cairo's Dhu al-Qidah,
  India's Ashwin beside the scholarly Asvina. The choices are shown as the abbreviations themselves.
  The weekdays stay English unless you ask for the calendar's own — `Yek Dos Ses Cha Pan Jom Sha` for
  the Persian calendar, `CN T2 T3 T4 T5 T6 T7` for the Vietnamese one, which is what Vietnam writes.
- **Your own names** — write your own name for any month or any weekday. No length limit and no
  alphabet limit: what ships is three or four Latin characters because that is what fits the grid
  on a small old screen, not because somebody else's language has to live under it. Past four
  characters the grid crowds and the lines shrink to fit, and a letter your chosen font has no
  glyph for comes out as a box. Leave one blank for the built-in name; each calendar keeps its own
  set.
- **The week starts on** — Sunday, Monday or Saturday. Iran and Israel begin the week on Saturday.
- **The month is written as** — `Aug 2026` or `2026-08`.
- **The clock takes its time from** — the phone, or an offset you set. Android carries its own table
  of time zone rules, and on an old phone that table is frozen: in every country that has changed or
  abolished summer time since — Brazil, Mexico, Iran, Turkey, Jordan, Egypt and others — the phone
  is an hour out for part of the year, and no app can correct it from the outside. Setting the offset
  here goes round it. The clock then asks the phone only *what moment it is*, which is a number that
  owes nothing to the zone table, and works out the rest itself. There is a list of places to pick an
  offset from, quarter-hour steps for the ones that are not whole hours (+5:30, +5:45, +12:45), a
  summer-time rule — none, Europe, United States and Canada, southern hemisphere, or your own dates —
  and, at the foot, both readings side by side so you can check them against your watch.

### The calendars

Every one of these is right for **every day from 1 January 1900 to 31 December 2200**, which is the
span the app promises. Outside it, and at the two edges where a calendar has nothing to say, the
clock shows the Gregorian date rather than a guess, and the paging arrows stop.

| Calendar | Where it is used, and roughly how many people | Kind |
|---|---|---|
| **Gregorian** | worldwide | civil |
| **Islamic** | ~2.0 billion Muslims | religious, civil |
| **Islamic Umm al-Qura** | Saudi Arabia and much of the Gulf — the reckoning most printed Hijri dates follow | religious, civil |
| **Islamic MABIMS** | Malaysia, Indonesia, Brunei and Singapore — around 300 million, from 2021 onward | religious, civil |
| **Chinese lunar** | China ~1.4 billion, and the diaspora | cultural |
| **Indian (Saka)** | India ~1.4 billion — the official civil calendar | civil |
| **Japanese** | Japan ~123 million | civil |
| **Ethiopian** | Ethiopia ~130 million | civil |
| **Vietnamese lunar** | Vietnam ~100 million | cultural |
| **Persian** | Iran ~92 million | civil |
| **Korean lunar** | South Korea ~52 million, North Korea ~26 million | cultural |
| **Thai Buddhist** | Thailand ~72 million | civil |
| **Minguo** | Taiwan ~23 million | civil |
| **Julian** | the Orthodox churches that keep it — Russian, Serbian, Georgian, Jerusalem | religious |
| **Coptic** | Egypt's Copts ~10–15 million, and the Coptic Orthodox Church | religious |
| **Hebrew** | Israel ~10 million, and the Jewish diaspora ~6 million | civil, religious |

A few things worth knowing about particular ones:

- **The Chinese, Korean and Vietnamese calendars are three tables, not one.** They are the same
  system computed at three different meridians, and that is enough to make them disagree: the same
  new moon at 00:12 in Seoul is the previous evening in Beijing, so Korea's new year falls a day
  after China's in 2027 and again in 2028. Vietnam's Tet fell a day before the Chinese new year in
  2007 for the same reason. A month in these calendars has a number rather than a name, and a leap
  month repeats the number before it: `M6` is the sixth month and `L6` the leap sixth.
- **The Ethiopian and Coptic calendars have thirteen months**, twelve of thirty days and a short one
  of five or six. The Hebrew calendar has twelve or thirteen depending on the year, and the lunisolar
  ones have a leap month that can fall anywhere.
- **The Japanese calendar cannot be computed forward.** An era ends when a reign does, on a date
  nobody can know in advance, so the app counts Reiwa onward from 2019 — an assumption, not a
  calculation. The dates before it are the real eras: Meiji, Taisho, Showa, Heisei.
- **The Julian calendar drifts by design.** It is thirteen days behind now, fourteen from 1 March
  2100 and fifteen from 2200, all of it inside the guaranteed span.
- **Minguo has no year before 1912**, when the Republic was founded, so earlier dates read Gregorian.

**Not included, and why.** These were looked at and left out rather than forgotten:

- **Bikram Sambat (Nepal, ~30 million)** — its month lengths follow solar transitions and are
  *published* year by year rather than derivable from a rule. It can be added the day authoritative
  data for the whole span can be obtained and checked; guessing at it would be worse than not having
  it.
- **Hindu Panchang (India, regionally)** — not one calendar but many, varying by region and school,
  each astronomical. There is no single answer to compute.
- **Sighting-based Hijri** — the date depends on an observation that has not happened yet. The
  Islamic calendar above, with the offset, is the honest substitute.
- **The Turkish (Diyanet) reckoning** — measured, and still out, but not by much. Diyanet computes
  its calendar rather than sighting it, so it ought to be reproducible. Against 38 month starts
  taken from Diyanet's own pages (2023–2026): the local rule — conjunction before sunset and moonset
  after sunset, reckoned for Ankara — gets **34**, a day late on four by a quarter of an hour of
  moonset margin; the 2016 congress rule — a crescent 8° from the sun and 5° up seen anywhere on
  Earth — gets **32**, a day early on six; **Umm al-Qura, which this app already ships, gets 35**.

  Those counts flatter the rules, because they ask the easy question: was the criterion met on the
  evening before a date somebody else already told us about. A calendar cannot be built that way —
  it has to be walked, month after month, each one deciding where the next begins — and walking the
  local rule from its own beginning reproduces only **29 of the 38**, including Ramazan 1447 a day
  early. Errors do not stay put: a month that ends a day late moves the evening the next month is
  judged on.

  So Turkey stays out, and the generator that would have baked the table refuses to write one while
  a Ramazan is wrong. **Umm al-Qura, which this app already ships, is the closest thing available**
  — 35 of 38 — and with the ±2 day offset it is what a Turkish user should reach for meanwhile.

**Prayer times and the direction of Mecca — with my apologies.** These were considered for this app
and they are not in it, and I would rather say why plainly than leave it looking like an oversight.

A prayer timetable is not a clock face. It is worked out for the exact place you are standing, for
that day's sun, and according to a calculation convention: the twilight angles that fix Fajr and
Isha differ between the recognised authorities, Asr depends on which school's shadow length is
followed, and the far north and south need further conventions again. The direction of Mecca is a
bearing from where you stand to the Kaaba, and for a phone to point at it the compass has to be
corrected for magnetic declination.

This app knows none of that and asks for none of it. It requests two permissions — to keep the
screen awake and to vibrate — and no location, no sensors and no network. Everything it shows, it
works out from the date and the offset you set. The old phones it is built for often have no
satellite fix and no magnetometer worth trusting.

So it could only ever offer an approximation, and this is the wrong thing to approximate: a quietly
wrong answer would cost the person praying, not the app. A simple clock for old devices is not the
right tool for this, and I do not plan to add it later. The apps built for the purpose do it
properly, and pointing at them is more use than doing it badly here.

What this app does carry for Muslim users is three Hijri reckonings — the arithmetic one, Umm
al-Qura, and MABIMS — and a two-day offset either way, for the months where the announcement and
the calculation part company. My thanks for your understanding.

| Landscape (wide) | Portrait (tall) |
|---|---|
| <img src="docs/screenshots/calendar-landscape.png" alt="Landscape: the hour and minute reading down the left with the seconds smaller beneath, the month filling the right-hand column, and a saying along the foot — all of it filled with a picture" width="420"> | <img src="docs/screenshots/calendar-portrait.png" alt="Portrait: the time on one line, the month beneath it, the seconds, and a saying at the foot, over a picture background with a second picture filling the digits" width="210"> |

  Both shots have a picture behind the clock and a second picture filling the writing, which is why
  the numerals are orange over blue — and why today's date shows the background through the digits
  rather than a colour.

  If the clock looks a little off-centre in these, that is on purpose and not a slip. Everything on
  the screen drifts very slowly — a few pixels at a time, around a circle it takes a long while to
  complete — so that no bright shape sits on the same pixels for hours. It is why a phone can be
  left showing this for years without the numbers etching themselves into the panel. The drift
  never leaves the margin, so nothing is ever clipped. That slow wandering is the app looking after
  your display.

  With the calendar on, the clock rearranges itself around it. Sideways, the time reads down the
  left — hour, minute, and the seconds smaller beneath — and the calendar takes the whole of the
  side column where the date and year used to be. Upright, the time becomes one line above the
  grid. Either way the weekday, the date and the year go: the calendar says them better. The slider
  that decides how much room the time takes still decides it.

**A saying**

- **Show a saying along the bottom** — a thin strip under the clock, about as deep as the timer's,
  carrying one saying and who said it. One a day, the same one all day, since a clock that changed
  what it said while you looked at it would be a thing that moves. Touch it for another. It wraps
  to one, two or three lines depending on how long it is and how much room the strip has, and it
  chooses its own font and decoration in the list of fields like every other line does.

  There are 6,678 of them, drawn from three nineteenth-century collections and all in the public
  domain by age:

  - Clouston, W. A., comp. *Book of Wise Sayings: Selected Largely from Eastern Sources*. London:
    Hutchinson & Co., 1893. 289 sayings, from eighty-three named hands — Sa'dí, Firdausí, the
    Dhammapada, the Hitopadesa, Goethe among them.
  - [Preston, Thomas], comp. *A Dictionary of English Proverbs and Proverbial Phrases*. London:
    Whittaker & Co., [n.d.]. 1,334 English proverbs. The title page names neither author nor year;
    the attribution is Project Gutenberg's.
  - Bohn, Henry G., comp. *A Polyglot of Foreign Proverbs*. London: Henry G. Bohn, 1857. 5,055
    proverbs in English rendering, marked by the language they came from — Spanish, Italian,
    French, Danish, Dutch and Portuguese.

  All three were published before 1900 and their compilers are long dead, so the texts are in the
  public domain worldwide. The transcriptions came from Project Gutenberg (ebooks 21130, 39281 and
  51090) with that project's header, footer and licence removed and its trademark unused, so
  nothing here carries any licence but this app's. They are all in
  `src/android/res/raw/quotes.txt`, one saying to a line, should you want to read them or replace
  them. The same citations are in `LICENSE`, and in the settings under the switch that turns them
  on.

**Starting**

- **Start when the charger is connected** — on or off. See below for what to do on newer Android.
- **Stay unlocked** — keeps the clock up past the lock screen: the screen never sleeps, the lock
  screen never covers it, and no screensaver takes over. Off by default, since showing over a lock
  screen is not something to do to somebody unasked.

## How to start it

- **From the app list**, like any other app.
- **Automatically when you plug in the charger** — switch this on in the settings. Android 10 and
  newer do not let an app open itself this way, so on those phones use the app list or the
  screensaver instead.
- **As the system screensaver** (Android calls it Daydream) on Android 4.2 and newer:
  Settings > Display > Daydream > reteclock.
- **From a desk dock**, if the phone reports being docked.

## For developers

Nothing below is needed to use the app.

### Stack

Java and the Android framework, nothing else. Built with the Android SDK command-line tools
(`aapt2`, `javac`, `d8`, `zipalign`, `apksigner`) driven by POSIX shell scripts. No Gradle. No
AndroidX, no support library, no Kotlin runtime and no third-party dependency — a single
`classes.dex` and an APK of about 317 KB. It is signed with the v1 (JAR) scheme so old phones
accept it, plus v2 and v3 for current ones, and it holds two normal permissions (`WAKE_LOCK` and
`VIBRATE`, the latter for the timer), both granted at install and never requested at runtime.

### Build

When this checkout sits in a workspace that has a sibling `usr/` directory, `scripts/build.sh`
picks up the shared JDK and Android SDK from it by itself. Anywhere else, point it at your tools:

```sh
export JAVA_HOME=/path/to/jdk-21              # only --release insists on this exact major version
export ANDROID_SDK_ROOT=/path/to/android-sdk   # build-tools 35.0.0 + platforms;android-19

scripts/build.sh             # dist/reteclock-<version>-debug.apk, signed with a local dev key
scripts/build.sh --release   # signed with your own release key (RETECLOCK_KEYSTORE)
scripts/build.sh --unsigned  # dist/reteclock-<version>-unsigned.apk, for F-Droid to sign
```

`scripts/build.sh` runs `aapt2` → `javac` (source 8, against the API 19 platform) → `d8`
(`--min-api 9`) → `zipalign` → `apksigner` (v1 + v2 + v3). It needs a JDK, the Android SDK
command-line tools, and Python 3. Any JDK will do for a debug build; `--release` requires JDK 21,
because F-Droid rebuilds the app and compares the result against the published APK, and different
javac versions do not produce identical bytecode. Nothing else — no Gradle, no downloads, no
dependencies. Every variable it reads is listed in `scripts/env.sh`.

This repository publishes only what is needed to build the app, plus this README with its
screenshots and the F-Droid submission material (`fastlane/`, `docs/fdroid/`). Working notes, plans
and test scaffolding are deliberately kept out of it.

## The name

**rete** is Latin for *net*, and *-clock* is just a clock.

The intended pronunciation is the Latin one: **RAY-teh** (two syllables, `rē-te`; the first vowel is
the long *e* of *they*, and the final *e* is pronounced, never silent).

If you would rather say it the way English usually treats this word, that is fine too. English
borrowed *rete* as an anatomical term and pronounces it **REE-tee**, so "REE-tee-clock" is a
perfectly good reading. Say it however you like; the clock does not mind.

## License

MIT. See `LICENSE`.
