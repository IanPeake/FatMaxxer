# FatMaxxer

![Screenshot](https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/Screenshot_20210606-151127_FatMax%20Optimiser_downscale.jpg)

Android app for the Polar H10 to advise Detrended Fluctuation Analysis alpha1 in real time.
There is preliminary research that running or cycling at DFA 0.75 corresponds to the first ventilatory threshold or FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).

Two minute rolling window for rmssd and alpha1. Alpha1 is evaluated every 20 seconds.

Artifact detection (RR interval changes too fast).

Realtime UI shows elapsed time, instantaneous heart rate, RMSSD and DFA alpha1 and detected artifacts / nr samples (percentage %) in the window.

Audio output advises HR, alpha1 and artifact rejection rate, adjusting to your work rate:
RMSSD at low heart rates above threshold.
Alpha1 at high heart rates above threshold.
Artifact reporting above threshold / at higher intensity.
Higher advice rates at higher intensity.
Audible WAV sample in real time on dropped artifact.

Graph plots
- green trace for alpha1 (values appear at 100x, e.g. 0.75 reads as 75, to share primary axis with HR)
- red trace for heart rate
- blue trace for RMSSD.

Main issue remaining: Android pauses the app unpredictably. Interacting with the app regularly seems to avert pausing.

Based on Marco Altini's Python code
(https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
Very rough, but alpha1 does seem to approximate Marco's code.
