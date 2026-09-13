package com.reteclock;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

import com.reteclock.core.LaunchRoute;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.Tones;

/**
 * Full-screen clock activity.
 *
 * Launched from the home screen, from a desk dock, or by {@link PowerConnectionReceiver} when the
 * device starts charging. It keeps the screen on for as long as it is visible.
 *
 * A long press opens the settings screen. Since nothing on screen says so, the clock says it once
 * on launch — but only when the user opened it themselves, and only until they have been there.
 */
public class ClockActivity extends Activity {

    /** Set by {@link PowerConnectionReceiver} so the clock knows it may show over the lock screen. */
    public static final String EXTRA_DOCK = "com.reteclock.DOCK";

    private ClockView view;
    /** Which slide is in force, asked once a second (issue #53). */
    private SlideWatch slideWatch;
    /** The bells, riding the clock's own tick. */
    private BellRinger bells;
    /** The player the timer's own cues use; the bells have one of their own. */
    private final SoundPlayer cuePlayer = new SoundPlayer();
    /** The timer's strip beside the clock, or null when the timer is switched off. */
    private TimerView timer;
    /** Everything on screen: the clock, the strip, and the sheet the flash uses. */
    private FrameLayout root;
    private View flash;
    private TimerSounds sounds;
    private TimerVoice voice;
    /** This run leaves the imported images and fonts alone; the run before it never came back. */
    private boolean safeMode;
    private final android.os.Handler handler = new android.os.Handler();

    /**
     * Clears the mark this run left, once it has lasted long enough to be worth trusting.
     *
     * Late enough that the pictures have been decoded and drawn many times over: whatever is going
     * to hang the clock has had its chance by then, and a run that gets here really was healthy.
     */
    private final Runnable reportHealthy = new Runnable() {
        @Override
        public void run() {
            Settings.setRunUnfinished(ClockActivity.this, false);
        }
    };

