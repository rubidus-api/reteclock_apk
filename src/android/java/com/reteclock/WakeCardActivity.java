package com.reteclock;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.reteclock.core.Bell;

/**
 * The card for a bell that woke the phone, shown over the lock screen (RFC-0012).
 *
 * <p>The same card the clock shows — {@link BellCard}, the same two answers and the same wording,
 * Back meaning Stop — so a bell looks the same on screen and off it. It answers to
 * {@link WakeRingService}, which owns the ring; this window is only where the answer is given, and
 * it goes when the ring does. Shipped disabled.
 */
public class WakeCardActivity extends Activity {

    private static WakeCardActivity showing;
    private Dialog card;

    /** Closes the card if one is up — the ring has ended some other way. */
    static void close() {
        if (showing != null) {
            showing.finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            WakeApi27.showOverLockScreen(this);
        }
        View ground = new View(this);
        ground.setBackgroundColor(Color.BLACK);
        setContentView(ground);
        showing = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bell bell = WakeRingService.ringingBell();
        if (bell == null) {
            finish();
            return;
        }
        if (card != null && card.isShowing()) {
            return;
        }
        card = BellCard.show(this, bell, new BellCard.Answer() {
            @Override
            public void putOff() {
                send(WakeRingService.ACTION_PUT_OFF);
            }

            @Override
            public void stop() {
                send(WakeRingService.ACTION_STOP);
            }

            @Override
            public void unanswered() {
                send(WakeRingService.ACTION_UNANSWERED);
            }
        });
        card.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                finish();
            }
        });
    }

    private void send(String action) {
        Intent intent = new Intent(this, WakeRingService.class);
        intent.setAction(action);
        try {
            startService(intent);
        } catch (RuntimeException ignored) {
            // The service has already gone; there is nothing left to answer.
        }
    }

    @Override
    protected void onDestroy() {
        if (card != null && card.isShowing()) {
            card.dismiss();
        }
        if (showing == this) {
            showing = null;
        }
        super.onDestroy();
    }
}
