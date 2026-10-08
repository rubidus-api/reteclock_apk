package com.reteclock;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.reteclock.core.WebLifetime;

import java.util.List;

/**
 * Explicit account setup, the opt-in switch, the port, and what the server is really doing.
 *
 * <p>The addresses come first, one to a row with a Copy button, IPv4 before IPv6: they are what
 * somebody opens this page for, and they have to be carried to another device (issue #69). They were
 * a block of text at the foot that could not be copied — it was selectable, but the page rewrote it
 * every two seconds and the selection went with it. Text is now set only when it has changed.
 *
 * <p>Under the addresses, what a person has to know to reach one (owner, 2026-10-08): that it is
 * {@code http://} and a current browser will try {@code https://}; when the server answers, which
 * is theirs to narrow to this page alone (R140); and that the address changes unless it is fixed.
 *
 * <p>The page keeps the screen on. The server lives only while one of the app's screens is showing
 * (RFC-0016), and this one, unlike the clock, used to let the phone fall asleep: half a minute
 * after the address had been read off it, the browser on the other device was refused.
 */
public final class WebSettingsActivity extends WebActivity {

    private static final int AMBER = 0xffffb300;
    private static final int TEAL = 0xff4db6ac;
    private static final int GREY = 0xffbdbdbd;
    /** When the server runs: the stored names, in the order the page offers them (R140, R141). */
    private static final String[] RULES = {WebLifetime.SCREEN, WebLifetime.PAGE, WebLifetime.ALWAYS};
    private static final int[] RULE_NAMES = {R.string.web_run_screen, R.string.web_run_page,
        R.string.web_run_always};

    private final Handler handler = new Handler();
    private LinearLayout addresses;
    private TextView status, lifetime;
    private EditText user, password, port;
    private CheckBox enabled;
    private RadioGroup run;
    private TextView sameDevice;
    private Button save;
    /** What the address rows and the status were last built from, so they are rebuilt only on a change. */
    private String shownAddresses = null, shownStatus = null, shownLifetime = null;

