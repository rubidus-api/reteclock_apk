package com.reteclock;

import android.app.Activity;

/** Activity visibility owns the optional listener, including transitions between settings pages. */
public class WebActivity extends Activity {
    @Override protected void onStart() { super.onStart(); WebAdmin.enter(this,this); }
    @Override protected void onStop() { WebAdmin.leave(this); super.onStop(); }
    void webSettingsChanged() { getWindow().getDecorView().invalidate(); }
}
