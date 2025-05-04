# <img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/Fatmaxxer_Icon_v0.2.png" height="36"/> FatMaxxer

FatMaxxer for Android may help you exercise at the best intensity for fat burning,
measured using just a Polar H10 heart rate strap, according to recent research (see below).
**This app requires a Polar H10** (or possibly H9). 

<b>NEWS: The FatMaxxer app has been delisted from the Google Play Store. Google now requires individual developers to publish their personal residential address, which I have refused to do. The github repository and APK build remain available as before.</b>
Public Release: https://play.google.com/store/apps/details?id=online.fatmaxxer.publicRelease1 .
The Google Play version may lag significantly behind the open source project hosted on Github and early test builds
(https://github.com/IanPeake/FatMaxxer#testers-wanted).

See Bruce Rogers' FAQ (http://www.muscleoxygentraining.com/2021/01/dfa-a1-and-exercise-intensity-faq.html) and review (http://www.muscleoxygentraining.com/2021/06/fatmaxxer-new-app-for-real-time-dfa-a1.html).

<img src="https://github.com/IanPeake/FatMaxxer/blob/main/screenshots/Screenshot_20210623-102602_FatMaxxer.jpg" height="240" alt="Screenshot"/> |
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/garmin_alpha1_notification.jpg" height="240" alt="Garmin notification"/> |
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshots/Screenshot_20210623-102638_FatMaxxer.jpg" height="240" alt="Garmin notification"/> |
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshots/Screenshot_20210623-102644_FatMaxxer.jpg" height="240" alt="Garmin notification"/> |
<img src="https://raw.githubusercontent.com/IanPeake/FatMaxxer/main/screenshots/Screenshot_20210623-102608_FatMaxxer.jpg" height="240" alt="Garmin notification"/> 

In action:
<img src="http://img.youtube.com/vi/Br2O0e7XoJ8/0.jpg">

## Update Aug 19 ##
New builds are now available targeting Android 13 (SDK version 33). See APK on github and in closed testing. Feedback welcome.

## Overview ##
FatMaxxer is an Android app which uses the Polar H10 to advise Detrended Fluctuation Analysis alpha1 (DFA, ⍺1) in real time.

There are promising signs that running or cycling at ⍺1 = 0.75 corresponds to the first ventilatory threshold "VT1" or roughly FatMax
(https://www.frontiersin.org/articles/10.3389/fphys.2020.596567/full).
Measuring VT1 reliably normally requires a lab test.
DFA requires only a heart rate strap that can measure inter-heartbeat intervals accurately and reliably.
FatMaxxer works just for H10 which is a consumer HR strap validated for use in several research studies.

FatMaxxer reports ⍺1 for the past two minutes in "near real time" via the GUI,
speech (speaker/headphones, configurable) and notifications (configurable).
The ⍺1 value and other features are calculated over the two minute rolling window of RR values,
with ⍺1 calculated every 20 seconds (configurable).
The RR stream is subject to artifact filtering, where adjacent RR intervals change by more than +/- threshold (%).
Threshold settings are 5%, 25% and "Auto".
The "Auto" setting uses a threshold of 5% when HR > 90 BPM and 25% when HR < 85 BPM.

FatMaxxer now provides a 10 second ECG snapshot (H10 only, TBC) around all detected artifacts when Developer mode is enabled: (http://www.muscleoxygentraining.com/2021/07/ecg-artifact-strips-from-fatmaxxer-guide.html). A test segment (segment 0) is recorded at 10s elapsed time.

## Google Play Privacy Policy ##

This section refers to FatMaxxer, packaged as a Google Play application.

FatMaxxer systematically collects and stores fitness data in the form of heart beat intervals from an attached Bluetooth Low Energy device.
FatMaxxer systematically collects and stores the IDs of nearby Polar BLE devices, including user-initiated connections.
FatMaxxer systematically collects and stores data derived from heart beat intervals and BLE devices.
Data are stored in a local private cache for approximately one week to enable the user to send crash logs, diagnostic and other performance data to third parties (such as the FatMaxxer developer) for troubleshooting or analysis or both.
Data may be shared by user request with other applications only via android data sharing APIs.
It cannot be guaranteed that shared data will be encrypted.

After one week cached data is deleted.
There is currently no way for the user to request that cached data will be deleted within the application.

## Testing ##

Sideload a recent test version from the APK downloadable from the Github repository.
Join the https://groups.google.com/g/fatmaxxer-closed-testing for access to Google Play closed test versions.
*To help me fix your issue efficiently* use the Github issue tracker above to report bugs.

## License ##
Apache 2.0 for any code that was authored by me. See also credits/acknowledgments below.

## Getting started ##
- Read and thoroughly understand how to use DFA ⍺1 (https://the5krunner.com/2021/02/25/important-training-news-dfa-alpha-1-new-threshold-discovery-method-with-hrv/) for training
- Put on your sensor
- On first launch, select your device in the menu from those discovered.
  Alternatively set preferred Device ID manually under settings, quit and restart.
  Device ID is an 8 digit hexadecimal string.
  The first device successfully connected will become your preferred device.
  On startup, app will try to connect to your preferred device.

## User Interface ##

Shows ⍺1, plus detected artifacts, number of samples and therefore artifact rate (%) over the window; elapsed time; instantaneous heart rate and heart rate variablility (RMSSD).
Android UI screenshot above shows output after walk/run intervals on an earlier version.

Graph plots:
- X axis: time (minutes) with a 2 minute viewport
- primary Y axis (0-200) configurable:
  - red: HR (BPM)
  - green: ⍺1 x 100 (e.g. 0.75 reads as 75). Yellow and red lines at 75 and 50 (HRVVT1*100 and HRVVT2*100). Grid lines at multiples of 25
  - magenta: RR intervals / 5
  - cyan: RMSSD
- secondary Y axis (0-10):
  - blue: artifacts (%)

Plots are user-configurable separately for real time and replay mode.

## Frequently Asked Questions ##

### I cannot find FatMaxxer in the Play Store in my Country ###

Release is currently limited to a specific set of countries due to lack of translations. If your country is not enabled let me know. Only English and Dutch are supported. Additional translations welcome.

### FatMaxxer hangs / crashes / does not connect to my sensor on startup ###

- Can you connect to your sensor with Polar Beat / Polar Flow as a BLE device?
- Have you enabled dual channel mode on your H10 using Polar Beat / Polar Flow?
- Is your H10 running the latest firmware (3.1.1 at the time of writing). You may have to install Polar Flow in order to update.
- Have you checked carefully that no other apps/devices are connected via BLE to the sensor? By default only one BLE connection can be made to the sensor. If you have an H10, and you enable dual channel mode in Polar Beat / Polar Flow, then a maximum of two separate BLE connections can be made to the H10, one of which must be FatMaxxer.

### FatMaxxer does not produce an ECG log file ###

- Have you enabled Developer mode (or "Enable ECG Logging" in most recent versions)?
- Are you using the H10? (It appears the H9 does not support ECG sampling.)
- Check that your H10 has recent firmware (3.1.1 recommended at time of writing). You may have to install Polar Flow in order to update.
- When working, ECG logging should always emit an ECG log if it reaches 10s elapsed time (A fake test artifact / ECG logging event is triggered at 10s.)

### FatMaxxer still hangs / crashes ###

If possible, locate the debug.log file and send it to fatmaxxer@gmail.com. Search for the folder online.fatmaxxer.alpha1 on your phone, or enable Developer Mode and Export the debug.log file.

### Will it work with my device? ###

Only the H10 (and maybe H9) are supported. The research only validated the H10. It's believed that other sensors are not reliable enough. The Polar BLE API used by FatMaxxer does not support the H7.

### (Why) does it have to be the H10/H9? ###

It doesn't *have* to be the H9/H10, but I chose to develop for those, and therefore use the Polar SDK, because they are regarded as the least-worst consumer sensors for accuracy/precision, and the HRV experimental research is based on them. I use the H10 for those reasons, and chose the Polar SDK for Android because I am an Android user. The Polar SDK doesn't support other non-Polar sensors unfortunately.

### Is there any plan for an iOS app? ###

Not at this stage, sorry. I wrote this app for Android because that's what I currently use. However the project is more or less entirely open source. It would be great if there was an iOS developer prepared to do a port to iOS.

### FatMaxxer crashes on my device ###

Please do feel free to open a new issue on https://github.com/IanPeake/FatMaxxer/ with as much detail as possible about the fault. I am still working on aligning with Android development best practice for several aspects, including Notifications.

## Audio / notification (wearable) updates ##

Reports ⍺1 and other features via audio and/or notifications (configurable), adjusting to work rate:

*Audio updates* report ⍺1 at HR above a hardcoded threshold, and RMSSD otherwise. Reports artifacts above a hardcoded threshold, or at higher intensity. Audio updates are more frequent at higher work rates. Audible warning (click) is played on dropped artifact.

*Notifications:* A notification update is sent whenever ⍺1 is recalculated. Notification title provides ⍺1 and artifacts dropped (%). This provides a basic way to view output on a wearable (see photo above). Some Garmin devices show notification titles during activities (see photo above).

## Logs ##
Log files are recorded to external storage and available for export via the Androd ShareSheet.
  - rr.log as per HRV Logger format
  - artifacts.log - timestamp for artifacts*.log is corresponds to the last processed sample of the window (watch this space)
Output to "external" storage; may not work on Android versions later than 9-ish.

## Replay ##
Enable Developer mode, Import a previously recorded RR.csv, then Replay and select the RR.csv file from your logs directory.
FatMaxxer will replay the previous session (for review / screen shot).
The graph will be slightly quantized for most plots except RRs.
Replay is at up to 60x real time.

## Known issues / limitations ##
- _Needless to say, I will not be held responsible for any app malfunction which causes you to overtrain or otherwise injure yourself_
- GraphView plotter is quirky and could be replaced
- Android may pause (kill) the app unpredictably. Enable the "Leave screen on" option, check on the app regularly, and avoid using other apps while in use.
- Audio update period should not need to be customized; it should be detectable from metrics like ⍺1---it's an objective measure of effort, after all
- UI cleanup to show battery, demote RMSSD, remove ugly status line
- features.csv does not output SDNN

## Acknowledgements and References ##
- Marco Altini's Python colab
  (https://colab.research.google.com/drive/1GUZVjZGhc2_JqV-J5m1mgbvbiTBV9WzZ?usp=sharing#scrollTo=AXWvsa6MMqSv).
  FatMaxxer's ⍺1 has been developed to approximately correspond to this code. The so-called smoothness priors method used by Kubios and Runalyzer is also now
  incorporated (https://ieeexplore.ieee.org/document/979357). Bruce Rogers has done some side by side testing to demonstrate close correspondence to Kubios. 
- Manas Sharma's polynomial fitting implementation (https://www.bragitoff.com/2017/04/polynomial-fitting-java-codeprogram-works-android-well/)
- Polar API and example (https://github.com/polarofficial/polar-ble-sdk)
- The Efficient Java Matrix Library (http://ejml.org/)
- GraphView (https://github.com/jjoe64/GraphView)
- Bruce Rogers' blog has a wealth of information (http://www.muscleoxygentraining.com/p/index.html).
  Bruce has been extremely active with early testing and usability feedback.
