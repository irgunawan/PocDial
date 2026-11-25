package com.irgun.pocdialer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.Editable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class DialerActivity extends Activity {

    private static final int REQ_CALL = 1;
    private EditText digits;

    private static final int[] KEY_IDS = {
            R.id.key1, R.id.key2, R.id.key3,
            R.id.key4, R.id.key5, R.id.key6,
            R.id.key7, R.id.key8, R.id.key9,
            R.id.keyStar, R.id.key0, R.id.keyPound
    };

    /* Layar radio POC 128x160 px (~170x213dp): resources -small sudah mengecilkan
       @dimen, ini jaring pengaman kalau perangkat mengklasifikasi dirinya lain. */
    private boolean tinyScreen() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return (dm.widthPixels / dm.density) < 220f || (dm.heightPixels / dm.density) < 300f;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private float sp(float v) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    /* Tinggi baris dikunci bobot (weightSum=6) sehingga selalu muat; yang perlu
       disesuaikan saat runtime hanya ukuran teks kalau perangkat melaporkan
       klasifikasi layar yang tidak memicu values-small. */
    private void applyTinyText() {
        final float kt = sp(13f), dt = sp(14f), at = sp(10f);
        for (int id : KEY_IDS) {
            Button b = (Button) findViewById(id);
            if (b == null) continue;
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, kt);
        }
        digits.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dt);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new CrashReporter(this, "POC DIAL", "1.5").install();
        setContentView(R.layout.activity_dialer);
        digits = (EditText) findViewById(R.id.digits);
        /* Keypad angka sudah ada di layar; jangan biarkan IME menutupi layar
           radio 128x160 (PNC360S). */
        try { digits.setShowSoftInputOnFocus(false); } catch (Throwable ignored) {}

        /* Minta izin CALL_PHONE lebih awal: di radio tombol CALL bisa ditekan
           sebelum sempat ada dialog izin, sehingga panggilan USSD gagal diam-diam. */
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
        }

        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            String number = intent.getData().getSchemeSpecificPart();
            if (number != null && number.length() > 0) digits.setText(number);
        }

        View.OnClickListener keyClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                digits.append(((Button) v).getText());
            }
        };
        for (int id : KEY_IDS) findViewById(id).setOnClickListener(keyClick);
        /* Tombol telepon bulat. Tekan-tahan = info aplikasi (dulu tombol i/BATAL). */
        View callBtn = findViewById(R.id.callButton);
        callBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                placeCall();
            }
        });
        callBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(DialerActivity.this)
                        .setTitle("POC DIAL")
                        .setMessage(R.string.about_text)
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            }
        });

        if (tinyScreen()) applyTinyText();

        if (Build.VERSION.SDK_INT >= 30 && !isDefaultDialer()) {
            Toast.makeText(this, "Jadikan PocDialer dialer utama untuk USSD langsung", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isDefaultDialer() {
        if (Build.VERSION.SDK_INT < 30) return true;
        try {
            Class<?> cls = Class.forName("android.telecom.TelecomManager");
            Object tm = getSystemService(TELEPHONY_SERVICE);
            String def = (String) cls.getMethod("getDefaultDialerPackage").invoke(tm);
            return getPackageName().equals(def);
        } catch (Throwable t) {
            return true;
        }
    }

    private void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            /* RoleManager dipakai lewat reflection supaya kelas API 29 tidak
               memaksa verifikasi saat aplikasi berjalan di Android 5/7. */
            Class<?> rmClass = Class.forName("android.app.role.RoleManager");
            Object rm = getSystemService("role");
            String role = (String) rmClass.getField("ROLE_DIALER").get(null);
            Boolean held = (Boolean) rmClass.getMethod("isRoleHeld", String.class).invoke(rm, role);
            if (rm != null && !Boolean.TRUE.equals(held)) {
                Intent i = (Intent) rmClass.getMethod("createRequestRoleIntent", String.class)
                        .invoke(rm, role);
                if (i != null) { startActivity(i); return; }
            }
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent("android.telecom.action.CHANGE_DEFAULT_DIALER");
            i.putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Set POC DIAL sebagai dialer default di Settings", Toast.LENGTH_LONG).show();
        }
    }

    private void placeCall() {
        String number = digits.getText().toString().trim();
        if (number.length() == 0) return;
        /* '#' harus dikodekan supaya dialer mengirimnya sebagai akhir USSD,
           bukan sebagai post-dial pause. Uri.encode menjaga '*' apa adanya. */
        Uri uri = Uri.parse("tel:" + Uri.encode(number));

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            Toast.makeText(this, "Izinkan akses telepon, lalu tekan CALL lagi", Toast.LENGTH_LONG).show();
            return;
        }

        /* ACTION_CALL sudah cukup di API < 30 dan merupakan satu-satunya jalur
           yang meneruskan USSD ke jaringan. Jangan turun ke ACTION_DIAL di sini:
           dialer ODM (com.onego.dialer) menolak USSD. */
        boolean callDirect = Build.VERSION.SDK_INT < 30 || isDefaultDialer();
        try {
            Intent call = new Intent(callDirect ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri);
            startActivity(call);
            return;
        } catch (Throwable first) {
            if (Build.VERSION.SDK_INT < 30) {
                Toast.makeText(this, "Gagal memanggil: " + first.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }
        requestDefaultDialerRole();
        Toast.makeText(this, "Jadikan POC DIAL dialer default, lalu tekan CALL lagi", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALL && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            placeCall();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            /* DPAD tengah / ENTER hanya dipakai kalau fokus sedang di tombol CALL.
               Di tombol angka, keduanya tetap jadi "ketik angka yang difokuskan". */
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (findViewById(R.id.callButton).isFocused()) {
                    placeCall();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_CALL:
                placeCall();
                return true;
            case KeyEvent.KEYCODE_DEL:
                Editable ed = digits.getText();
                if (ed.length() > 0) ed.delete(ed.length() - 1, ed.length());
                return true;
            case KeyEvent.KEYCODE_CLEAR:
                digits.setText("");
                return true;
            case KeyEvent.KEYCODE_STAR:
                digits.append("*");
                return true;
            case KeyEvent.KEYCODE_POUND:
                digits.append("#");
                return true;
            case KeyEvent.KEYCODE_F5:
                findViewById(R.id.key5).requestFocus();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
