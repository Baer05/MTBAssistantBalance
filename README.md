# MtbAssistant project repository
Android application that can connect Xsens dots and the feelSpace naviBelt via Bluetooth and 
receive packets from an ESP32 microcontroller via a UDPListener. A CSV file is created with this 
received data and the data generated by the Xsens Dot. The received UDP data is evaluated and if 
the values are too high, feedback is given using the nviBelt and a graphic view of the data is 
displayed after the end of the recording.


## Xsens Android instructions
1. [Download](https://content.xsens.com/xsens-dot-software-development-kit?hsCtaTracking=2af14a41-b15f-4733-b2ca-5498b2888842%7C21941862-cb62-421e-9e0e-2dac04d1ca9f) the aar library and copy it into MtbAssistant/app/libs.
2. Tell gradle to include the aar library and where to find it (i.e. its build.gradle in MtbAssistant ).If the following lines are not included, then add:
```gradle

  // If do not add this, when use our SDK, it will show ERROR: Failed to resolve: :XsensDotSdk:
        flatDir {
            dirs "libs"
        }

```
3. Sync gradle and make sure build.gradle for ...the aar appears in the External Libraries section