    /**
     * Sends a home-screen tap to the settings when that is where it belongs.
     *
     * The button opens the clock (issue #39), which is what the app did before an unusable picture
     * made a door necessary. Two things still open the door instead: the user asking for it, and a
     * run that never came back — the case the door was built for, which the mark now detects rather
     * than the launcher guarding against it. Anything that is not a person tapping the icon — the
     * dock, the charger, the screensaver — goes to the clock whatever else is true.
     */
    private boolean routeToSettings() {
        Intent intent = getIntent();
        boolean fromHomeScreen = intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        int route = LaunchRoute.of(fromHomeScreen, Settings.directStart(this),
                Settings.runUnfinished(this));
        if (route != LaunchRoute.SETTINGS) {
            return false;
        }
        startActivity(new Intent(this, SettingsActivity.class));
        finish();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (routeToSettings()) {
            return;
        }

        int flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        // Asked to stay up: the same flags the charger already brings, but every time. The screen
        // never sleeps, the keyguard never covers the clock, and nothing takes it away — which is
        // what a clock on a wall, running a timer, is for.
        if (Settings.stayUnlocked(this)
                || (getIntent() != null && getIntent().getBooleanExtra(EXTRA_DOCK, false))) {
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        }
        getWindow().addFlags(flags);

        // Read the mark before this run writes its own: it belongs to the run before this one.
        // A trial of a picture-quality step that never came back is read here, once: the step is
        // refused and the mark cleared, so the next run is not asked to walk into it again. This is
        // the far half of RFC-0011 — the near half is that the step was never written, so this run
        // is already drawing at the step that last worked.
        Settings.readUnfinishedTrial(this);
        // A run that did not survive at all also drops the quality one step, for the same reason a
        // safe start leaves the pictures alone: repeating what killed the last run is not a plan.
        if (Settings.runUnfinished(this)) {
            int at = Settings.imageQuality(this);
            if (at != com.reteclock.core.ImageQuality.FLOOR) {
                Settings.refuseImageStep(this, at);
            }
        }
        safeMode = com.reteclock.core.SafeStart.safeMode(Settings.runUnfinished(this));
        if (safeMode) {
            Settings.setSafeNotice(this, true);
        }

        view = new ClockView(this, safeMode);
        // A bell is not part of the clock's drawing, but it happens on the clock's second.
        bells = new BellRinger(this);
        slideWatch = new SlideWatch(this);
        // A timer counting has the right of way over a bell — see BellRinger.Busy.
        bells.setBusy(new BellRinger.Busy() {
            @Override
            public boolean timerIsRunning() {
                return timer != null && timer.isRunning();
            }
        });
        // A bell that can be put off asks a question, and only this screen can be asked it: the
        // screensaver is not handed its touches, so there it rings and a touch stops it as before.
        bells.setRinging(new BellRinger.Ringing() {
            @Override
            public void bellIsRinging(com.reteclock.core.Bell bell) {
                showBellCard(bell);
            }
        });
        view.setOnSecond(new ClockView.OnSecond() {
            @Override
            public void second(long nowMs) {
                bells.tick(nowMs);
                // A slide ending is a different layout, pictures and strip (issue #53).
                if (slideWatch.changed(nowMs)) {
                    view.reloadOptions();
                    layOutScreen();
                }
            }
        });
        // A tap dims the screen and the next one gives it back (issue #41); the menu is behind a
        // long press. The tap used to open the menu, which cost the clock the one gesture a bedside
        // clock actually wants and, on a phone driven by gestures rather than buttons, put a dialog
        // in the way of every attempt to swipe the app away.
        // Clickable, but deliberately *not* focusable: this is the whole screen. A focus ring
        // around the clock face would be a teal rectangle on somebody's bedside table, and the
        // keys it would catch are already answered by onKeyDown. See KeyRoute and Focusable.
        view.setClickable(true);
        // The calendar's arrows are part of the clock's own face, so a touch is offered to them
        // before it is taken as "open the menu". They exist only while the calendar does.
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                int action = event.getAction();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    // A ringing bell takes the touch before anything else does, and the touch is
                    // then spent: somebody reaching to stop a sound is not asking for a menu.
                    // Taken now, and the rest of the gesture with it: consuming only the press
                    // leaves the release to the view, which counts it as a tap and opens the menu
                    // on top of whatever the arrow just did.
                    ownGesture = silenceWhatIsSounding()
                            || view.pageCalendar(event.getX(), event.getY())
                            || view.nextSaying(event.getX(), event.getY());
                    return ownGesture;
                }
                if (!ownGesture) {
                    return false;
                }
                if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) {
                    ownGesture = false;
                }
                return true;
            }
        });
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDim();
            }
        });
        view.setLongClickable(true);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClockMenu.show(ClockActivity.this);
                return true;
            }
        });

        root = new FrameLayout(this);
        setContentView(root);
        layOutScreen();
        hideSystemBars();
        maybeHintAtSettings();
    }

    /**
     * Puts the clock on screen, with the timer's strip beside it when the timer is on: down the
     * left in landscape, across the top in portrait.
     *
     * Called again when the screen turns, because this activity handles its own configuration
     * changes and the strip has to change sides.
     */
    private void layOutScreen() {
        // The strip is built afresh each time; the one being dropped must stop ticking, or it goes
        // on sounding its own copy of the run in the background.
        if (timer != null) {
            timer.retire();
        }
        root.removeAllViews();
        if (view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        }

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean wantTimer = !safeMode && Settings.timerOn(this)
                && !Settings.timerPresets(this).isEmpty();

        // A television that overscans eats a border, and the saying and the date line live in it.
        // Zero unless the option is on: see SafeArea, and RFC-0009 Q2.
        int safe = com.reteclock.core.SafeArea.marginPx(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels,
                Settings.safeArea(this));

        if (!wantTimer) {
            timer = null;
            view.setContentInset(safe, safe, safe, safe);
            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            timer = new TimerView(this);
            timer.setListener(timerListener);
            List<TimerPreset> presets = Settings.timerPresets(this);
            TimerPreset chosen = presets.get(Settings.timerChosen(this));
            timer.setPreset(chosen);
            // A timer started before the screen was left, or turned, carries on from where it is.
            timer.adopt(com.reteclock.core.TimerMemory.restore(chosen,
                    Settings.runPreset(this), Settings.runOrigin(this),
                    Settings.runPausedAt(this), android.os.SystemClock.elapsedRealtime()),
                    Settings.runStarted(this));
            // The L on the strip, and the bar one control shorter to make room for it.
            timer.setLogKept(Settings.timerLogKept(this));

            // Hiding empties the strip; it does not take it away. The clock keeps the shape and
            // the size it had, so nothing on it moves or reflows, and the hourglass stays on the
            // very pixel it was on. Only switching the timer off entirely gives the space back,
            // and that is the branch above.
            timer.setHidden(Settings.timerHidden(this));
            // The strip's controls are drawn in the clock's own text colour, so the two belong to
            // the same clock rather than looking like a panel bolted on.
            int chosenText = Settings.color(this, Settings.KEY_TEXT_COLOR);
            int chosenBackground = com.reteclock.core.ClockColors.opaque(
                    Settings.color(this, Settings.KEY_BACKGROUND_COLOR));
            timer.setChrome(
                    com.reteclock.core.ClockColors.resolveText(chosenText, chosenBackground));

            // The clock is laid out at full size *under* the strip. Its background — a colour, or
            // a picture, or a GIF playing — therefore runs the whole width of the screen and the
            // strip sits in it rather than cutting it in two.
            //
            // Whether the *text* keeps clear of the strip depends on who arranged it, and that is
            // the whole of issue #52. See TimerRoom: the app's own arrangement is moved over,
            // because the app placed those digits and can place them elsewhere; a layout somebody
            // drew is not, because they placed every box against the whole screen in the editor
            // and the editor never showed the timer taking a bite out of it.
            int strip = stripThickness(landscape);
            int edge = landscape ? com.reteclock.core.layout.Strips.LEFT
                    : com.reteclock.core.layout.Strips.TOP;
            java.util.List<com.reteclock.core.layout.LayoutBox> drawnBoxes = drawnBoxes(landscape);
            android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
            com.reteclock.core.layout.TimerRoom room = com.reteclock.core.layout.TimerRoom.of(
                    drawnBoxes != null, drawnBoxes,
                    screen.widthPixels, screen.heightPixels, edge, strip, true);
            boolean overlaid = room.insetLeft == 0 && room.insetTop == 0
                    && room.insetRight == 0 && room.insetBottom == 0;

            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            view.setContentInset(safe + room.insetLeft, safe + room.insetTop,
                    safe + room.insetRight, safe + room.insetBottom);

            FrameLayout.LayoutParams band;
            if (overlaid) {
                // Placed at the rectangle the layout's own arithmetic produced, in the screen's own
                // coordinates. No television margin is added: the layout was drawn against the
                // whole screen, and moving the strip in from an edge the boxes were placed against
                // would put it over one of them.
                band = new FrameLayout.LayoutParams(
                        Math.round(room.strip[2]), Math.round(room.strip[3]));
                band.gravity = Gravity.LEFT | Gravity.TOP;
                band.leftMargin = Math.round(room.strip[0]);
                band.topMargin = Math.round(room.strip[1]);
                // A hidden strip over somebody's layout takes nothing at all — not the pixels and
                // not the touches. Left as it is, it would swallow taps on whatever it lies over
                // and open the preset list from a part of the screen that shows a clock.
                if (Settings.timerHidden(this)) {
                    timer.setVisibility(View.GONE);
                }
            } else {
                boolean vertical = edge == com.reteclock.core.layout.Strips.LEFT
                        || edge == com.reteclock.core.layout.Strips.RIGHT;
                band = vertical
                        ? new FrameLayout.LayoutParams(strip, FrameLayout.LayoutParams.MATCH_PARENT)
                        : new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, strip);
                band.gravity = (edge == com.reteclock.core.layout.Strips.RIGHT
                                ? Gravity.RIGHT : Gravity.LEFT)
                        | (edge == com.reteclock.core.layout.Strips.BOTTOM
                                ? Gravity.BOTTOM : Gravity.TOP);
                // The strip moves in with everything else: it is at an edge, which is the part of
                // the screen a television is eating.
                band.setMargins(safe, safe, safe, safe);
            }
            root.addView(timer, band);
        }

        // The sheet the flash uses, above everything and invisible until it is wanted.
        flash = new View(this);
        flash.setBackgroundColor(Color.WHITE);
        flash.setVisibility(View.INVISIBLE);
        root.addView(flash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** The setting can be changed while the clock is up, so the flags follow it on every return. */
    private void applyStayUnlocked() {
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        if (Settings.stayUnlocked(this)) {
            getWindow().addFlags(flags);
        } else if (getIntent() == null || !getIntent().getBooleanExtra(EXTRA_DOCK, false)) {
            getWindow().clearFlags(flags);
        }
    }

    /**
     * Which edge the timer's strip is on: the layout's answer where there is one, or the app's.
     *
     * The app's own answer is the one it has always given — down the left lying down, across the
     * top standing up. A drawn layout can say otherwise, and a broken one says nothing: anything
     * unreadable falls back, for the same reason the clock's own drawing does.
     */
    /**
     * The boxes of the layout the user drew for this way up, or null to let the app arrange it.
     *
     * The same four cases {@link ClockView} treats as one — automatic, nothing for this
     * orientation, nothing drawn at all, or something that threw — because the answer here has to
     * agree with the answer there: whoever arranges the clock decides whether it is moved over for
     * the timer's strip.
     */
    private java.util.List<com.reteclock.core.layout.LayoutBox> drawnBoxes(boolean landscape) {
        if (safeMode) {
            return null;
        }
        try {
            com.reteclock.core.layout.LayoutPreset preset =
                    Settings.layoutInForce(this, landscape);
            if (preset == null || preset.isAutomatic()) {
                return null;
            }
            java.util.List<com.reteclock.core.layout.LayoutBox> boxes = preset.boxes();
            return boxes == null || boxes.isEmpty() ? null : boxes;
        } catch (RuntimeException broken) {
            return null;
        }
    }

    private int stripThickness(boolean landscape) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int shorter = Math.min(metrics.widthPixels, metrics.heightPixels);
        // Eight per cent of the shorter edge. It was sixteen, and on a real phone that was a band
        // as wide as a thumb for what is, most of the time, a bar and three small numbers. Halving
        // it halves everything on it too: the controls and the lettering are both measured from the
        // strip's thickness, so there is one number here rather than three that must agree.
        int wanted = Math.round(shorter * 0.08f);
        int floor = Math.round(28f * metrics.density);
        int ceiling = Math.round(60f * metrics.density);
        return Math.max(floor, Math.min(wanted, ceiling));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The phone may have gone from its light theme to its dark one, which is a colour change
        // here (issue #33) — the clock reads its colours again rather than waiting for a restart.
        view.reloadOptions();
        // The strip changes sides; the clock re-measures itself as it always did.
        layOutScreen();
        hideSystemBars();
    }

    /** What the strip asks the world for: sounds, speech, a flash, and the preset list. */
    /** Whether the touch in progress belongs to the calendar's arrows or to the saying. */
    private boolean ownGesture;

    /** The card a ringing bell raised, while it is up. One bell is asked about at a time. */
    private android.app.Dialog bellCard;

    /**
     * Raises the card for a bell that can be put off.
     *
     * A second bell arriving while the first card is up does not stack a second card: the sound is
     * the notice, and one question on screen is the whole point of asking one.
     */
    private void showBellCard(final com.reteclock.core.Bell bell) {
        if (bellCard != null && bellCard.isShowing()) {
            return;
        }
        if (isFinishing()) {
            return;
        }
        bellCard = BellCard.show(this, bell, new BellCard.Answer() {
            @Override
            public void putOff() {
                bells.putOff(bell, System.currentTimeMillis());
            }

            @Override
            public void stop() {
                bells.stopRinging(bell);
            }
        });
    }

    /** Takes the card away with the screen, so it is not left over a window nobody is at. */
    private void dismissBellCard() {
        if (bellCard != null) {
            if (bellCard.isShowing()) {
                bellCard.dismiss();
            }
            bellCard = null;
        }
    }

    /**
     * Stops whatever the app is playing, and says whether there was anything to stop.
     *
     * A bell or a timer's own sound, either way: a touch that lands on a phone making a noise means
     * "enough", and it means that before it means anything else on this screen.
     */
    private boolean silenceWhatIsSounding() {
        boolean stopped = bells.silence();
        if (cuePlayer.isPlaying()) {
            cuePlayer.fadeOutAndStop();
            stopped = true;
        }
        return stopped;
    }

    private final TimerView.Listener timerListener = new TimerView.Listener() {
        @Override
        public void remember(com.reteclock.core.TimerRun run, long startEpochMs) {
            if (run == null) {
                Settings.forgetRun(ClockActivity.this);
            } else {
                Settings.rememberRun(ClockActivity.this,
                        com.reteclock.core.TimerMemory.identityOf(timer == null
                                ? null : timer.preset()),
                        com.reteclock.core.TimerMemory.originOf(run,
                                android.os.SystemClock.elapsedRealtime()),
                        com.reteclock.core.TimerMemory.pausedAtOf(run), startEpochMs);
            }
        }

        @Override
        public void runEnded(com.reteclock.core.TimerRun run, long startEpochMs, long elapsedMs) {
            logRun(ClockActivity.this, run, startEpochMs, elapsedMs);
        }

        @Override
        public void openTimerSettings() {
            Intent intent = new Intent(ClockActivity.this, TimerSettingsActivity.class);
            startActivity(intent);
        }

        @Override
        public void cue(Tones.Note[] pattern) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockActivity.this);
            }
            sounds.play(pattern, Settings.timerAlert(ClockActivity.this));
        }

        @Override
        public void sound(String name, Tones.Note[] fallback) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockActivity.this);
            }
            CueSound.play(ClockActivity.this, cuePlayer, sounds, name, fallback);
        }

        @Override
        public void speak(String message) {
            if (message == null || message.isEmpty() || !CueSound.canSpeak(ClockActivity.this)) {
                return;
            }
            if (voice == null) {
                voice = new TimerVoice(ClockActivity.this);
            }
            voice.say(message, android.os.SystemClock.elapsedRealtime());
        }

        @Override
        public void flash() {
            flashScreen(3);
        }

        @Override
        public void choosePreset() {
            showPresetList();
        }
    };

    /**
     * Writes a run that has ended into the log, if a log is being kept and this ending is new.
     *
     * Shared by the clock and the screensaver because the rule is the same in both: a finished run
     * stays on the strip, and every view built afterwards notices it has finished, so the ending is
     * named — the preset, and when it began — and an ending already named is not written again.
     */
    static void logRun(android.content.Context context, com.reteclock.core.TimerRun run,
            long startEpochMs, long elapsedMs) {
        if (run == null || !Settings.timerLogKept(context)) {
            return;
        }
        String stamp = startEpochMs + "|" + com.reteclock.core.TimerMemory.identityOf(run.preset());
        if (Settings.runAlreadyLogged(context, stamp)) {
            return;
        }
        Settings.markRunLogged(context, stamp);
        TimerLog.write(context, run.preset(), startEpochMs, elapsedMs);
    }

    /** Three quick blinks of the whole screen: an interval has ended. */
    private void flashScreen(final int times) {
        if (flash == null || times <= 0) {
            return;
        }
        flash.setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flash.setVisibility(View.INVISIBLE);
                if (times > 1) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            flashScreen(times - 1);
                        }
                    }, 90L);
                }
            }
        }, 70L);
    }

    /** The hourglass: which preset should the controls start? */
    private void showPresetList() {
        final List<TimerPreset> presets = Settings.timerPresets(this);
        if (presets.isEmpty() || timer == null) {
            return;
        }
        // Putting the timer away — or bringing it back — is the first thing on the list, because
        // it is the one thing the hourglass can do that nothing else on the clock face can. When
        // the strip is already empty the item reads the other way round, so the same first line is
        // always the way out of whichever state you are in.
        final boolean wasHidden = Settings.timerHidden(this);
        final String[] names = new String[presets.size() + 1];
        names[0] = getString(wasHidden ? R.string.timer_unhide : R.string.timer_hide);
        for (int i = 0; i < presets.size(); i++) {
            names[i + 1] = presets.get(i).name + "   "
                    + com.reteclock.core.TimeReadout.of(presets.get(i).totalMs());
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.timer_pick)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            // Showing again must not touch the run: a timer that is going comes
                            // back mid-count rather than starting over, which is the whole point
                            // of having hidden it rather than stopped it.
                            Settings.setTimerHidden(ClockActivity.this, !wasHidden);
                            layOutScreen();
                            return;
                        }
                        // Choosing a preset is also how the strip comes back: the hourglass is the
                        // way in and the way out, and there is nowhere else to look for it.
                        Settings.setTimerHidden(ClockActivity.this, false);
                        Settings.setTimerChosen(ClockActivity.this, which - 1);
                        layOutScreen();
                        if (timer != null) {
                            timer.setPreset(presets.get(which - 1));
                        }
                    }
                })
                .show();
    }

    /**
     * Says that a long press opens the settings, briefly.
     *
     * Not when the charger started the clock: that happens on a bedside stand, possibly in the
     * middle of the night, and a message nobody asked for has no business appearing there. And not
     * once the user has opened the settings, because then they know.
     */
    private void maybeHintAtSettings() {
        boolean startedByCharger =
                getIntent() != null && getIntent().getBooleanExtra(EXTRA_DOCK, false);
        // A safe run says so whatever started it: the user needs to know why their picture is gone
        // and where to go about it, and that outranks a quiet bedside.
        if (safeMode) {
            Toast.makeText(this, R.string.hint_safe_mode, Toast.LENGTH_LONG).show();
            return;
        }
        if (startedByCharger || Settings.hintSeen(this)) {
            return;
        }
        Toast.makeText(this, R.string.hint_touch, Toast.LENGTH_LONG).show();
    }

    /** Whether the screen is being held at {@link com.reteclock.core.ScreenDim#DIM}. */
    private boolean dimmed;

    /**
     * Dims the screen, or gives it back to the phone's own setting (issue #41, R86).
     *
     * Only this window is touched — the system brightness is left exactly as the user set it, no
     * permission is asked for, and the dark ends with the clock however it ends.
     */
    private void toggleDim() {
        setDim(com.reteclock.core.ScreenDim.next(dimmed));
    }

    /**
     * Dark, or the phone's own level again.
     *
     * A tap toggles; a key says which it wants, because a remote has a key for each way and being
     * asked to dim twice should leave the screen dim rather than bright.
     */
    private void setDim(boolean wanted) {
        dimmed = wanted;
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = com.reteclock.core.ScreenDim.brightness(dimmed);
        getWindow().setAttributes(params);
    }

    /**
     * The Menu key opens the settings (issue #40, R85).
     *
     * A phone that still has the key carries the label this clock's bare face cannot draw, and it
     * is the first thing somebody looking for the settings presses. {@link KeyRoute} names the one
     * key that is taken; everything else — Back above all — reaches the platform untouched, which
     * is what lets the clock be left.
     */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // A held centre key has to be seen as held: the platform sets the flag on the repeat, and
        // asking for it is what tells a press apart from a hold without a timer of our own.
        if (event != null && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
            event.startTracking();
        }
        boolean held = event != null && event.isLongPress();
        return obey(com.reteclock.core.KeyRoute.onClock(keyCode, held, timerRunning()))
                || super.onKeyDown(keyCode, event);
    }

    /** Whether the strip is counting just now — the question the centre key's answer turns on. */
    private boolean timerRunning() {
        return timer != null && timer.isRunning();
    }

    /**
     * Does what the rule said, and says whether it did anything.
     *
     * Every one of these is something the face already does with a finger; nothing here is a second
     * way of working, only a second way of asking. See {@link com.reteclock.core.KeyRoute}.
     */
    private boolean obey(int route) {
        if (route == com.reteclock.core.KeyRoute.SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (route == com.reteclock.core.KeyRoute.MENU) {
            ClockMenu.show(this);
            return true;
        }
        if (route == com.reteclock.core.KeyRoute.DIM || route == com.reteclock.core.KeyRoute.UNDIM) {
            setDim(route == com.reteclock.core.KeyRoute.DIM);
            return true;
        }
        if (timer == null) {
            // The timer is switched off, so its keys are nobody's: they go to the platform, where
            // a media key at least reaches whatever is playing.
            return false;
        }
        if (route == com.reteclock.core.KeyRoute.TIMER_START
                || route == com.reteclock.core.KeyRoute.TIMER_PAUSE) {
            timer.toggle();
            return true;
        }
        if (route == com.reteclock.core.KeyRoute.TIMER_STOP) {
            timer.stop();
            return true;
        }
        return false;
    }

    /**
     * And the release with it: the platform opens its own (empty) options panel on the Menu key's
     * way up, and a press that has already opened the settings must not also leave a panel behind.
     */
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (com.reteclock.core.KeyRoute.onClock(keyCode) == com.reteclock.core.KeyRoute.SETTINGS) {
            return true;
        }
        // The centre key was answered on the way down; letting its release through would have the
        // platform click whatever happens to hold the focus.
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just changed the options in the settings screen — including whether
        // the timer is on at all, which changes what is on screen.
        view.reloadOptions();
        // The bells may have been edited in the meantime, and whatever fell while they were being
        // edited is not rung on the way back.
        bells.reload();
        slideWatch.reload();
        applyStayUnlocked();
        layOutScreen();
        view.start();
        if (timer != null) {
            timer.resumeDrawing();
        }
        // The mark this run leaves if it never comes back.
        Settings.setRunUnfinished(this, true);
        handler.removeCallbacks(reportHealthy);
        handler.postDelayed(reportHealthy, com.reteclock.core.SafeStart.HEALTHY_MS);
    }

    @Override
    protected void onPause() {
        view.stop();
        dismissBellCard();
        bells.stop();
        cuePlayer.stopNow();
        if (timer != null) {
            timer.pauseDrawing();
        }
        if (voice != null) {
            voice.release();
            voice = null;
        }
        // Being put aside is proof the clock was answering, so the mark comes off here too — the
        // long press into the settings is exactly the case a hung clock never reaches.
        handler.removeCallbacks(reportHealthy);
        Settings.setRunUnfinished(this, false);
        super.onPause();
    }


    /**
     * Hides the status and navigation bars where the platform supports it.
     *
     * The flag constants are compile-time integers, so referring to them costs nothing on
     * platforms that do not know them; the runtime check keeps the call itself safe.
     */
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 19) {
            view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else if (Build.VERSION.SDK_INT >= 14) {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }
}
