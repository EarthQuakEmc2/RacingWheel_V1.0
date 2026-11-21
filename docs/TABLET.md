# Tablet layout

This branch targets a landscape Android tablet whose screen is 6 inches by 4.5 inches. That is a 4:3 picture, about 7.5 inches on the diagonal, and 960 by 720 dp.

The phone layout on `main` is unchanged. This branch keeps that layout when the window is a phone (short and wide) and switches when the window is at least 720 dp wide, 560 dp tall, and between 1.15 and 1.55 times as wide as it is tall.

What changes on that screen:

- Pedal strips are at least 136 dp wide, with larger numbers and labels.
- The top bar, the bottom bar, and the navigation targets are taller.
- Face-button and editor hit slop grows to 28 dp.
- The clutch strip, the setup list, the editor panel, and the garage cards are wider.
- Shifter zones keep the phone rules: 20% of the wheel width, 32% of its height on the grip, notched around buttons, and resizable in the editor. Left is still shift up and right is still shift down.

`versionName` is `0.5.1-tablet` and `versionCode` is 8, so a debug APK from this branch installs over the 0.5.1 phone build.
