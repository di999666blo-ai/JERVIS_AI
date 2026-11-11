# BOBBY Android self-updates

BOBBY checks the latest GitHub Release for this repository, compares the Android version code, downloads `BOBBY.apk`, verifies SHA-256, and hands installation to Android's PackageInstaller.

## One-time release signing setup

Android only accepts an in-place APK update when the new APK has the same application ID and signing certificate as the installed APK. Keep the signing keystore private and backed up.

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add all four values:

- `BOBBY_KEYSTORE_BASE64`: base64 contents of the generated `bobby-release.jks` file
- `BOBBY_KEYSTORE_PASSWORD`: keystore password
- `BOBBY_KEY_ALIAS`: key alias
- `BOBBY_KEY_PASSWORD`: private-key password

The Android workflow validates that all four are present. It publishes a stable GitHub Release only when all four are configured. Partial configuration fails early; no release is published.

## First signed installation

Earlier CI APKs may have been signed with GitHub's temporary runner debug key. That key is not stable across runner machines. If the currently installed BOBBY APK was signed with a different key, Android will reject an in-place update from the new permanent key. In that case, back up/export BOBBY's local data, install the first permanently signed APK once (which may require uninstalling the old build), then use in-app updates from that baseline onward.

## Android approval

BOBBY can check, download and verify the update, but Android displays its own install confirmation. This is an Android security requirement; BOBBY does not bypass it.
