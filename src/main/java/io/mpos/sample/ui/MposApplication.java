package io.mpos.sample.ui;

import android.app.Application;
import android.os.Build;

import io.mpos.transactions.Currency;

public class MposApplication extends Application {

    /**
     * Default currency used in the sample app.
     */
    public final static Currency DEFAULT_CURRENCY = Currency.EUR;


    /**
     * Checks if the app runs on the Emulator or on a real device.
     * @return
     */
    public static boolean isRunningOnEmulator() {
        return Build.FINGERPRINT.contains("generic");
    }
}
