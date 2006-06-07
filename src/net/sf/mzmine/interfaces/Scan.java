/*
 * Copyright 2006 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.interfaces;

/**
 * This class represent one spectrum of a raw data file.
 */
public interface Scan {

    public int getScanNumber();

    public int getMSLevel();

    public double getPrecursorMZ();

    public double getRetentionTime();

    public double getMZRangeMin();
    public double getMZRangeMax();

    public double getBasePeakMZ();
    public double getBasePeakIntensity();

    public double[] getMZValues();
    public double[] getIntensityValues();

    public int getNumberOfDataPoints();

    public boolean isCentroided();
    
    /**
     * 
     * @return parent scan number or -1 if there is no parent scan
     */
    public int getParentScanNumber();
    
    /**
     * 
     * @return array of fragment scan numbers, or null if there are none
     */
    public int[] getFragmentScanNumbers();

}
