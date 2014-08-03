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

/* Code created was by or on behalf of Syngenta and is released under the open source license in use for the
 * pre-existing code or project. Syngenta does not assert ownership or copyright any over pre-existing work.
 */

package net.sf.mzmine.modules.peaklistmethods.identification.camera;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.mzmine.datamodel.DataPoint;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.MZmineObjectBuilder;
import net.sf.mzmine.datamodel.PeakIdentity;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.datamodel.MsScan;
import net.sf.mzmine.datamodel.impl.PeakListRowAnnotationImpl;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.MZTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.DataPointSorter;
import net.sf.mzmine.util.RUtilities;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

/**
 * A task to perform a CAMERA search.
 *
 * @author $Author$
 * @version $Revision$
 */
public class CameraSearchTask extends AbstractTask {

    // Logger.
    private static final Logger LOG = Logger.getLogger(CameraSearchTask.class.getName());

    // Required version of CAMERA.
    private static final String CAMERA_VERSION = "1.12";

    // Minutes to seconds conversion factor.
    private static final double SECONDS_PER_MINUTE = 60.0;

    // The MS-level processed by this module.
    private static final int MS_LEVEL = 1;

    // Isotope regular expression.
    private static final Pattern ISOTOPE_PATTERN = Pattern.compile("\\[\\d+\\](.*)");

    // Peak signal to noise ratio.
    private static final double SIGNAL_TO_NOISE = 10.0;

    // Data point sorter.
    private static final DataPointSorter ASCENDING_MASS_SORTER =
            new DataPointSorter(SortingProperty.MZ, SortingDirection.Ascending);

    // Peak list to process.
    private final PeakList peakList;

    // Task progress.
    private double progress;

    // Parameters.
    private final Double fwhmSigma;
    private final Double fwhmPercentage;
    private final Integer isoMaxCharge;
    private final Integer isoMaxCount;
    private final MZTolerance isoMassTolerance;
    private final Double corrThreshold;
    private final Double corrPValue;

    public CameraSearchTask(final ParameterSet parameters, final PeakList list) {

        // Initialize.
        peakList = list;
        progress = 0.0;

        // Parameters.
        fwhmSigma = parameters.getParameter(CameraSearchParameters.FWHM_SIGMA).getValue();
        fwhmPercentage = parameters.getParameter(CameraSearchParameters.FWHM_PERCENTAGE).getValue();
        isoMaxCharge = parameters.getParameter(CameraSearchParameters.ISOTOPES_MAX_CHARGE).getValue();
        isoMaxCount = parameters.getParameter(CameraSearchParameters.ISOTOPES_MAXIMUM).getValue();
        isoMassTolerance = parameters.getParameter(CameraSearchParameters.ISOTOPES_MZ_TOLERANCE).getValue();
        corrThreshold = parameters.getParameter(CameraSearchParameters.CORRELATION_THRESHOLD).getValue();
        corrPValue = parameters.getParameter(CameraSearchParameters.CORRELATION_P_VALUE).getValue();
    }

    @Override
    public String getTaskDescription() {

        return "Identification of pseudo-spectra in " + peakList;
    }

    @Override
    public double getFinishedPercentage() {

        return progress;
    }

    @Override
    public void run() {

        try {

            setStatus(TaskStatus.PROCESSING);

            // Check number of raw data files.
            if (peakList.getNumberOfRawDataFiles() != 1) {

                throw new IllegalStateException(
                        "CAMERA can only process peak lists for a single raw data file, i.e. non-aligned peak lists.");
            }

            // Run the search.
            cameraSearch(peakList.getRawDataFile(0));

            if (!isCanceled()) {

                // Finished.
                setStatus(TaskStatus.FINISHED);
                LOG.info("CAMERA Search completed");
            }

            // Repaint the window to reflect the change in the peak list
            MZmineCore.getDesktop().getMainWindow().repaint();
        }
        catch (Throwable t) {

            LOG.log(Level.SEVERE, "CAMERA Search error", t);
            errorMessage = t.getMessage();
            setStatus(TaskStatus.ERROR);
        }
    }

