# DEPRECATED

See below for more project information, but as of Nov 2020, Hermeez/TextTop/Android Messages/whatevery name du jour you'd like is shutdown. The backend has been dismantled, and this code is uploaded here for posterity/safekeeping. Project directory:

 - `desktop-v1` -- This contains the source code for an electron app. This version of the desktop client had basic support for multiple users, but the framework used to build the client application became unwieldly and difficult to develop, leading to:
 - `desktop-v2` -- This was a rewrite of the client application -- this time without electron -- just a React app available via the browser. In developing it, I cut some corners and skipped the necessary features that would support multiple users. This version was in use for a while, although it supported only myself.
 - `golang` -- API server for communicating with the client application
 - `TextTop` -- V1 of the Android application used to obtain/send SMS and MMS messages from the user's mobile device. Written in Java.
 - `Hermes` -- V2 of the Android application built for the purpose above. Rewritten in Kotlin. At some point in the evolution of the Android OS, however, the undocumented and strange librar(ies) used to interact with SMS and MMS messages on the phone stopped being very reliable. As of Nov 2020, this application will handle reading/writing SMS and reading MMS, but will no longer send MMS messages.

# Android Messages

It's like OSX Messages, but for Android!

## Installation Steps (5/8/17)

`Android`
  - Turn on [developer
  mode](http://www.greenbot.com/article/2457986/how-to-enable-developer-options-on-your-android-phone-or-tablet.html)/allow access to non-Play Store applications
  - Enable installation of apps from non-playstore origins (someday it'll be on the Play store)
  - Download the Hermes apk by visiting https://hermeez.co/apk/app-debug.apk on your phone
  - Login, set your password, sync messages, etc.

`OSX`
  - The current version of the `.app` file is available in Drive:
  - Request access and download from [Google
  Drive](https://drive.google.com/drive/folders/0B6b3HCSfzBaVeEM0cl9OMUJNcVE)
  - Launch the app, login with the email you OAuthed with and the password you set in the Android
  app
  
Request access to the `hermeez.slack.com` slack for updates!
