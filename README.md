# <img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/Fatmaxxer_Icon_v0.2.png" height="36"/> FatMaxxer

According to recent research (see below) the FatMaxxer Android app may help you to exercise at the optimum effort level for fat burning,
measured using just a Polar H10 heart rate strap.

Free public test of the (upcoming) paid version on the Google Play store: https://play.google.com/store/apps/details?id=online.fatmaxxer.publicRelease1

See Bruce Rogers' review here: http://www.muscleoxygentraining.com/2021/06/fatmaxxer-new-app-for-real-time-dfa-a1.html.

<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshot-run-scaled-cropped.jpg" height="240" alt="Screenshot"/> | <img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/garmin_alpha1_notification.jpg" height="240" alt="Garmin notification"/>

## Overview ##
Android app for the Polar H10 to advise Detrended Fluctuation Analysis alpha1 (⍺1) in real time.

There are promising signs that running or cycling at ⍺1 = 0.75 corresponds to the first ventilatory threshold "VT1" or roughly FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).
This requires a reliable heart rate strap that can measure inter-heartbeat intervals very accurately (Polar H10)
and a tool to measure ⍺1.

FatMaxxer reports ⍺1 for the past two minutes in "near real time" via the GUI,
speech (speaker/headphones, configurable) and notifications (configurable).
The ⍺1 value and other features are calculated over the two minute rolling window of RR values,
with ⍺1 calculated every 20 seconds (configurable).
The RR stream is subject to artifact filtering, where adjacent RR intervals change by more than +/- threshold (%).
Threshold settings are 5%, 25% and "Auto".
The "Auto" setting uses a threshold of 5% when HR > 90 BPM and 25% when HR < 85 BPM.

## Status: Testers Wanted ##
FatMaxxer is in a very early stage of development.
The app is provided on Github as a APK (debug version) and as a public test release on the Google Play store.
Email fatmaxxer@gmail.com for access to more up to date releases through the Play Store.
The Play Store public beta releases lag behind the APK version on Github by as much as 24 hours.

The app may crash or not work properly.
*To help me fix your issue efficiently* use the Github issue tracker above to report bugs.
On a first time install you may have to manually set the preferences: lambda = 500, alpha1 calculation period = 10, threshold = "Auto", etc.

## License
Apache 2.0 for any code that was authored by me.

## Getting started ##
- Read and thoroughly understand how to use DFA ⍺1 (https://the5krunner.com/2021/02/25/important-training-news-dfa-alpha-1-new-threshold-discovery-method-with-hrv/) for training
- Put on your Polar H10
- On first launch, select your device in the menu from those discovered.
  Alternatively set preferred Device ID manually under settings, quit and restart.
  Device ID is an 8 digit hexadecimal string.
  The first device successfully connected will become your preferred device.
  On startup, app will try to connect to your preferred device.

## User Interface ##
Shows ⍺1, plus detected artifacts, number of samples and therefore artifact rate (%) over the window; elapsed time; instantaneous heart rate and heart rate variablility (RMSSD). Android UI screenshot above (shows out of date buttons) shows output after a recent run including a warmup to a HR in the 130--140 range, then steady at approx 137 bpm, with ⍺1 fluctuating between approx 0.75--1.0.

Graph plots:
- X axis: time (minutes) with a 2 minute viewport
- primary Y axis (0-200):
  - red trace: HR (BPM)
  - green trace: ⍺1 x 100 (e.g. 0.75 reads as 75). Yellow and red lines at 75 and 50 (HRVVT1*100 and HRVVT2*100). Grid lines at multiples of 25
- secondary Y axis (0-10):
  - blue trace: artifacts (%)

## Frequently Asked Questions ##
- *Will it work with my device?* Only the Polar H10 (and maybe H9) are supported. The research only validated the H10; it's believed that other sensors are not reliable enough. And the H7 is not a BLE device, so it's not supported by the Polar BLE SDK that FatMaxxer uses.
- *Is there any plan for an iOS app?* Not at this stage, sorry. I wrote this app for Android because that's what I currently use. However the project is more or less entirely open source. It would be great if there was an iOS developer prepared to do a port to iOS.
- *FatMaxxer crashes on my device* The app is in a very early stage of development. Please do feel free to open a new issue with as much detail as possible about the fault. I am still working on aligning with Android development best practice for several aspects, including Notifications.

## Audio / notification (wearable) updates ##
Reports ⍺1 and other features via audio and/or notifications (configurable), adjusting to work rate:

*Audio updates* report ⍺1 at HR above a hardcoded threshold, and RMSSD otherwise. Reports artifacts above a hardcoded threshold, or at higher intensity. Audio updates are more frequent at higher work rates. Audible warning (click) is played on dropped artifact.

*Notifications:* A notification update is sent whenever ⍺1 is recalculated. Notification title provides ⍺1 and artifacts dropped (%). This provides as a basic way to view output on a wearable (see photo above). Some Garmin devices show notification titles during activities (see photo above).

## Logs ##
Log files are recorded to external storage and available for export via the Androd ShareSheet.
  - rr.log as per HRV Logger format
  - artifacts.log - timestamp for artifacts*.log is corresponds to the last processed sample of the window (watch this space)
Output to "external" storage; may not work on Android versions later than 9-ish.

## Known issues / limitations ##
- _Needless to say, I will not be held responsible for any app malfunction which causes you to overtrain!_
- GraphView plotter is quirky and could be replaced
- Android may pause (kill) the app unpredictably. Enable the "Leave screen on" option, check on the app regularly, and avoid using other apps while in use.
- Audio update period should not need to be customized; it should be detectable from metrics like ⍺1---it's an objective measure of effort, after all
- UI cleanup to show battery, demote RMSSD, remove ugly status line
- features.csv does not output SDNN

## Acknowledgements and References ##
- Marco Altini's Python colab
  (https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
  FatMaxxer's ⍺1 has been developed to approximately correspond to this code. The so-called smoothness priors method used by Kubios and Runalyzer is also now
  incorporated (https://ieeexplore.ieee.org/document/979357). Detailed testing stil to come. 
- Manas Sharma's polynomial fitting implementation (https://www.bragitoff.com/2017/04/polynomial-fitting-java-codeprogram-works-android-well/)
- Polar API and example (https://github.com/polarofficial/polar-ble-sdk)
- The Efficient Java Matrix Library (http://ejml.org/)
- GraphView (https://github.com/jjoe64/GraphView)
- Bruce Rogers' blog has a wealth of information (http://www.muscleoxygentraining.com/p/index.html).
  Bruce has been extremely active with early testing and usability feedback.