    /**
     * Perform CAMERA search.
     *
     * @param rawFile raw data file of peak list to process.
     */
    private void cameraSearch(final RawDataFile rawFile) {

        LOG.finest("Detecting peaks.");

        // Get R engine.
        final Rengine rEngine;
        try {

            rEngine = RUtilities.getREngine();
        }
        catch (Throwable t) {

            throw new IllegalStateException("CAMERA requires R but it couldn't be loaded (" + t.getMessage() + ')');
        }

        synchronized (RUtilities.R_SEMAPHORE) {

            // Is R installed - load CAMERA library.
            if (rEngine.eval("require(CAMERA)").asBool().isFALSE()) {

                throw new IllegalStateException("The CAMERA R package couldn't be loaded - is it installed in R?");
            }

            // Check version of CAMERA.
            if (rEngine.eval("packageVersion('CAMERA') >= '" + CAMERA_VERSION + '\'').asBool().isFALSE()) {

                throw new IllegalStateException(
                        "An old version of the CAMERA package is installed in R - please update CAMERA to version " +
                        CAMERA_VERSION + " or later");
            }

            // Create empty peaks matrix.
            rEngine.eval("columnHeadings <- c('mz','mzmin','mzmax','rt','rtmin','rtmax','into','intb','maxo','sn')",
                         false);
            rEngine.eval("peaks <- matrix(nrow=0, ncol=length(columnHeadings))", false);
            rEngine.eval("colnames(peaks) <- columnHeadings", false);

            // Initialize.
            final Feature[] peaks = peakList.getPeaks(rawFile);
            progress = 0.0;

            // Initialize scan map.
            final Map<MsScan, Set<DataPoint>> peakDataPointsByScan =
                    new HashMap<MsScan, Set<DataPoint>>(rawFile.getNumOfScans(MS_LEVEL));
            int dataPointCount = 0;
            for (final int scanNumber : rawFile.getScanNumbers(MS_LEVEL)) {

                // Create a set to hold data points (sorted by m/z).
                final Set<DataPoint> dataPoints = new TreeSet<DataPoint>(ASCENDING_MASS_SORTER);

                // Add a dummy data point.
                dataPoints.add(MZmineObjectBuilder.getDataPoint());
                dataPointCount++;

                // Map the set.
                peakDataPointsByScan.put(rawFile.getScan(scanNumber), dataPoints);
            }

            // Add peaks.
            double progressInc = 1.0 / (double) peaks.length;
            for (final Feature peak : peaks) {

                // Get peak data.
                Range rtRange = null;
                Range intRange = null;
                final double mz = peak.getMZ();

                // Get the peak's data points per scan.
                for (final int scanNumber : peak.getScanNumbers()) {

                    final MsScan scan = rawFile.getScan(scanNumber);
                    if (scan.getMSLevel() != MS_LEVEL) {

                        throw new IllegalStateException(
                                "CAMERA can only process peak lists from MS-level " + MS_LEVEL);
                    }

                    // Copy the data point.
                    final DataPoint dataPoint = peak.getDataPoint(scanNumber);
                    if (dataPoint != null) {

                        final double intensity = dataPoint.getIntensity();
                        peakDataPointsByScan.get(scan).add(MZmineObjectBuilder.getDataPoint(mz, intensity));
                        dataPointCount++;

                        // Update RT range.
                        final double rt = scan.getRetentionTime();
                        if (rtRange == null) {

                            rtRange = new Range(rt);
                        } else {

                            rtRange.extendRange(rt);
                        }

                        // Update intensity range.
                        if (intRange == null) {
                            intRange = new Range(intensity);
                        } else {
                            intRange.extendRange(intensity);
                        }
                    }
                }

                // Set peak values.
                final double area = peak.getArea();
                final double maxo = intRange == null ? peak.getHeight() : intRange.getMax();
                final double rtMin = (rtRange == null ? peak.getRawDataPointsRTRange() : rtRange).getMin();
                final double rtMax = (rtRange == null ? peak.getRawDataPointsRTRange() : rtRange).getMax();

                // Add peak row.
                rEngine.eval("peaks <- rbind(peaks, c("
                             + mz + ", "                 // mz
                             + mz + ", "                 // mzmin: use the same as mz.
                             + mz + ", "                 // mzmax: use the same as mz.
                             + peak.getRT() + ", "       // rt
                             + rtMin + ", "              // rtmin
                             + rtMax + ", "              // rtmax
                             + area + ", "               // into: peak area.
                             + area + ", "               // intb: doesn't affect result, use area.
                             + maxo + ", "               // maxo
                             + SIGNAL_TO_NOISE + "))", false);

                progress += progressInc;
            }

            progress = 0.0;
            progressInc = 0.25;

            // Create R vectors.
            final int scanCount = peakDataPointsByScan.size();
            final double[] scanTimes = new double[scanCount];
            final int[] scanIndices = new int[scanCount];
            final double[] masses = new double[dataPointCount];
            final double[] intensities = new double[dataPointCount];

            // Fill vectors.
            int scanIndex = 0;
            int pointIndex = 0;
            for (final int scanNumber : rawFile.getScanNumbers(MS_LEVEL)) {

                final MsScan scan = rawFile.getScan(scanNumber);
                scanTimes[scanIndex] = scan.getRetentionTime();
                scanIndices[scanIndex] = pointIndex + 1;
                scanIndex++;

                for (final DataPoint dataPoint : peakDataPointsByScan.get(scan)) {

                    masses[pointIndex] = dataPoint.getMZ();
                    intensities[pointIndex] = dataPoint.getIntensity();
                    pointIndex++;
                }
            }

            // Set vectors.
            rEngine.assign("scantime", scanTimes);
            rEngine.assign("scanindex", scanIndices);
            rEngine.assign("mass", masses);
            rEngine.assign("intensity", intensities);

            // Construct xcmsRaw object
            rEngine.eval("xRaw <- new(\"xcmsRaw\")", false);
            rEngine.eval("xRaw@tic <- intensity", false);
            rEngine.eval("xRaw@scantime <- scantime * " + SECONDS_PER_MINUTE, false);
            rEngine.eval("xRaw@scanindex <- scanindex", false);
            rEngine.eval("xRaw@env$mz <- mass", false);
            rEngine.eval("xRaw@env$intensity <- intensity", false);

            // Create the xcmsSet object.
            rEngine.eval("xs <- new('xcmsSet')", false);

            // Set peaks.
            rEngine.eval("xs@peaks <- peaks", false);

            // Set file (dummy) file path.
            rEngine.eval("xs@filepaths  <- ''", false);

            // Set sample name.
            rEngine.assign("sampleName", peakList.getName());
            rEngine.eval("sampnames(xs) <- sampleName", false);

            // Create an empty xsAnnotate.
            rEngine.eval("an <- xsAnnotate(xs, sample=1)", false);

            // Group by RT.
            rEngine.eval("an <- groupFWHM(an, sigma=" + fwhmSigma
                         + ", perfwhm=" + fwhmPercentage
                         + ')',
                         false);
            progress += progressInc;

            // Identify isotopes.
            rEngine.eval("an <- findIsotopes(an, maxcharge=" + isoMaxCharge
                         + ", maxiso=" + isoMaxCount
                         + ", ppm=" + isoMassTolerance.getPpmTolerance()
                         + ", mzabs=" + isoMassTolerance.getMzTolerance()
                         + ')',
                         false);
            progress += progressInc;

            // Split groups by correlating peak shape (need to set xraw to raw data).
            rEngine.eval("an <- groupCorr(an, calcIso=TRUE, xraw=xRaw, cor_eic_th=" + corrThreshold
                         + ", pval=" + corrPValue
                         + ')',
                         false);
            progress += progressInc;

            // Get the peak list.
            rEngine.eval("peakList <- getPeaklist(an)", false);

            // Extract the pseudo-spectra and isotope annotations from the peak list.
            final REXP spectraExp = rEngine.eval("as.integer(peakList$pcgroup)");
            final REXP isotopeExp = rEngine.eval("peakList$isotopes");

            // Add identities.
            if (spectraExp != null) {

                addPseudoSpectraIdentities(peaks, spectraExp, isotopeExp);
            }
            progress += progressInc;
        }
    }

