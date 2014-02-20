======
README
======

General information
===================

This project shows how the Android SDK can be included within a sample app. In this application we
use features execute transactions (CHARGE / REFUND). Those transactions are registered only
locally. In a real world application it is highly recommended to register them from your
backend and then look them up using the SDK.  The necessary modifications are explained in comments.

Overview
========

The ``MenuActivity`` can start two activities (one each for charge respectively refund).

Each of those activities extends the ``MposActivity`` which creates the shared provider and also
provides convenient methods for elementary UI actions. This makes the actual activity classes
more clear. Those activities will be discussed based on the ``Charge Activity``.

In the ``Charge Activity`` the transaction processing is initiated by the user by clicking on the
start button and then we register our transaction using a transaction template. In real world, you
would ask your backend to register it, provide you with the corresponding identifier and look it up.

When the registering/lookup is finished, we connect to the accessory and initiate a check whether an update is required or not
and perform it is necessary. After a succeeded update we start the transaction and handle calls of
onTransactionActionRequired(...) and onTransactionSuccess(...).

Prerequisites
=============

Please ensure that you have installed at least the following components using the Android SDK Manager:

- Android Studio 0.4 (or better)
- Android 4.2.2 (API 17) SDK Platform - Revision 2+
- Android Support Repository - Revision 2+
- Android Support Library - Revision 18+

You can start the Android SDK Manager within the AndroidStudio IDE: *Tools* > *Android* > *SDK Manager*.

How to use
==========

In order to use this example for your application simply apply the following steps on your local
machine.

0. Extract the zip archive.

1. Import this folder using AndroidStudio http://developer.android.com/sdk/installing/studio.html

	a. Start AndroidStudio
	b. Select *Import Project* and *Import project from external model* (Gradle)
	c. Select the *mPOSSampleProject* folder
	d. When asked select: *Use default gradle wrapper (recommended)*
	e. Wait patiently(!) while the IDE starts and configures the project (this can take up to 5 minutes)

2. Use the ``mPOSSample`` run configuration

3. Now the app should be built and installed on your Android phone or the emulator

4. For further usage you want to replace the already included libraries with up-to-date versions. You can examine the current used version within the menu fragment.

5. You can add the java doc from *mPOSSample/libs* via the module settings.

Limitations
===========
- During a firmware update of a card reader no processing information is shown. That doesn't mean, that the SDK is stuck.
- The app might show unexpected behaviour when confronted with screen rotations while a Dialog is shown
- The handling of ``ActionResponses`` is simplified
