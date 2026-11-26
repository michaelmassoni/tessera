{
  description = "Tessera: optional Nix dev shell (JDK + Android SDK). Not required for building; CI uses standard GitHub runners.";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = { android_sdk.accept_license = true; allowUnfree = true; };
      };
      buildToolsVersion = "37.0.0";
      # imageApi: null = no emulator; otherwise the API level of the AOSP (no Google APIs) image.
      mkSdk = imageApi: pkgs.androidenv.composeAndroidPackages ({
        # 37.1: compileSdk (Compose 1.13 needs it); 37.0: the emulator image and targetSdk.
        platformVersions = [ "37.0" "37.1" ] ++ pkgs.lib.optional (imageApi != null && imageApi != "37.0") imageApi;
        buildToolsVersions = [ buildToolsVersion ];
      } // pkgs.lib.optionalAttrs (imageApi != null) {
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "default" ];
        abiVersions = [ "x86_64" ];
      });
      mkShell = imageApi:
        let
          android = mkSdk imageApi;
          sdk = "${android.androidsdk}/libexec/android-sdk";
        in pkgs.mkShell {
          packages = [ pkgs.jdk21 pkgs.android-tools android.androidsdk ];
          ANDROID_HOME = sdk;
          ANDROID_SDK_ROOT = sdk;
          JAVA_HOME = "${pkgs.jdk21}";
          # AGP downloads a dynamically linked aapt2 from Maven that cannot run on NixOS; use the SDK's.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";
          shellHook = ''
            export ANDROID_USER_HOME="$PWD/.android"
            export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
          '';
        };
    in {
      devShells.${system} = {
        default = mkShell null;
        # nix develop .#emulator        -> emulator + API 37 AOSP x86_64 image (target SDK)
        emulator = mkShell "37.0";
        # nix develop .#emulator-minsdk -> emulator + API 31 AOSP x86_64 image (min SDK, Android 12)
        emulator-minsdk = mkShell "31";
      };
    };
}
