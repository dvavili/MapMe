package edu.cmu.ioio;

import ioio.lib.util.AbstractIOIOActivity;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import edu.cmu.distance.PedometerSettings;
import edu.cmu.distance.Settings;
import edu.cmu.distance.StepService;
import edu.cmu.distance.Utils;

/**
 * This is the main activity of the HelloIOIO example application.
 * 
 * It displays a toggle button on the screen, which enables control of the
 * on-board LED. This example shows a very simple usage of the IOIO, by using
 * the {@link AbstractIOIOActivity} class. For a more advanced use case, see the
 * HelloIOIOPower example.
 */
public class ProximityDetector extends Activity {
	EditText textArea;
	private IOIOThread ioio_thread_;
	static Handler guiHandler;
	JSONObject jsonObject;
	Button clearBtn, startIOIOBtn;

	URL paraimpuURL = null;
	URLConnection yc = null;
	BufferedReader in = null;

	private int mStepValue;
	private float mDistanceValue;
	private int mOrientationValue;
	private TextView mStepValueView;
	private TextView mDistanceValueView;
	private TextView mOrientationView;
	private SharedPreferences mSettings;
	private PedometerSettings mPedometerSettings;
	private Utils mUtils;
	private static final String TAG = "MapMe";
	private boolean mQuitting = false; // Set when user selected Quit from menu,
																			// can be used by onPause, onStop,
																			// onDestroy

	private SensorManager mSensorManager;
	private final SensorListener mListener = new SensorListener() {

		public void onSensorChanged(int sensor, float[] values) {
			synchronized (this) {
				mOrientationValue = (int) values[0];
			}
			if (mOrientationView != null)
				mOrientationView.setText(mOrientationValue + "");
		}

		public void onAccuracyChanged(int sensor, int accuracy) {
		}
	};

	private boolean mIsRunning;
	ArrayList<DistanceTable> irDistanceList = null;

	private StepService mService;

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = ((StepService.StepBinder) service).getService();

