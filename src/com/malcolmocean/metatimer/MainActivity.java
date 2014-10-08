package com.malcolmocean.metatimer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.malcolmocean.metatimer.R;

public class MainActivity extends Activity {
	/* values */
	private static final long TIMER_UPDATE_RATE = 30;
	private static final long LOWEST_ALLOWED_TIME = 2000;
	private static final long DEFAULT_MEAN_TIME = 4000;
	private long currentMeanTime = DEFAULT_MEAN_TIME;
	private double successIncreaseRatio = 1.1;
	private double mistakeDecreaseRatio = 0.8;
	private long activityTimeout = 30*1000;
	private double failedAttemptWeight = 0.5; // TODO grab from prefs

	public static String PREFS_TIMER_DEFAULT = "PREFS_TIMER_DEFAULT";
	public static String PREFS_TOTAL_TIME = "PREFS_TOTAL_TIME";
	public static String PREFS_DEV_MODE = "PREFS_DEV_MODE";
	
	private Activity myActivity;
	private Handler myHandler;
	private Thread myInactivityTimerThread;
	private SharedPreferences myPrefs;
	private TextView myTimerDigits; 
	private TextView debugOutput;
	private TextView totalOutput;
	private long lastRandomTime;
	private CountDownTimer myTimer;
	private static final long[] VIBRATION_PATTERN = {0L, 75L, 75L, 75L}; 
	private boolean timerActive = false;
	private boolean inactiveMode = true;
	private boolean wasLastTimeSuccessful = true; // ultimately make this an array of stuff
	private long lastSuccessfulMax = currentMeanTime;
	private long inactiveTime = (long) 1e15;
	private long lastTimerStartTime = -1;
	private long myTotalTime = 0; // TODO actually track days etc
	private boolean devMode = false;
	private Random myRandom = new Random();
	
