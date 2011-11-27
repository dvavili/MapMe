//package edu.cmu.ioio;
//
//import ioio.lib.api.AnalogInput;
//import ioio.lib.api.DigitalInput;
//import ioio.lib.api.DigitalInput.Spec.Mode;
//import ioio.lib.api.DigitalOutput;
//import ioio.lib.api.IOIO;
//import ioio.lib.api.IOIOFactory;
//import ioio.lib.api.PulseInput;
//import ioio.lib.api.PulseInput.PulseMode;
//import ioio.lib.api.PwmOutput;
//import ioio.lib.api.PulseInput.ClockRate;
//import ioio.lib.api.exception.ConnectionLostException;
//import ioio.lib.api.exception.IncompatibilityException;
//import ioio.lib.util.AbstractIOIOActivity;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//
//import android.graphics.Path.Direction;
//
///**
// * This is the thread on which all the IOIO activity happens. It will be run
// * every time the application is resumed and aborted when it is paused. The
// * method setup() will be called right after a connection with the IOIO has been
// * established (which might happen several times!). Then, loop() will be called
// * repetitively until the IOIO gets disconnected.
// */
//public class IOIOThread extends AbstractIOIOActivity.IOIOThread {
//
////	private IOIO ioio_;
//	private PwmOutput _servo;
//	private AnalogInput proximitySensor_;
//	// private DigitalOutput sonarOutput_;
//	// private PulseInput sonarInput_;
//	Object syncObj = new Object();
//
//	private final int proximitySensorPin = 41;
//	private final int servoMotorPin = 5;
//	private final int PWMFrequency = 50;
//	// private final int sonarOutputPin = 14;
//	// private final int sonarInputPin = 9;
//
//	public static int sleepTime = 100;// 46;
//	public static float forwardDutyCycle = 0.05f;
//	public static float backwardDutyCycle = 0.1f;
//
//	public void setup() throws ConnectionLostException {
//		try {
//		
//			pwmOutput_ = ioio_.openPwmOutput(10, 100);
//
//			enableUi(true);
//		} catch (ConnectionLostException e) {
//			enableUi(false);
//			throw e;
//
//		}
//	}
//	/** Thread body. */
//	@Override
//	public void run() {
//		super.run();
//		try {
//			ioio_ = IOIOFactory.create();
//			connect();
//			ProximityDetector.connected = true;
//		} catch (Exception e) {
//			ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//		}
//	}
//
//	private void connect() {
//		try {
//			ioio_.waitForConnect();
//			// sonarOutput_ = ioio_.openDigitalOutput(sonarOutputPin);
//			// DigitalInput.Spec spec = new
//			// DigitalInput.Spec(sonarInputPin,Mode.PULL_UP);
//			// sonarInput_ = ioio_.openPulseInput(spec,
//			// ClockRate.RATE_40KHz,PulseMode.POSITIVE,true);
//			proximitySensor_ = ioio_.openAnalogInput(proximitySensorPin);
//			_servo = ioio_.openPwmOutput(servoMotorPin, PWMFrequency); // 20ms periods
//		} catch (ConnectionLostException e) {
//			ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//		} catch (IncompatibilityException e) {
//			ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//		}
//	}
//
//	public void scanSurrounding(final float distance, final int orientation,
//			final ArrayList<DistanceTable> distMap) {
//		ProximityDetector.showMsg("", Constants.CLEAR);
//		new Thread() {
//			@Override
//			public void run() {
//				synchronized (syncObj) {
//					HashMap<Double, Double> distanceList = new HashMap<Double, Double>();
//					int i = 0;
//					boolean scanComplete = false;
//					int status = Constants.FORWARD;
//					try {
//						_servo.setDutyCycle(forwardDutyCycle);
//						// long initialTime = 0, endTime = 0;
//						float volt = 0;
//						double distance = 0;
//						// int duration = 0;
//						// while (scanComplete != true) {
//						// sonarOutput_.write(false);
//						// sleep(0, 2000);
//						// sonarOutput_.write(true);
//						// sleep(0, 5000);
//						// sonarOutput_.write(false);
//						// do {
//						// distance = sonarInput_.getDuration();
//						// duration++;
//						// if(duration % 50 == 0)
//						// ProximityDetector.showMsg(duration + " " + distance+"\n",
//						// Constants.DONT_PUBLISH);
//						// } while(distance>0);
//						// ProximityDetector.showMsg(duration + " " + distance+"\n",
//						// Constants.DONT_PUBLISH);
//
//						while (!scanComplete) {
//							// boolean value = sonarInput_.read();
//							// if (value == true && initialTime == 0)
//							// initialTime = System.currentTimeMillis();
//							// else if(value== false){
//							// // sonarInput_.waitForValue(false);
//							// endTime = System.currentTimeMillis();
//							// break;
//							// }
//							// }
//
//							_servo.setDutyCycle(0f);
//							volt = proximitySensor_.getVoltage();
//							// distance = endTime - initialTime;
//							distance = DistanceTable.getDistance(distMap, volt);
//							// distanceList.put(new Double(i * 15), distance);
//
//							distanceList.put(new Double(0), distance);
//							_servo.setDutyCycle(forwardDutyCycle);
//							sleep(sleepTime);
//							scanComplete = true;
//							i++;
//							if (i >= 11) {
//								scanComplete = true;
//								_servo.setDutyCycle(0f);
//								sleep(1000);
//								_servo.setDutyCycle(backwardDutyCycle);
//								sleep(540);
//								_servo.setDutyCycle(0f);
//								break;
//							}
//							_servo.setDutyCycle(forwardDutyCycle);
//							sleep(sleepTime);
//							_servo.setDutyCycle(0f);
//						}
//						ProximityDetector.showMsg(distanceList.toString(), Constants.PUBLISH);
//					} catch (ConnectionLostException e) {
//						ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//						connect();
//					} catch (InterruptedException e) {
//						ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//					} catch (Exception e) {
//						ProximityDetector.showMsg(e.getMessage(), Constants.DONT_PUBLISH);
//					}
//				}
//			}
//		}.start();
//	}
//}
