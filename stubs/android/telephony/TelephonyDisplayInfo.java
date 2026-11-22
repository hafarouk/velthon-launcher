package android.telephony;

/** Compile-time stand-in for the Android 11 class (the build compiles against API 28). Never packaged:
 *  on the phone the real class is used. */
public final class TelephonyDisplayInfo {
    public int getNetworkType() { return 0; }
    public int getOverrideNetworkType() { return 0; }
}
