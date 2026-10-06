package com.reteclock;

import android.os.*;
import android.graphics.Color;
import android.text.InputType;
import android.widget.*;
import android.view.View;

/** Explicit account setup, opt-in switch, port and actual local listening state. */
public final class WebSettingsActivity extends WebActivity {
    private final Handler handler=new Handler();
    private TextView status;private EditText user,password,port;private CheckBox enabled;private Button save;
    private final Runnable update=new Runnable(){public void run(){if(status!=null)status.setText(getString(R.string.web_addresses)+"\n"+WebAdmin.localAddresses()+"\n\n"+WebAdmin.stateText());handler.postDelayed(this,2000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        // As the other settings pages: no system title above the page's own, and no keyboard
        // thrown up over the page before anybody has touched a field.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(20,20,20,20);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.BLACK);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText(R.string.web_title);title.setTextColor(Color.WHITE);title.setTextSize(24);body.addView(title);
        TextView warning=new TextView(this);warning.setText(R.string.web_warning);warning.setTextColor(0xffffb300);body.addView(warning);
        enabled=new CheckBox(this);enabled.setText(R.string.web_enabled);enabled.setChecked(WebAdmin.enabled(this));body.addView(enabled);
        user=field(body,R.string.web_user,InputType.TYPE_CLASS_TEXT);user.setText(WebAdmin.account(this).getString("user",""));
        password=field(body,R.string.web_password,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        port=field(body,R.string.web_port,InputType.TYPE_CLASS_NUMBER);port.setText(String.valueOf(WebAdmin.port(this)));
        save=new Button(this);save.setText(R.string.web_save);body.addView(save);
        save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            try{save.setEnabled(false);WebAdmin.configure(WebSettingsActivity.this,enabled.isChecked(),Integer.parseInt(port.getText().toString()),user.getText().toString(),password.getText().toString(),
                new Runnable(){public void run(){save.setEnabled(true);password.setText("");status.setText(WebAdmin.stateText());}});
            }catch(RuntimeException bad){save.setEnabled(true);Toast.makeText(WebSettingsActivity.this,bad.getMessage()==null?getString(R.string.web_invalid):bad.getMessage(),Toast.LENGTH_LONG).show();}
        }});
        Button stop=new Button(this);stop.setText(R.string.web_stop);body.addView(stop);stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){WebAdmin.account(WebSettingsActivity.this).edit().putBoolean("enabled",false).commit();enabled.setChecked(false);WebAdmin.reconcile();}});
        status=new TextView(this);status.setTextColor(Color.WHITE);if(Build.VERSION.SDK_INT>=11)status.setTextIsSelectable(true);body.addView(status);
    }
    private EditText field(LinearLayout body,int label,int type){TextView text=new TextView(this);text.setText(label);text.setTextColor(Color.WHITE);body.addView(text);EditText edit=new EditText(this);edit.setSingleLine(true);edit.setInputType(type);body.addView(edit);return edit;}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(update);handler.post(update);}
    @Override protected void onPause(){handler.removeCallbacks(update);super.onPause();}
}