    private final Runnable update = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // As the other settings pages: no system title above the page's own, and no keyboard
        // thrown up over the page before anybody has touched a field.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(20, 20, 20, 20);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(body);
        // The page opens at its top: selectable text can take focus, and a ScrollView scrolls to
        // whatever holds it — the title was off the screen once there was more to select.
        scroll.setDescendantFocusability(ScrollView.FOCUS_BEFORE_DESCENDANTS);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(R.string.web_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        body.addView(title);
        body.addView(note(R.string.web_warning, AMBER));

        body.addView(heading(R.string.web_open_these));
        addresses = new LinearLayout(this);
        addresses.setOrientation(LinearLayout.VERTICAL);
        body.addView(addresses);
        body.addView(note(R.string.web_http_only, AMBER));
        lifetime = note(R.string.web_lifetime_screen, GREY);
        lifetime.setPadding(0, 12, 0, 0);
        body.addView(lifetime);
        sameDevice = note(R.string.web_same_device, GREY);
        body.addView(sameDevice);
        TextView fixed = note(R.string.web_fixed_ip, GREY);
        fixed.setPadding(0, 12, 0, 0);
        body.addView(fixed);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(0, 12, 0, 12);
        if (Build.VERSION.SDK_INT >= 11) {
            status.setTextIsSelectable(true);
        }
        body.addView(status);

        body.addView(heading(R.string.web_setup));
        enabled = new CheckBox(this);
        enabled.setText(R.string.web_enabled);
        enabled.setChecked(WebAdmin.enabled(this));
        body.addView(enabled);
        TextView when = new TextView(this);
        when.setText(R.string.web_run);
        when.setTextColor(Color.WHITE);
        when.setPadding(0, 12, 0, 0);
        body.addView(when);
        run = new RadioGroup(this);
        String stored = WebAdmin.lifetime(this);
        for (int i = 0; i < RULES.length; i++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(i + 1);
            choice.setText(RULE_NAMES[i]);
            run.addView(choice);
            if (RULES[i].equals(stored)) {
                run.check(i + 1);
            }
        }
        body.addView(run);
        user = field(body, R.string.web_user, InputType.TYPE_CLASS_TEXT);
        user.setText(WebAdmin.account(this).getString("user", ""));
        password = field(body, R.string.web_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        port = field(body, R.string.web_port, InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(WebAdmin.port(this)));

        save = new Button(this);
        save.setText(R.string.web_save);
        body.addView(save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    save.setEnabled(false);
                    WebAdmin.configure(WebSettingsActivity.this, enabled.isChecked(),
                            RULES[Math.max(1, run.getCheckedRadioButtonId()) - 1],
                            Integer.parseInt(port.getText().toString()),
                            user.getText().toString(), password.getText().toString(),
                            new Runnable() {
                                @Override
                                public void run() {
                                    save.setEnabled(true);
                                    password.setText("");
                                    refresh();
                                }
                            });
                } catch (NumberFormatException bad) {
                    save.setEnabled(true);
                    toast(getString(R.string.web_invalid));
                } catch (RuntimeException bad) {
                    save.setEnabled(true);
                    toast(bad.getMessage() == null ? getString(R.string.web_invalid)
                            : bad.getMessage());
                }
            }
        });
        Button stop = new Button(this);
        stop.setText(R.string.web_stop);
        body.addView(stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebAdmin.account(WebSettingsActivity.this).edit().putBoolean("enabled", false)
                        .commit();
                enabled.setChecked(false);
                WebAdmin.reconcile();
                refresh();
            }
        });
    }

    /** The address rows and the status line, rebuilt only when what they say has changed. */
    private void refresh() {
        if (addresses == null) {
            return;
        }
        List<String[]> urls = WebAdmin.urls();
        StringBuilder key = new StringBuilder();
        for (String[] url : urls) {
            key.append(url[0]).append(url[1]).append('\n');
        }
        if (!key.toString().equals(shownAddresses)) {
            shownAddresses = key.toString();
            addresses.removeAllViews();
            if (urls.isEmpty()) {
                addresses.addView(note(R.string.web_no_address, Color.WHITE));
            }
            for (String[] url : urls) {
                addresses.addView(addressRow(url[0], url[1]));
            }
        }
        // Which rule is in force is what is stored, not what the box shows before Save.
        String rule = WebAdmin.lifetime(this);
        if (!rule.equals(shownLifetime)) {
            shownLifetime = rule;
            lifetime.setText(WebLifetime.PAGE.equals(rule) ? R.string.web_lifetime_page
                    : WebLifetime.ALWAYS.equals(rule) ? R.string.web_lifetime_always
                    : R.string.web_lifetime_screen);
            // In the background a browser on this device needs no split screen.
            sameDevice.setVisibility(WebLifetime.ALWAYS.equals(rule) ? View.GONE : View.VISIBLE);
        }
        // The server's own state — but not the addresses over again when it is simply listening.
        String state = WebAdmin.stateText();
        String said = (state.startsWith("http") ? getString(R.string.web_listening) : state)
                + "\n" + WebAdmin.contactText(this);
        if (!said.equals(shownStatus)) {
            shownStatus = said;
            status.setText(said);
        }
    }

    /** One address: which kind, the address itself — selectable — and a button that copies it. */
    private View addressRow(String kind, final String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(kind);
        label.setTextColor(TEAL);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        text.addView(label);
        TextView address = new TextView(this);
        address.setText(url);
        address.setTextColor(Color.WHITE);
        address.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        if (Build.VERSION.SDK_INT >= 11) {
            address.setTextIsSelectable(true);
        }
        text.addView(address);
        row.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = new Button(this);
        copy.setText(R.string.web_copy);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast(copy(url) ? getString(R.string.web_copied, url)
                        : getString(R.string.web_copy_failed));
            }
        });
        row.addView(copy);
        return row;
    }

    @SuppressWarnings("deprecation")
    private boolean copy(String text) {
        if (Build.VERSION.SDK_INT >= 11) {
            return ClipApi11.copy(this, text);
        }
        try {
            // The clipboard of Android 2.3: text only, and all there was.
            android.text.ClipboardManager clipboard =
                    (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setText(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private TextView heading(int text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEAL);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setPadding(0, 24, 0, 6);
        return view;
    }

    private TextView note(int text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        return view;
    }

    private EditText field(LinearLayout body, int label, int type) {
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        body.addView(text);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(type);
        body.addView(edit);
        return edit;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(update);
        handler.post(update);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(update);
        super.onPause();
    }
}
