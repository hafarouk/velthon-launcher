Compile-time stand-ins for newer Android classes. `build.sh` compiles them into `build/stubs` and puts that on the
class path only; they are never put in the APK, where the phone's own classes are used.
