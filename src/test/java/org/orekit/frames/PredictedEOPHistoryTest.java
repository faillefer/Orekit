/* Copyright 2002-2023 CS GROUP
 * Licensed to CS GROUP (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.frames;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.stream.Collectors;

import org.hipparchus.util.FastMath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.Utils;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;


public class PredictedEOPHistoryTest {

    @Test
    public void testExtensionDates() {

        // truncate EOP between 2018 and 2020
        final int mjdLimit = new DateComponents(2021, 1, 1).getMJD();
        final EOPHistory truncatedEOP = new EOPHistory(trueEOP.getConventions(),
                                                       trueEOP.getEntries().
                                                               stream().
                                                               filter(e -> e.getMjd() < mjdLimit).
                                                               collect(Collectors.toList()),
                                                       trueEOP.isSimpleEop(),
                                                       trueEOP.getTimeScales());

        // predicted history from truncated data
        EOPHistory predicted = new PredictedEOPHistory(truncatedEOP,
                                                       30 * Constants.JULIAN_DAY,
                                                       EOPFitter.createDefaultDut1FitterShortTermPrediction(),
                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction());
        Assertions.assertEquals(0.0,
                                new AbsoluteDate(2018, 1, 1, 0, 0, 0.0, utc).durationFrom(predicted.getStartDate()),
                                1.0e-10);
        Assertions.assertEquals(0.0,
                                new AbsoluteDate(2021, 1, 30, 0, 0, 0.0, utc).durationFrom(predicted.getEndDate()),
                                1.0e-10);
    }

    @Test
    public void testCommonCoverage() {

        // truncate EOP between 2018 and 2021
        final int mjdLimit = new DateComponents(2022, 1, 1).getMJD();
        final EOPHistory truncatedEOP = new EOPHistory(trueEOP.getConventions(),
                                                       trueEOP.getEntries().
                                                               stream().
                                                               filter(e -> e.getMjd() < mjdLimit).
                                                               collect(Collectors.toList()),
                                                       trueEOP.isSimpleEop(),
                                                       trueEOP.getTimeScales());

        EOPHistory predicted = new PredictedEOPHistory(truncatedEOP,
                                                       30 * Constants.JULIAN_DAY,
                                                       EOPFitter.createDefaultDut1FitterShortTermPrediction(),
                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction(),
                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction());

        // check we get the same value as raw EOP (dropping the last interpolated day)
        double maxErrorUT1  = 0;
        double maxErrorLOD  = 0;
        double maxErrorXp   = 0;
        double maxErrorYp   = 0;
        double maxErrordPsi = 0;
        double maxErrordEps = 0;
        double maxErrordx   = 0;
        double maxErrordy   = 0;
        for (double dt = 0;
             dt < truncatedEOP.getEndDate().durationFrom(truncatedEOP.getStartDate()) - Constants.JULIAN_DAY;
             dt += 20000.0) {
            final AbsoluteDate   date      = truncatedEOP.getStartDate().shiftedBy(dt);
            final PoleCorrection rawPC     = truncatedEOP.getPoleCorrection(date);
            final PoleCorrection predPC    = predicted.getPoleCorrection(date);
            final double[]       rawENC    = truncatedEOP.getEquinoxNutationCorrection(date);
            final double[]       predENC   = predicted.getEquinoxNutationCorrection(date);
            final double[]       rawNroNC  = truncatedEOP.getNonRotatinOriginNutationCorrection(date);
            final double[]       predNroNC = predicted.getNonRotatinOriginNutationCorrection(date);
            maxErrorUT1  = FastMath.max(maxErrorUT1,  truncatedEOP.getUT1MinusUTC(date) - predicted.getUT1MinusUTC(date));
            maxErrorLOD  = FastMath.max(maxErrorLOD,  truncatedEOP.getLOD(date)         - predicted.getLOD(date));
            maxErrorXp   = FastMath.max(maxErrorXp,   rawPC.getXp()            - predPC.getXp());
            maxErrorYp   = FastMath.max(maxErrorYp,   rawPC.getYp()            - predPC.getYp());
            maxErrordPsi = FastMath.max(maxErrordPsi, rawENC[0]                - predENC[0]);
            maxErrordEps = FastMath.max(maxErrordEps, rawENC[1]                - predENC[1]);
            maxErrordx   = FastMath.max(maxErrordx,   rawNroNC[0]              - predNroNC[0]);
            maxErrordy   = FastMath.max(maxErrordy,   rawNroNC[1]              - predNroNC[1]);
        }
        Assertions.assertEquals(0.0, maxErrorUT1,  1.0e-15);
        Assertions.assertEquals(0.0, maxErrorLOD,  1.0e-15);
        Assertions.assertEquals(0.0, maxErrorXp,   1.0e-15);
        Assertions.assertEquals(0.0, maxErrorYp,   1.0e-15);
        Assertions.assertEquals(0.0, maxErrordPsi, 1.0e-15);
        Assertions.assertEquals(0.0, maxErrordEps, 1.0e-15);
        Assertions.assertEquals(0.0, maxErrordx,   1.0e-15);
        Assertions.assertEquals(0.0, maxErrordy,   1.0e-15);
    }

    @Test
    public void testAccuracyShortTerm() {
        doTestAccuracy(true, 0.207, 0.776, 2.039, 10.938, 360.301, 6528.073, 43395.119, 171707.066, 484370.496, 1043267.054);
    }

    @Test
    public void testAccuracyLongTerm() {
        doTestAccuracy(false, 1.514, 1.987, 2.291, 3.204, 9.358, 20.701, 29.725, 39.764, 53.510, 79.884);
    }

    private void doTestAccuracy(final boolean shortTerm,
                                final double expectedMax001, final double expectedMax003,
                                final double expectedMax005, final double expectedMax010,
                                final double expectedMax030, final double expectedMax060,
                                final double expectedMax090, final double expectedMax120,
                                final double expectedMax150, final double expectedMax180) {

        double maxError001Days = 0;
        double maxError003Days = 0;
        double maxError005Days = 0;
        double maxError010Days = 0;
        double maxError030Days = 0;
        double maxError060Days = 0;
        double maxError090Days = 0;
        double maxError120Days = 0;
        double maxError150Days = 0;
        double maxError180Days = 0;
        for (int d = 0; d < 365; d += 14) {
            // set up a prediction
            final DateComponents dc = new DateComponents(new DateComponents(2021, 1, 1), d);
            final int mjdLimit = dc.getMJD();
            final EOPHistory truncatedEOP = new EOPHistory(trueEOP.getConventions(),
                                                           trueEOP.getEntries().
                                                           stream().
                                                           filter(e -> e.getMjd() < mjdLimit).
                                                           collect(Collectors.toList()),
                                                           trueEOP.isSimpleEop(),
                                                           trueEOP.getTimeScales());
            final PredictedEOPHistory predictedEOP = shortTerm ?
                                               new PredictedEOPHistory(truncatedEOP,
                                                                       180 * Constants.JULIAN_DAY,
                                                                       EOPFitter.createDefaultDut1FitterShortTermPrediction() ,
                                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                                       EOPFitter.createDefaultPoleFitterShortTermPrediction(),
                                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction(),
                                                                       EOPFitter.createDefaultNutationFitterShortTermPrediction()) :
                                               new PredictedEOPHistory(truncatedEOP,
                                                                       180 * Constants.JULIAN_DAY,
                                                                       EOPFitter.createDefaultDut1FitterLongTermPrediction() ,
                                                                       EOPFitter.createDefaultPoleFitterLongTermPrediction(),
                                                                       EOPFitter.createDefaultPoleFitterLongTermPrediction(),
                                                                       EOPFitter.createDefaultNutationFitterLongTermPrediction(),
                                                                       EOPFitter.createDefaultNutationFitterLongTermPrediction());

            // set up two itrf frames, one using true, one using predicted EOP
            final Frame itrfTrue = FramesFactory.buildUncachedITRF(trueEOP, TimeScalesFactory.getUTC());
            final Frame itrfPred = FramesFactory.buildUncachedITRF(predictedEOP, TimeScalesFactory.getUTC());

            final AbsoluteDate t0 = new AbsoluteDate(dc, trueEOP.getTimeScales().getUTC());
            for (double dt = 0; dt < 180 * Constants.JULIAN_DAY; dt += 7200) {
                final AbsoluteDate date = t0.shiftedBy(dt);
                final Transform t = itrfTrue.getTransformTo(itrfPred, date);
                final double deltaP = t.getRotation().getAngle() * Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
                if (dt <= Constants.JULIAN_DAY) {
                    maxError001Days = FastMath.max(maxError001Days, deltaP);
                }
                if (dt <= 3 * Constants.JULIAN_DAY) {
                    maxError003Days = FastMath.max(maxError003Days, deltaP);
                }
                if (dt <= 5 * Constants.JULIAN_DAY) {
                    maxError005Days = FastMath.max(maxError005Days, deltaP);
                }
                if (dt <= 10 * Constants.JULIAN_DAY) {
                    maxError010Days = FastMath.max(maxError010Days, deltaP);
                }
                if (dt <= 30 * Constants.JULIAN_DAY) {
                    maxError030Days = FastMath.max(maxError030Days, deltaP);
                }
                if (dt <= 60 * Constants.JULIAN_DAY) {
                    maxError060Days = FastMath.max(maxError060Days, deltaP);
                }
                if (dt <= 90 * Constants.JULIAN_DAY) {
                    maxError090Days = FastMath.max(maxError090Days, deltaP);
                }
                if (dt <= 120 * Constants.JULIAN_DAY) {
                    maxError120Days = FastMath.max(maxError120Days, deltaP);
                }
                if (dt <= 150 * Constants.JULIAN_DAY) {
                    maxError150Days = FastMath.max(maxError150Days, deltaP);
                }
                if (dt <= 180 * Constants.JULIAN_DAY) {
                    maxError180Days = FastMath.max(maxError180Days, deltaP);
                }
            }

        }

        Assertions.assertEquals(expectedMax001, maxError001Days, 0.001);
        Assertions.assertEquals(expectedMax003, maxError003Days, 0.001);
        Assertions.assertEquals(expectedMax005, maxError005Days, 0.001);
        Assertions.assertEquals(expectedMax010, maxError010Days, 0.001);
        Assertions.assertEquals(expectedMax030, maxError030Days, 0.001);
        Assertions.assertEquals(expectedMax060, maxError060Days, 0.001);
        Assertions.assertEquals(expectedMax090, maxError090Days, 0.001);
        Assertions.assertEquals(expectedMax120, maxError120Days, 0.001);
        Assertions.assertEquals(expectedMax150, maxError150Days, 0.001);
        Assertions.assertEquals(expectedMax180, maxError180Days, 0.001);

    }

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        final EOPHistory raw = FramesFactory.getEOPHistory(IERSConventions.IERS_2010, true);

        PredictedEOPHistory predicted = new PredictedEOPHistory(raw,
        		                                                30 * Constants.JULIAN_DAY,
        		                                                EOPFitter.createDefaultDut1FitterShortTermPrediction(),
        		                                                EOPFitter.createDefaultPoleFitterShortTermPrediction(),
        		                                                EOPFitter.createDefaultPoleFitterShortTermPrediction(),
        		                                                EOPFitter.createDefaultNutationFitterShortTermPrediction(),
        		                                                EOPFitter.createDefaultNutationFitterShortTermPrediction());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream    oos = new ObjectOutputStream(bos);
        oos.writeObject(predicted);

        Assertions.assertTrue(bos.size() > 185000);
        Assertions.assertTrue(bos.size() < 190000);

        ByteArrayInputStream  bis = new ByteArrayInputStream(bos.toByteArray());
        ObjectInputStream     ois = new ObjectInputStream(bis);
        PredictedEOPHistory deserialized  = (PredictedEOPHistory) ois.readObject();
        Assertions.assertEquals(predicted.getStartDate(), deserialized.getStartDate());
        Assertions.assertEquals(predicted.getEndDate(), deserialized.getEndDate());
        Assertions.assertEquals(predicted.getEntries().size(), deserialized.getEntries().size());
        for (int i = 0; i < predicted.getEntries().size(); ++i) {
            EOPEntry e1 = predicted.getEntries().get(i);
            EOPEntry e2 = deserialized.getEntries().get(i);
            Assertions.assertEquals(e1.getMjd(),         e2.getMjd());
            Assertions.assertEquals(e1.getDate(),        e2.getDate());
            Assertions.assertEquals(e1.getUT1MinusUTC(), e2.getUT1MinusUTC(), 1.0e-10);
            Assertions.assertEquals(e1.getLOD(),         e2.getLOD(),         1.0e-10);
            Assertions.assertEquals(e1.getDdEps(),       e2.getDdEps(),       1.0e-10);
            Assertions.assertEquals(e1.getDdPsi(),       e2.getDdPsi(),       1.0e-10);
            Assertions.assertEquals(e1.getDx(),          e2.getDx(),          1.0e-10);
            Assertions.assertEquals(e1.getDy(),          e2.getDy(),          1.0e-10);
            Assertions.assertEquals(e1.getX(),           e2.getX(),           1.0e-10);
            Assertions.assertEquals(e1.getY(),           e2.getY(),           1.0e-10);
        }

    }

    @BeforeEach
    public void setUp() {
        Utils.setDataRoot("eop-prediction");
        FramesFactory.clearEOPHistoryLoaders();
        FramesFactory.addDefaultEOP2000HistoryLoaders("none", "none", "^eopc04_14_IAU2000\\.[0-9][0-9]\\.txt$", "none", "none");
        utc     = TimeScalesFactory.getUTC();
        trueEOP = FramesFactory.getEOPHistory(IERSConventions.IERS_2010, true);

        // raw history between 2018 and 2022
        Assertions.assertEquals(0.0,
                                new AbsoluteDate(2018, 1, 1, 0, 0, 0.0, utc).durationFrom(trueEOP.getStartDate()),
                                1.0e-10);
        Assertions.assertEquals(0.0,
                                new AbsoluteDate(2022, 12, 31, 0, 0, 0.0, utc).durationFrom(trueEOP.getEndDate()),
                                1.0e-10);

    }

    @AfterEach
    public void tearDown() {
        utc          = null;
        trueEOP      = null;
    }

    TimeScale  utc;
    EOPHistory trueEOP;

}
