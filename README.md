# FatMaxxer

<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshot-run-scaled-cropped.jpg" height="240"> |
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/garmin_alpha1_notification.jpg" height="240">

According to recent research (see below) this Android app may help you to exercise at the effort level that maximises fat burning, using a Polar H10 heart rate strap.

## Overview ##
Android app for the Polar H10 to advise Detrended Fluctuation Analysis alpha1 (⍺1) in real time.

There are promising signs that running or cycling at ⍺1==0.75 corresponds to the first ventilatory threshold "VT1" or FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).

The ⍺1 value and other features are calculated over a two minute rolling window of RR values, with ⍺1 calculated every 20 seconds (configurable). The RR values are subject to artifact filtering (RR interval changes by more than +/- 5%---configurable).

Provides a real time GUI (cycling), and updates via speech to text (speaker/headphones) or notifications (wearables).

## Getting started ##
- Read and thoroughly understand how to use DFA ⍺1 (https://the5krunner.com/2021/02/25/important-training-news-dfa-alpha-1-new-threshold-discovery-method-with-hrv/) for training
- Put on your Polar H10
- On first launch: enter Device ID into text field below "SET DEVICE ID", then press "SET DEVICE ID". App will immediately attempt connection and also on startup thereafter.
- Beware, the spoken output is currently hard-configured to work for my preferred MAF heart rate of approx. 130

## User Interface ##
Shows ⍺1 plus detected artifacts, number of samples and therefore artifact rate (%) over the window; elapsed time; instantaneous heart rate and heart rate variablility (RMSSD). Android UI screenshot above (shows out of date buttons) shows output after a recent run including a warmup to a HR in the 130--140 range, then steady at approx 137 bpm, with ⍺1 fluctuating between approx 0.75--1.0.

Graph plots
- primary axis (0-200)
  - red trace: HR
  - green trace: ⍺1 x 100 (e.g. 0.75 reads as 75)
- secondary axis
-- blue trace: artifacts (secondary axis)

## Audio/notification updates ##
Provided for ⍺1, HR and other selected features in a context-sensitive way, adjusting to work rate.
The value ⍺1 at HR above a hardcoded threshold, and RMSSD otherwise.
Artifact reporting above a hardcoded threshold, or at higher intensity.
Updates are more frequent at higher intensities.
Audible WAV sample (click) on dropped artifact.
Notifications are provided as a basic way to update a wearable:
Some Garmin devices can show notifications, including during runs (see photo).

## Logs ##
Log file output to "external" storage; may not work on Android versions later than 9ish.
- rr.log as per HRV Logger format
- artifacts.log - timestamp for artifacts*.log is corresponds to the last processed sample of the window (watch this space)

## Known issues ##
- Android pauses the app unpredictably. Interacting with the app regularly seems to help. Proposed solution---persistent notification.
- Make spoken output customizable to your approx. HR zones
- features.csv does not output SDNN

## Acknowledgements ##
- Marco Altini's Python colab
  (https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
  FatMaxxer ⍺1 has been checked to approximately correspond to this code.
- Polar API and example (https://github.com/polarofficial/polar-ble-sdk)
- GraphView (https://github.com/jjoe64/GraphView)
