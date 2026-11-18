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
      mkSdk = withEmulator: pkgs.androidenv.composeAndroidPackages ({
        platformVersions = [ "37.0" ];
        buildToolsVersions = [ buildToolsVersion ];
      } // pkgs.lib.optionalAttrs withEmulator {
        includeEmulator = true;
        includeSystemImages = true;
        # AOSP image without Google Play Services, matching what Tessera targets.
        systemImageTypes = [ "default" ];
        abiVersions = [ "x86_64" ];
      });
      mkShell = withEmulator:
        let
          android = mkSdk withEmulator;
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
        default = mkShell false;
        # nix develop .#emulator  -> adds emulator + API 37 AOSP x86_64 system image
        emulator = mkShell true;
      };
    };
}