			mService.registerCallback(mCallback);
			mService.reloadSettings();

		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			synchronized (ProximityDetector.this) {
				switch (msg.what) {
				case Constants.STEPS_MSG:
					mStepValue = msg.arg1;
					mStepValueView.setText("" + mStepValue);
					break;
				case Constants.DISTANCE_MSG:
					mDistanceValue = msg.arg1 / 1000f;
					if (mDistanceValue <= 0) {
						mDistanceValueView.setText("0");
					} else {
						mDistanceValueView.setText(("" + (mDistanceValue)));
					}
					if (ioio_thread_ != null)
						ioio_thread_.scanSurrounding(mDistanceValue, 0, irDistanceList);
					break;
				default:
					super.handleMessage(msg);
				}
			}
		}
	};

	// TODO: unite all into 1 type of message
	private StepService.ICallback mCallback = new StepService.ICallback() {
		@Override
		public void stepsChanged(int value) {
			mHandler.sendMessage(mHandler.obtainMessage(Constants.STEPS_MSG, value, 0));
		}

		@Override
		public void distanceChanged(float value) {
			mHandler.sendMessage(mHandler.obtainMessage(Constants.DISTANCE_MSG, (int) (value * 1000), 0));
		}

		@Override
		public void paceChanged(int value) {
		}

		@Override
		public void speedChanged(float value) {
		}

		@Override
		public void caloriesChanged(float value) {
		}
	};

	/**
	 * Called when the activity is first created. Here we normally initialize our
	 * GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.distance_main);

		mStepValue = 0;
		mDistanceValue = 0;
		mUtils = Utils.getInstance();
		jsonObject = new JSONObject();
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		initializeGUIElements();
		loadDistanceMapValues();
		createGUIHandler();
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "[ACTIVITY] onStart");
		super.onStart();
	}

	/**
	 * Called when the application is resumed (also when first started). Here is
	 * where we'll create our IOIO thread.
	 */
	@Override
	protected void onResume() {
		super.onResume();

		mSensorManager.registerListener(mListener, SensorManager.SENSOR_ORIENTATION,
				SensorManager.SENSOR_DELAY_UI);

		mStepValueView = (TextView) findViewById(R.id.step_value);
		mDistanceValueView = (TextView) findViewById(R.id.distance_value);

		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mPedometerSettings = new PedometerSettings(mSettings);

		mUtils.setSpeak(mSettings.getBoolean("speak", false));

		// Read from preferences if the service was running on the last onPause
		mIsRunning = mPedometerSettings.isServiceRunning();

		// Start the service if this is considered to be an application start
		// (last onPause was long ago)
		if (!mIsRunning && mPedometerSettings.isNewStart()) {
			startStepService();
			bindStepService();
		} else if (mIsRunning) {
			bindStepService();
		}

		mPedometerSettings.clearServiceRunning();
	}

	/**
	 * Called when the application is paused. We want to disconnect with the IOIO
	 * at this point, as the user is no longer interacting with our application.
	 */
	@Override
	protected void onPause() {
		if (mIsRunning) {
			unbindStepService();
		}
		if (mQuitting) {
			mPedometerSettings.saveServiceRunningWithNullTimestamp(mIsRunning);
		} else {
			mPedometerSettings.saveServiceRunningWithTimestamp(mIsRunning);
		}

		mSensorManager.unregisterListener(mListener);

		try {
			ioio_thread_.join();
		} catch (InterruptedException e) {
		}

		super.onPause();
	}

	@Override
	protected void onStop() {
		mSensorManager.unregisterListener(mListener);
		super.onStop();
	}

	private void startStepService() {
		if (!mIsRunning) {
			Log.i(TAG, "[SERVICE] Start");
			mIsRunning = true;
			startService(new Intent(ProximityDetector.this, StepService.class));
		}
	}

	private void bindStepService() {
		Log.i(TAG, "[SERVICE] Bind");
		bindService(new Intent(ProximityDetector.this, StepService.class), mConnection,
				Context.BIND_AUTO_CREATE + Context.BIND_DEBUG_UNBIND);
	}

	private void unbindStepService() {
		Log.i(TAG, "[SERVICE] Unbind");
		unbindService(mConnection);
	}

	private void stopStepService() {
		Log.i(TAG, "[SERVICE] Stop");
		if (mService != null) {
			Log.i(TAG, "[SERVICE] stopService");
			stopService(new Intent(ProximityDetector.this, StepService.class));
		}
		mIsRunning = false;
	}

	private void resetValues(boolean updateDisplay) {
		if (mService != null && mIsRunning) {
			mService.resetValues();
		} else {
			mStepValueView.setText("0");
			mDistanceValueView.setText("0");
			SharedPreferences state = getSharedPreferences("state", 0);
			SharedPreferences.Editor stateEditor = state.edit();
			if (updateDisplay) {
				stateEditor.putInt("steps", 0);
				stateEditor.putInt("pace", 0);
				stateEditor.putFloat("distance", 0);
				stateEditor.putFloat("speed", 0);
				stateEditor.putFloat("calories", 0);
				stateEditor.commit();
			}
		}
	}

	public static void showMsg(String msg, int publish) {
		Message m = new Message();
		if (publish == Constants.PUBLISH_TO_TWITTER)
			msg += " #proximity_alert";
		m.obj = msg;
		m.arg1 = publish;
		guiHandler.sendMessage(m);
	}

	private void initializeGUIElements() {
		textArea = (EditText) findViewById(R.id.textarea);
		textArea.setFocusable(false);
		mOrientationView = (TextView) findViewById(R.id.orientationText);
		clearBtn = (Button) findViewById(R.id.clear);
		clearBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showMsg("", Constants.CLEAR);
			}
		});
		startIOIOBtn = (Button) findViewById(R.id.startIOIO);
		startIOIOBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				ioio_thread_ = new IOIOThread();
				ioio_thread_.start();
				// resetValues(true);
			}
		});
	}

	private void createGUIHandler() {
		guiHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				if (msg.arg1 == Constants.PUBLISH) {
					try {
						jsonObject.put("token", "441537b3-83fb-4487-9a47-35cc01132670");
						jsonObject.put("content-type", "text/plain");
						JSONObject finalMessage = new JSONObject();
						finalMessage.put("distance", mDistanceValue);
						finalMessage.put("orientation", mOrientationValue);
						finalMessage.put("irReadings", msg.obj.toString());
						JSONObject data = new JSONObject();
						data.put("message", msg.obj.toString());
						jsonObject.put("data", data);
						DefaultHttpClient httpclient = new DefaultHttpClient();
						HttpPost httpost = new HttpPost("http://paraimpu.crs4.it/data/new");
						StringEntity se = new StringEntity(jsonObject.toString());
						httpost.setEntity(se);
						httpost.setHeader("Accept", "application/json");
						httpost.setHeader("Content-type", "application/json");
						httpclient.execute(httpost);
						textArea.append("\nDistance = " + finalMessage.getInt("distance"));
						textArea.append("\nOrientation = " + finalMessage.getInt("orientation"));
						ArrayList<HashMap<Double, Double>> irReadings = convertToIRReadings(finalMessage
								.getString("irReadings"));
						textArea.append("\nIR Readings = " + irReadings);
					} catch (JSONException e) {
						textArea.append(e.getMessage());
					} catch (UnsupportedEncodingException e) {
						textArea.append(e.getMessage());
					} catch (ClientProtocolException e) {
						textArea.append(e.getMessage());
					} catch (IOException e) {
						textArea.append(e.getMessage());
					}
				} else if (msg.arg1 == Constants.CLEAR)
					textArea.setText("");
				else
					textArea.append(msg.obj.toString());
			}

			private ArrayList<HashMap<Double, Double>> convertToIRReadings(String irValueString) {
				ArrayList<HashMap<Double, Double>> irReadings = new ArrayList<HashMap<Double, Double>>();
				StringTokenizer irValueTokenizer = new StringTokenizer(irValueString.substring(1,
						irValueString.length() - 1), ",");
				while (irValueTokenizer.hasMoreTokens()) {
					String irReading = irValueTokenizer.nextToken().trim();
					double angle = new Double(irReading.substring(1, irReading.indexOf('=')));
					double distance = new Double(irReading.substring(irReading.indexOf('=') + 1,
							irReading.length() - 1));
					HashMap<Double, Double> irMap = new HashMap<Double, Double>();
					irMap.put(angle, distance);
					irReadings.add(irMap);
				}
				return irReadings;
			}
		};
	}

	private void loadDistanceMapValues() {
		try {
			irDistanceList = new ArrayList<DistanceTable>();
			Resources res = getResources();
			InputStream is = res.openRawResource(R.raw.ir_sensor);
			DataInputStream dis = new DataInputStream(is);
			boolean inputToRead = true;
			while (inputToRead) {
				try {
					StringTokenizer strTokens = new StringTokenizer(dis.readLine(), " ");
					double distance = new Double(strTokens.nextToken());
					double voltage = new Double(strTokens.nextToken());
					if (distance >= 19)
						irDistanceList.add(new DistanceTable(voltage, distance));
				} catch (EOFException eofEx) {
					inputToRead = false;
				} catch (Exception e) {
					inputToRead = false;
				}
			}
			Collections.sort(irDistanceList, new Comparator<DistanceTable>() {

				@Override
				public int compare(DistanceTable val1, DistanceTable val2) {
					return (val1.getVoltageVal() > val2.getVoltageVal() ? -1 : (val1.getVoltageVal() == val2
							.getVoltageVal() ? 0 : 1));
				}
			});
		} catch (Exception ioe) {
			ioe.printStackTrace();
		}
	}

	/* Creates the menu items */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (mIsRunning) {
			menu.add(0, Constants.MENU_PAUSE, 0, R.string.pause)
					.setIcon(android.R.drawable.ic_media_pause).setShortcut('1', 'p');
		} else {
			menu.add(0, Constants.MENU_RESUME, 0, R.string.resume)
					.setIcon(android.R.drawable.ic_media_play).setShortcut('1', 'p');
		}
		menu.add(0, Constants.MENU_RESET, 0, R.string.reset)
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel).setShortcut('2', 'r');
		menu.add(0, Constants.MENU_SETTINGS, 0, R.string.settings)
				.setIcon(android.R.drawable.ic_menu_preferences).setShortcut('8', 's');
		menu.add(0, Constants.MENU_QUIT, 0, R.string.quit)
				.setIcon(android.R.drawable.ic_lock_power_off).setShortcut('9', 'q');
		return true;
	}

	/* Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Constants.MENU_PAUSE:
			unbindStepService();
			stopStepService();
			return true;
		case Constants.MENU_RESUME:
			startStepService();
			bindStepService();
			return true;
		case Constants.MENU_RESET:
			resetValues(true);
			return true;
		case Constants.MENU_SETTINGS:
			Intent settingIntent = new Intent(ProximityDetector.this, Settings.class);
			ProximityDetector.this.startActivity(settingIntent);
			return true;
		case Constants.MENU_QUIT:
			resetValues(false);
			unbindStepService();
			stopStepService();
			mQuitting = true;
			if (ioio_thread_ != null) {
				ioio_thread_.stop();
			}
			finish();
			System.exit(0);
			return true;
		}
		return false;
	}

}