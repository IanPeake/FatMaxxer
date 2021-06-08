# FatMaxxer

![Screenshot](https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshot-run-scaled.jpg)
|
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/garmin_alpha1_notification.jpg" width="200" alt="a1 notification to Garmin wearable">

Android app for the Polar H10 to advise Detrended Fluctuation Analysis alpha1 (⍺1) in real time.
There are promising signs that running or cycling at ⍺1==0.75 corresponds to the first ventilatory threshold "VT1" or FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).

The ⍺1 value and other features are calculated over a two minute rolling window of RR values, with ⍺1 calculated every 20 seconds (configurable). The RR values are subject to artifact filtering (RR interval changes by more than +/- 5%---configurable).

Realtime UI shows elapsed time, instantaneous heart rate, RMSSD and ⍺1 and detected artifacts / number of samples (percentage %) in the window. Android UI screenshot above (shows out of date buttons) from a recent run with warm up to HR in the 130--140 range, with ⍺1 fluctuating between approx 0.75--1.0.

Audio/notification updates for ⍺1, detected artifact rate, HR, etc.,
adjusting to work rate---HR and/or ⍺1.
RMSSD at HRs below a given threshold.
The value ⍺1 at HRs above a given threshold.
Artifact reporting above threshold / at higher intensity.
More frequent advice at higher intensity.
Audible WAV sample (click) on dropped artifact.
Some Garmin devices can show notifications, including during runs (see photo).

Graph plots
- primary axis (0-200)
  - red trace: HR
  - green trace: ⍺1 x 100 (e.g. 0.75 reads as 75)
- secondary axis
-- blue trace: artifacts (secondary axis)

Log file output to "external SD"; may not work on Android versions later than 9ish.
- rr.log as per HRV Logger format
- artifacts.log - timestamp for artifacts*.log is corresponds to the last processed sample of the window (watch this space)

Known issues:
- Android pauses the app unpredictably. Interacting with the app regularly seems to help. Proposed solution---persistent notification.
- Make spoken output customizable to your approx. HR zones
- features.csv does not output SDNN

Based on
- Marco Altini's Python code
(https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
The calculation ⍺1 has been tested briefly to correspond to that code.
- Polar API and example (https://github.com/polarofficial/polar-ble-sdk)
- https://github.com/jjoe64/GraphView

# Getting started
- Read and thoroughly understand how to use DFA ⍺1 (https://the5krunner.com/2021/02/25/important-training-news-dfa-alpha-1-new-threshold-discovery-method-with-hrv/) for training
- On first launch: enter Device ID into text field below "SET DEVICE ID", then press "SET DEVICE ID". App will immediately attempt connection and also on startup thereafter.
- Beware, the spoken output is currently hard-configured to work for my preferred MAF heart rate of approx. 130