    /**
     * Add pseudo-spectra identities.
     *
     * @param peaks      peaks to annotate with identities.
     * @param spectraExp the pseudo-spectra ids vector.
     * @param isotopeExp the isotopes vector.
     */
    private void addPseudoSpectraIdentities(final Feature[] peaks,
                                            final REXP spectraExp,
                                            final REXP isotopeExp) {

        // Get the spectra and isotopes vectors.
        final int[] spectra = spectraExp.asIntArray();
        final String[] isotopes = isotopeExp == null ? null : isotopeExp.asStringArray();

        // Add identities for each peak.
        int peakIndex = 0;
        for (final Feature peak : peaks) {

            // Create pseudo-spectrum identity
            final PeakListRowAnnotationImpl identity =  new PeakListRowAnnotationImpl(
                    "Pseudo-spectrum #" + String.format("%03d", spectra[peakIndex]));
            identity.setPropertyValue(PeakIdentity.PROPERTY_METHOD, "Bioconductor CAMERA");

            // Add isotope info, if any.
            if (isotopes != null) {

                final String isotope = isotopes[peakIndex].trim();
                if (isotope.length() > 0) {


                    // Parse the isotope pattern.
                    final Matcher matcher = ISOTOPE_PATTERN.matcher(isotope);
                    if (matcher.matches()) {

                        identity.setPropertyValue("Isotope", matcher.group(1));

                    } else {

                        LOG.warning("Irregular isotope value: " + isotope);
                    }
                }
            }

            // Add identity to peak's row.
            peakList.getPeakRow(peak).addPeakIdentity(identity, true);
            peakIndex++;
        }
    }
}
