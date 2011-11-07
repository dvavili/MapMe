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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import edu.cmu.distance.Pedometer;
import edu.cmu.distance.PedometerSettings;
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
	Button exitBtn, clearBtn;

	URL paraimpuURL = null;
	URLConnection yc = null;
	BufferedReader in = null;

	private int mStepValue;
	private float mDistanceValue;
	private TextView mStepValueView;
	private TextView mDistanceValueView;
	private SharedPreferences mSettings;
	private PedometerSettings mPedometerSettings;
	private Utils mUtils;
	private static final String TAG = "MapMe";
	private boolean mQuitting = false; // Set when user selected Quit from menu,
										// can be used by onPause, onStop,
										// onDestroy

	private boolean mIsRunning;
	ArrayList<DistanceTable> irDistanceList = null;

	private StepService mService;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = ((StepService.StepBinder) service).getService();

			mService.registerCallback(mCallback);
			mService.reloadSettings();

		}

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
					mStepValue = (int) msg.arg1;
					mStepValueView.setText("" + mStepValue);
					ioio_thread_.scanSurrounding(irDistanceList);
					break;
				case Constants.DISTANCE_MSG:
					mDistanceValue = ((int) msg.arg1) / 1000f;
					if (mDistanceValue <= 0) {
						mDistanceValueView.setText("0");
					} else {
						mDistanceValueView.setText(("" + (mDistanceValue)));
					}
					break;
				default:
					super.handleMessage(msg);
				}
			}
		}
	};

	// TODO: unite all into 1 type of message
	private StepService.ICallback mCallback = new StepService.ICallback() {
		public void stepsChanged(int value) {
			mHandler.sendMessage(mHandler.obtainMessage(Constants.STEPS_MSG, value, 0));
		}

		public void distanceChanged(float value) {
			mHandler.sendMessage(mHandler.obtainMessage(Constants.DISTANCE_MSG,
					(int) (value * 1000), 0));
		}

		public void paceChanged(int value) {}
		public void speedChanged(float value) {}
		public void caloriesChanged(float value) {}
	};

	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.distance_main);

		mStepValue = 0;
		mDistanceValue = 0;
		mUtils = Utils.getInstance();
		jsonObject = new JSONObject();

		initializeGUIElements();
		textArea.append("in create");
		loadDistanceMapValues();
		createGUIHandler();
		startReceivingMessages();
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "[ACTIVITY] onStart");
		super.onStart();
	}

	private void startReceivingMessages() {
		new Thread() {
			public void run() {
				try {
					while (true) {
						paraimpuURL = new URL(
								"http://paraimpu.crs4.it/use?token=3a0653f0-a885-4eae-9230-eba4bb0daad8");
						yc = paraimpuURL.openConnection();
						in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
						String inputLine;

						if ((inputLine = in.readLine()) != null) {
							// Process on the input
						}
						Thread.sleep(1000);
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					try {
						in.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start();
	}

	/**
	 * Called when the application is resumed (also when first started). Here is
	 * where we'll create our IOIO thread.
	 */
	@Override
	protected void onResume() {
		super.onResume();
		ioio_thread_ = new IOIOThread();
		ioio_thread_.start();

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
		showMsg("in resume", Constants.DONT_PUBLISH);
	}

	/**
	 * Called when the application is paused. We want to disconnect with the
	 * IOIO at this point, as the user is no longer interacting with our
	 * application.
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

		super.onPause();
		stopStepService();

		try {
			ioio_thread_.join();
		} catch (InterruptedException e) {
		}
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
		bindService(new Intent(ProximityDetector.this, StepService.class), mConnection, Context.BIND_AUTO_CREATE
				+ Context.BIND_DEBUG_UNBIND);
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
        }
        else {
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
		exitBtn = (Button) findViewById(R.id.exit);
		exitBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				resetValues(false);
				unbindStepService();
                stopStepService();
                mQuitting = true;
                finish();
				System.exit(0);
			}
		});
		clearBtn = (Button) findViewById(R.id.clear);
		clearBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showMsg("", Constants.CLEAR);
			}
		});

	}

	private void createGUIHandler() {
		guiHandler = new Handler() {
			public void handleMessage(Message msg) {
				if (msg.arg1 == Constants.PUBLISH) {
					try {
						jsonObject.put("token", "441537b3-83fb-4487-9a47-35cc01132670");
						jsonObject.put("content-type", "text/plain");
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
					return (val1.getVoltageVal() > val2.getVoltageVal() ? -1 : (val1
							.getVoltageVal() == val2.getVoltageVal() ? 0 : 1));
				}
			});
//			for (int i = 0; i < irDistanceList.size(); i++) {
//				textArea.append(i + " " + irDistanceList.get(i).getVoltageVal() + " "
//						+ irDistanceList.get(i).getDistanceVal() + "\n");
//			}
		} catch (Exception ioe) {
			ioe.printStackTrace();
		}
	}

}