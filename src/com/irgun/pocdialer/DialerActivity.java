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
        new CrashReporter(this, "POC DIAL", "1.4").install();
        setContentView(R.layout.activity_dialer);
        digits = (EditText) findViewById(R.id.digits);
        /* Keypad angka sudah ada di layar; jangan biarkan IME menutupi layar
           radio 128x160 (PNC360S). */
        try { digits.setShowSoftInputOnFocus(false); } catch (Throwable ignored) {}

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
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            Intent i = new Intent("android.telecom.action.CHANGE_DEFAULT_DIALER");
            i.putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Set PocDialer sebagai dialer default di Settings", Toast.LENGTH_LONG).show();
        }
    }

    private void placeCall() {
        String number = digits.getText().toString().trim();
        if (number.length() == 0) return;
        Uri uri = Uri.parse("tel:" + Uri.encode(number));

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            Toast.makeText(this, "Izinkan akses telepon, lalu tekan CALL lagi", Toast.LENGTH_LONG).show();
            return;
        }

        boolean direct = Build.VERSION.SDK_INT < 30 || isDefaultDialer();
        Intent call;
        if (direct) {
            call = new Intent(Intent.ACTION_CALL, uri);
        } else {
            call = new Intent(Intent.ACTION_DIAL, uri);
            requestDefaultDialerRole();
        }
        try {
            startActivity(call);
        } catch (Throwable t) {
            Toast.makeText(this, "Tidak dapat membuka dialer: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
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
            /* DPAD tengah / ENTER sengaja tidak diambil: biarkan tombol yang
               sedang fokus yang bereaksi (ketik angka, bukan langsung memanggil). */
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
