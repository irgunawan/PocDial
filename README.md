# POC DIAL

Dialer POC mandiri untuk perangkat radio Android (Hytera P50 Pro, KSUN ZL10, dll).
Keypad **benar-benar focusable**, sehingga DPAD fisik bisa memindahkan fokus — berbeda dengan
`com.android.dialer` bawaan yang `focusable="false"`.

- Package: `com.irgun.pocdialer`
- Label aplikasi: **POC DIAL**
- `minSdk 21` (Android 5.0/5.1) · `targetSdk 34`
- Telepon suara + USSD (`*888*8#`)
- Fallback `KEYCODE_F5` sebagai arah "atas" pada KSUN ZL10
- Tombol CALL di layar (tombol CALL fisik Hytera tidak bisa di-remap tanpa root)
- Tombol **ABOUT** menampilkan **irgun.web.id**

## Build

Butuh Android SDK `platforms;android-34` + `build-tools;34.0.0` dan JDK 17.

```bash
export ANDROID_HOME=/path/to/sdk
export KEYSTORE=/path/to/release.jks
export KEYPASS_FILE=/path/to/keypass.txt
./build.sh
```

APK siap pakai juga disertakan di [`apk/POC-DIAL-v1.0.apk`](apk/POC-DIAL-v1.0.apk).

## Uji di perangkat

```bash
adb install -r apk/POC-DIAL-v1.0.apk
adb shell am start -n com.irgun.pocdialer/.DialerActivity
adb shell uiautomator dump /sdcard/u.xml
adb shell cat /sdcard/u.xml | grep 'focused="true"'
adb shell am start -a android.intent.action.CALL -d 'tel:*888*8%23'
adb shell getevent -lt
```

Kriteria lulus: DPAD memindahkan fokus antar angka, ENTER / DPAD_CENTER / CALL mengirim USSD,
`#` fisik mengetik `#`, dan F5 di KSUN ZL10 memfokuskan angka `2`.

## OTA

Entri OTA untuk paket ini tersedia di `https://ota.irgun.web.id/check.php?key=com.irgun.pocdialer`.

## Catatan migrasi package

Package berubah dari `com.pocindonesia.pocdialer` menjadi `com.irgun.pocdialer`.
APK baru **bukan update** untuk instalasi lama — Android menganggapnya aplikasi
berbeda. Uninstall versi lama dulu sebelum memasang yang baru, atau akan ada dua
entri POC DIAL di launcher.

Kunci OTA ikut berubah. Pastikan endpoint `check.php` punya entri untuk
`com.irgun.pocdialer`; kalau belum, OTA tidak akan menemukan update.

Berkas `apk/POC-DIAL-v1.0.apk` dan `v1.1.apk` adalah arsip lama dan masih
memakai package `com.pocindonesia.pocdialer` — sengaja tidak dihapus.
