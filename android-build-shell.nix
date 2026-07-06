let
  pkgs = import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  };
  android = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "34" ];
    buildToolsVersions = [ "34.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
    includeNDK = false;
  };
in pkgs.mkShell {
  packages = [ pkgs.javaPackages.compiler.temurin-bin.jdk-17 android.androidsdk ];
  ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
  ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
  JAVA_HOME = "${pkgs.javaPackages.compiler.temurin-bin.jdk-17}";
}
