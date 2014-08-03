/*
 * Copyright 2006-2014 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.rawdatamethods.targetedpeakdetection;

import net.sf.mzmine.datamodel.DataPoint;

/**
 * DataPoint implementation extended with retention time and scan number
 */
class GapDataPoint implements DataPoint {

	private int scanNumber;
	private double mz, rt, intensity;

	/**
     */
	GapDataPoint(int scanNumber, double mz, double rt, double intensity) {

		this.scanNumber = scanNumber;
		this.mz = mz;
		this.rt = rt;
		this.intensity = intensity;

	}

	int getScanNumber() {
		return scanNumber;
	}

	public double getIntensity() {
		return intensity;
	}

	public double getMZ() {
		return mz;
	}

	public double getRT() {
		return rt;
	}

	@Override
	public void setMZ(double mz) {
		this.mz = mz;
	}

	@Override
	public void setIntensity(double intensity) {
		this.intensity = intensity;
	}

}
