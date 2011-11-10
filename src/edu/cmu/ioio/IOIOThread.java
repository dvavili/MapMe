package edu.cmu.ioio;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.IOIO;
import ioio.lib.api.IOIOFactory;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.api.exception.IncompatibilityException;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This is the thread on which all the IOIO activity happens. It will be run
 * every time the application is resumed and aborted when it is paused. The
 * method setup() will be called right after a connection with the IOIO has been
 * established (which might happen several times!). Then, loop() will be called
 * repetitively until the IOIO gets disconnected.
 */
public class IOIOThread extends Thread {

	private IOIO ioio_;
	private PwmOutput _servo;
	private AnalogInput proximitySensor_;

	private final int proximitySensorPin = 41;
	private final int servoMotorPin = 35;
	private final int PWMFrequency = 50;

	/** Thread body. */
	@Override
	public void run() {
		super.run();
		ioio_ = IOIOFactory.create();
		connect();
	}

	private void connect() {
		try {
			ioio_.waitForConnect();
			proximitySensor_ = ioio_.openAnalogInput(proximitySensorPin);
			_servo = ioio_.openPwmOutput(servoMotorPin, PWMFrequency); // 20ms periods
		} catch (ConnectionLostException e) {
			ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
		} catch (IncompatibilityException e) {
			ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
		}
	}

	public void scanSurrounding(final float distance, final int orientation,
			final ArrayList<DistanceTable> distMap) {
		 ProximityDetector.showMsg("",Constants.CLEAR);
		new Thread() {
			@Override
			public void run() {
				ArrayList<HashMap<Double, Double>> distanceList = new ArrayList<HashMap<Double, Double>>();
				int i = 0;
				int status = Constants.FORWARD;
				boolean scanComplete = false;
				try {
					while (scanComplete != true) {
						_servo.setDutyCycle(0.05f + i * 0.05f);
						synchronized (this) {
							if (status == Constants.FORWARD) {
								i++;
								if (i >= 5) {
									status = Constants.BACKWARD;
									ProximityDetector.showMsg("Scanning clockwise\n",
											Constants.DONT_PUBLISH);
								}
							} else if (status == Constants.BACKWARD) {
								i--;
								if (i <= 0) {
									status = Constants.FORWARD;
									scanComplete = true;
									ProximityDetector.showMsg("Scanning Anti-clockwise\n",
											Constants.DONT_PUBLISH);
								}
							}
						}
						float volt = proximitySensor_.getVoltage();
						double distance = DistanceTable.getDistance(distMap, volt);
						HashMap<Double, Double> distMap = new HashMap<Double, Double>();
						distMap.put(new Double(i * 10), new Double(distance));
						distanceList.add(distMap);
						sleep(200);
					}
					ProximityDetector.showMsg(distanceList.toString(), Constants.PUBLISH);
				} catch (ConnectionLostException e) {
					ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
					connect();
				} catch (InterruptedException e) {
					ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
				} catch (Exception e) {
					ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
				}
			}
		}.start();
	}
}