	private ToggleButton myUpButton;
	private ToggleButton myDownButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		myActivity = this;
		myHandler = new Handler();
		setUpPrefs();
		setUpUI();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		savePrefs();
	}
	
	private void setUpUI() {
		setContentView(R.layout.activity_main);
		totalOutput = (TextView) findViewById(R.id.time_today);
		myTimerDigits = (TextView) findViewById(R.id.timerdigits);
		myTimerDigits.setOnLongClickListener(new View.OnLongClickListener() {
			
			@Override
			public boolean onLongClick(View arg0) {
				vibrateLP();
				(new DurationPickerDialog(myActivity, currentMeanTime)).show();
				return false;
			}
		});
		debugOutput = (TextView) findViewById(R.id.debug);
		debugOutput.setScrollContainer(true);
		updateTimerDigits(currentMeanTime);
		myUpButton = (ToggleButton) findViewById(R.id.btn_up);
		myUpButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				pressUpButton();
				myUpButton.setChecked(false);
			}
		});
		myDownButton = (ToggleButton) findViewById(R.id.btn_down);
		myDownButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				pressDownButton();
				myDownButton.setChecked(false);
			}
		});
		adjustButtons();
		totalOutput.setText(formatTotal(myTotalTime));
		totalOutput.setTextColor(devMode ? Color.RED : Color.WHITE);
		totalOutput.setOnLongClickListener(new View.OnLongClickListener() {@Override
			public boolean onLongClick(View arg0) {
				vibrateLP();
				toggleDevMode(); return false;
			}});
	}
	
	private SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
	
	private void setUpPrefs() {
		String date = df.format(new Date());
    	myPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    	currentMeanTime = myPrefs.getLong(PREFS_TIMER_DEFAULT, 4000L);
    	lastSuccessfulMax = currentMeanTime;
		myTotalTime = myPrefs.getLong(PREFS_TOTAL_TIME + "_" + date, 0L);
		devMode = myPrefs.getBoolean(PREFS_DEV_MODE, false);
	}
	
	private void savePrefs() {
		String date = df.format(new Date());
		SharedPreferences.Editor prefsEdit = myPrefs.edit();
		prefsEdit.putLong(PREFS_TIMER_DEFAULT, lastSuccessfulMax);
		toast("storing prefs timer default as " + lastSuccessfulMax);
		prefsEdit.putLong(PREFS_TOTAL_TIME + "_" + date, myTotalTime);
		prefsEdit.putBoolean(PREFS_DEV_MODE, devMode);
		prefsEdit.apply();
	}
	
	private void stopTimer() {
		try {
			myTimer.cancel();
		} catch (Exception e) {
			debugOutput.setText(e.getLocalizedMessage());
			e.printStackTrace();
//			Log.d("", e.printStackTrace());
		}
		timerActive = false;
		updateTimerDigits(currentMeanTime);
		adjustButtons();
	}
	
	private long adjustedDuration() {
		return adjustedDuration(true, -1);
	}

	private long adjustedDuration(boolean success) {
		return adjustedDuration(success, -1);
	}
	
	private long adjustedDuration(boolean success, long timeIn) {
		long result = currentMeanTime;
		if (success) {
			if (wasLastTimeSuccessful) {
				result = (long) (successIncreaseRatio * Math.max(lastRandomTime, currentMeanTime));
			} else {
				result = Math.max(lastRandomTime, currentMeanTime);
			}
		} else {
			if (timeIn > 0 && timeIn < lastRandomTime) { // if failed partway, harsher penalty
				// TODO maybe also consider whether failure happened before currentMeanTime
				if (wasLastTimeSuccessful) {
					result = (long) (mistakeDecreaseRatio * lastSuccessfulMax);
				} else {
					result = (long) (Math.sqrt(mistakeDecreaseRatio) * timeIn);
				}
			} else {
				if (wasLastTimeSuccessful) {
					result = (long) Math.max(lastSuccessfulMax, lastRandomTime * mistakeDecreaseRatio * mistakeDecreaseRatio);
				} else {
					result = (long) (mistakeDecreaseRatio * Math.min(lastRandomTime, currentMeanTime));
				}
			}
		}
		return Math.max(result, LOWEST_ALLOWED_TIME);
	}
	
	private void succeed() {
		currentMeanTime = adjustedDuration();
		debugOutput.append("...success!\n");
		updateTimerDigits(currentMeanTime);
		lastSuccessfulMax = lastRandomTime;
		wasLastTimeSuccessful = true;
		if(!devMode) {
			myTotalTime += lastRandomTime;
		}
	}
	
	// timeIn is -1 if happened at the end
	private void fail(long timeIn) {
		currentMeanTime = adjustedDuration(false, timeIn);
		if (timeIn > -1) {
			inactiveMode = true;
		}
		debugOutput.append("...not so much...\n");
		updateTimerDigits(currentMeanTime);
		wasLastTimeSuccessful = false;
		if(!devMode) {
			myTotalTime += (long) (failedAttemptWeight * lastRandomTime);
		}
	}

	private void adjustButtons () {
		String upText = "Start";
		String downText = "Stop";
		
		if (timerActive) {
			myDownButton.setEnabled(true);
			upText = "Legit \u2014 reset timer.";
			downText = "Oops; Stop \u2193\u2193";
		} else {
			if (inactiveMode) {
				myDownButton.setEnabled(false);
				downText = "Push above to start";
				upText = "Start timer";
			} else {
				myDownButton.setEnabled(true);
				if (wasLastTimeSuccessful) {
					upText = "Yes! \u2191 " + formatTimerDigits(adjustedDuration());
//					downText = "Oops! \u2193 " + adjustedDuration(false);
				} else {
					upText = "Cool. Do it again.";
//					downText = adjustedDuration(false) + " == " + currentMeanTime;
				}
				if (adjustedDuration(false) == currentMeanTime) {
					downText = "Oops. Try again.";
				} else {
					downText = "Oops \u2193 " + formatTimerDigits(adjustedDuration(false));
				}
			}
		}

		myUpButton.setTextOff(upText);
		myUpButton.setTextOn(upText);
		myUpButton.setText(upText);
		myDownButton.setTextOff(downText);
		myDownButton.setTextOn(downText);
		myDownButton.setText(downText);
	}
	
	private void pressUpButton () {
		if (!timerActive) { // last time was a success, update if within 30s
			if (!inactiveMode) {
				succeed();
			} else {
				debugOutput.append("\n");
			}
			startTimer();
		} else { // legit interruption
			inactiveMode = true;
			stopTimer();
		}
		totalOutput.setText(formatTotal(myTotalTime));
	}
	
	private void pressDownButton () { // TODO if bottom button disabled, do something different
		if (!timerActive) { // last time was a failure, update if within 30s
			if (!inactiveMode) {
				fail(-1);
			} else {
				debugOutput.append("\n");
			}
			startTimer();
		} else { // failed partway
			stopTimer();
			fail(System.currentTimeMillis() - lastTimerStartTime);
		}
		totalOutput.setText(formatTotal(myTotalTime));
	}
	
	private void startTimer() {
		timerActive = true;
		inactiveMode = false;
		inactiveTime = System.currentTimeMillis() + activityTimeout;
		lastRandomTime = getRandomMillis(currentMeanTime);
		toast("starting timer [" + lastRandomTime + "]");
		adjustButtons();
		lastTimerStartTime = System.currentTimeMillis();
		myTimer = new CountDownTimer(lastRandomTime, TIMER_UPDATE_RATE) {
			
			@Override
			public void onTick(long remaining) {
				updateTimerDigits(remaining);
			}
			
			@Override
			public void onFinish() {
				toast("Timer Complete");
				updateTimerDigits(currentMeanTime);
				vibrate();
				timerActive = false;
				inactiveMode = false;
//				lastTimerFinishTime = System.currentTimeMillis();
				debugOutput.append("Finished " + lastRandomTime);
				adjustButtons();
				inactiveTime = System.currentTimeMillis() + activityTimeout;
				if (myInactivityTimerThread == null) {
					myInactivityTimerThread = new Thread(new Runnable () {
						@Override
						public void run() {
							while (System.currentTimeMillis() < inactiveTime) {
								try {
									Log.d("TURBO", "sleeping for " + (inactiveTime - System.currentTimeMillis()));
									Thread.sleep(inactiveTime - System.currentTimeMillis());
								} catch (InterruptedException e) {}
							}
							inactiveMode = true;
							myHandler.post(new Runnable() {
								@Override
								public void run() {
									debugOutput.append("...inactive.");
									adjustButtons();
								}
							});
							myInactivityTimerThread = null;
						}
					});
					myInactivityTimerThread.start();
				}
			}
		};
		myTimer.start();
	}
	
	public void newDuration (long millis) {
		currentMeanTime = millis;
		debugOutput.append("...inactive.");
		inactiveMode = true;
		adjustButtons();
		if(myInactivityTimerThread != null && myInactivityTimerThread.isAlive()) {
			myInactivityTimerThread.interrupt();
			myInactivityTimerThread = null;
		}
		stopTimer();
	}
	
	@SuppressLint("DefaultLocale")
	private void updateTimerDigits (long millis) {
		myTimerDigits.setText(formatTimerDigits(millis));
	}
	
	@SuppressLint("DefaultLocale")
	private String formatTimerDigits (long millis) {
		long[] HMSm = millisToHMSm(millis);
		String hourString = HMSm[0] > 0 ? String.format("%01d:", HMSm[0]) : "";
		String minuteString = (HMSm[0] + HMSm[1]) > 0 ? String.format("%02d:", HMSm[1]) : "";
		String secString = (HMSm[0] + HMSm[1]) > 0 ? String.format("%02d", HMSm[2]) : String.format("%01d", HMSm[2]);
		long m2 = (long) Math.floor(HMSm[3]/100.0);
		String milliString = String.format(".%01d", m2 == 10 ? 9 : m2);
		String suffix = "s";
		if (minuteString.length() > 0 || hourString.length() > 0) {
			suffix = "";
		}
		String formatted = "~" + hourString + minuteString + secString + milliString + suffix;
		return formatted;
	}	

	private void vibrate() {
		Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		v.vibrate(VIBRATION_PATTERN, -1);
	}
	
	private void vibrateLP() {
		Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		v.vibrate(50);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			break;
		case R.id.action_devmode:
			toggleDevMode();
			break;
		}
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode){
		case KeyEvent.KEYCODE_VOLUME_UP:
			myUpButton.setChecked(true);
			pressUpButton();
			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			myDownButton.setChecked(true);
			pressDownButton();
		    return true;
		}
	 return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch(keyCode){
		case KeyEvent.KEYCODE_VOLUME_UP:
			myUpButton.setChecked(false);
			return true;
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			myDownButton.setChecked(false);
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		vibrateLP();
		Toast.makeText(this, "Pressed for a long time =) ", Toast.LENGTH_SHORT).show();
		return true;
	}

	private void toast(String message) {
	       Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
	
	public class DurationPickerDialog extends Dialog {
		Dialog myDialog = this;
		EditText hours;
		EditText mins;
		EditText secs;
		
		public DurationPickerDialog(Context context, long millis) {
			super(context);
			this.setTitle("Set Timer Duration");
			this.setContentView(R.layout.durationdialog);
			long[] timeArr = millisToHMSm(millis);

			hours = ((EditText) findViewById(R.id.set_hours));
			hours.setText(String.format("%02d", timeArr[0]));
			mins = ((EditText) findViewById(R.id.set_mins));
			mins.setText(String.format("%02d", timeArr[1]));
			secs = ((EditText) findViewById(R.id.set_secs));
			secs.setText(String.format("%02d", timeArr[2]));
			View oneToFocus = timeArr[0] > 0 ? hours :
				(timeArr[1] > 1 ? mins : secs);
			oneToFocus.requestFocus();
			((Button) findViewById(R.id.btn_cancel)).setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View arg0) {
					myDialog.cancel();
				}
			});
			((Button) findViewById(R.id.btn_ok)).setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View arg0) {
					long[] HMSm = {
							Long.parseLong(hours.getText().toString()),
							Long.parseLong(mins.getText().toString()),
							Long.parseLong(secs.getText().toString()),
							0L
					};
					newDuration(hMSmToMillis(HMSm));
					myDialog.dismiss();
				}
			});
		}
		
	}
	
	public long[] millisToHMSm (long millis) {
//		long[] result = new long[4];
		long totalSecs = millis/1000;
		long hours = totalSecs/(60*60);
		long mins = (totalSecs%(60*60))/(60);
		long secs = totalSecs%(60);
		long remainingMillis = millis%1000;
		long[] result = {hours, mins, secs, remainingMillis};
		return result;
	}
	
	public long hMSmToMillis (long[] HMSm) {
		return (HMSm[3] + 1000 * (HMSm[2] + 60 * (HMSm[1] + 60 * HMSm[0])));
	}
	
	public long getRandomMillis(long center) {
		double stddev = (Math.pow(center/1000, 0.6666)/4) * 1000;
		
		// Goal is approximately
		//  5s +- 1
		// 30s +- 3
		//  1m +- 4
		//  5m +- 10
		// wait, nope, not if I'm actually doing gaussian not just +-
		
		long result = (long)(center + stddev * myRandom.nextGaussian());
		if (result < center - 3 * stddev) {
			result = (long) (center - 3 * stddev);
		} else if (result > center + 3 * stddev) {
			result = (long) (center + 3 * stddev);
		}
		
		return result;
	}
	
	public String formatTotal(long millis) {
		long[] HMSm = millisToHMSm(millis);
		if (HMSm[0] == 0) {
			if (HMSm[1] == 0) {
				return HMSm[2] + "s";
			}
			return HMSm[1] + "m" + HMSm[2] + "s";
		}
		
		return HMSm[0] + ":" + HMSm[1] + ":" + HMSm[2];
	}

	// TODO get one of these classes doing something I like
	private class selectOnFocus implements View.OnFocusChangeListener {
		
		@Override
		public void onFocusChange(View view, boolean wat) {
			if (((EditText) view).hasFocus()) {
				((EditText) view).selectAll();
			}
		}
	}
	
	private class clearOnFocus implements View.OnFocusChangeListener {
		private String oldVal;
		@Override
		public void onFocusChange(View view, boolean wat) {
			EditText et = (EditText) view;
			if (et.hasFocus()) {
				oldVal = et.getText().toString();
				toast(oldVal);
				et.setText("");
			} else if (et.getText().toString() == "") {
				et.setText(oldVal);
			}
		}
	}
	
	private void toggleDevMode () {
		devMode = !devMode;
		if (devMode) {
			totalOutput.setTextColor(Color.RED);
		} else {
			totalOutput.setTextColor(Color.WHITE);
		}
	}
}
