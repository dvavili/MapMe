package edu.cmu.ioio;

import java.util.ArrayList;

public class DistanceTable {
	private double voltageVal;
	private double distanceVal;

	public DistanceTable(double volt, double dist) {
		voltageVal = volt;
		distanceVal = dist;
	}

	public double getVoltageVal() {
		return voltageVal;
	}

	public void setVoltageVal(double voltageVal) {
		this.voltageVal = voltageVal;
	}

	public double getDistanceVal() {
		return distanceVal;
	}

	public void setDistanceVal(double distanceVal) {
		this.distanceVal = distanceVal;
	}

	public static double getDistance(ArrayList<DistanceTable> distMap, double volt) {
		double distance = -1;
		int index = -1;
		for (int i = 0; i < distMap.size(); i++) {
			if (volt < distMap.get(i).getVoltageVal()) {
				double prevVal = Double.MAX_VALUE;
				if (i != 0)
					prevVal = Math.abs(volt - distMap.get(i - 1).getVoltageVal());
				double nextVal = Math.abs(volt - distMap.get(i).getVoltageVal());
				index = prevVal < nextVal ? i - 1 : i;
			}
		}
		if (index != -1)
			distance = distMap.get(index).getDistanceVal();
		return distance;
	}

}
