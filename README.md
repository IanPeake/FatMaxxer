# FatMaxxer

![Screenshot](https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshot-run-scaled.jpg)

Android app for the Polar H10 to advise Detrended Fluctuation Analysis alpha1 in real time.
There is preliminary research that running or cycling at Alpha1==0.75 corresponds to the first ventilatory threshold or FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).

Two minute rolling window for rmssd and alpha1. Alpha1 is evaluated every 20 seconds.

Artifact detection (RR interval changes too fast) and filtering.

Realtime UI shows elapsed time, instantaneous heart rate, RMSSD and DFA alpha1 and detected artifacts / number of samples (percentage %) in the window.

Audio output advises HR, alpha1 and detected artifact rate,
adjusting to work rate---HR and/or alpha1.
RMSSD at HRs below a given threshold.
Alpha1 at HRs above a given threshold.
Artifact reporting above threshold / at higher intensity.
More frequent advice at higher intensity.
Audible WAV sample (click) on dropped artifact.

Graph plots
- red trace: HR.
- green trace: ⍺1 (values are mapped to the range 0--200, e.g. 0.75 reads as 75, to share primary axis with HR.)
- blue trace: artifacts

Main issue remaining: Android pauses the app unpredictably. Interacting with the app regularly seems to avert pausing.

Based on
- Marco Altini's Python code
(https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
Alpha1 does seem to approximate Marco's code.
- Polar API and example (https://github.com/polarofficial/polar-ble-sdk)
- https://github.com/jjoe64/GraphView
