package edu.cmu.ioio;

import java.util.ArrayList;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.IOIOFactory;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.api.exception.IncompatibilityException;

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
	private final int servoMotorPin = 40;
	private final int PWMFrequency = 50;

	/** Thread body. */
	@Override
	public void run() {
		super.run();
		ioio_ = IOIOFactory.create();
	}

	public void scanSurrounding(final ArrayList<DistanceTable> distMap) {
		ProximityDetector.showMsg("",Constants.CLEAR);
		new Thread() {
			public void  run() {
				int i = 0;
				int status = Constants.FORWARD;
				boolean scanComplete = false;
				try {
					ioio_.waitForConnect();
					ProximityDetector.showMsg("Connected\n", Constants.DONT_PUBLISH);
					proximitySensor_ = ioio_.openAnalogInput(proximitySensorPin);
					_servo = ioio_.openPwmOutput(servoMotorPin, PWMFrequency); // 20ms periods
					while (scanComplete != true) {
						_servo.setDutyCycle(0.05f + i * 0.05f);
						synchronized (this) {
							if (status == Constants.FORWARD) {
								i++;
								if (i == 12) {
									status = Constants.BACKWARD;
									ProximityDetector.showMsg(new Integer(i).toString() + "\n",
											Constants.DONT_PUBLISH);
								}
							} else if (status == Constants.BACKWARD) {
								i--;
								if (i == 0) {
									status = Constants.FORWARD;
									scanComplete = true;
									ProximityDetector.showMsg(new Integer(i).toString() + "\n",
											Constants.DONT_PUBLISH);
								}
							}
						}
						float volt = proximitySensor_.getVoltage();
						ProximityDetector.showMsg("" + DistanceTable.getDistance(distMap, volt), Constants.DONT_PUBLISH);
						sleep(2000);
					}
				} catch (ConnectionLostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IncompatibilityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					try {
						ioio_.waitForDisconnect();
						// showMsg("\nDisconnected\n", DONT_PUBLISH);
					} catch (InterruptedException e) {
					}
				}
			}
		}.start();
	}
}
