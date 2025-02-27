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
package org.orekit.gnss;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.hipparchus.exception.LocalizedCoreFormats;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.geometry.euclidean.twod.Vector2D;
import org.hipparchus.util.FastMath;
import org.orekit.annotation.DefaultDataContext;
import org.orekit.data.DataContext;
import org.orekit.data.DataSource;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScales;

/** Parser for Rinex measurements files.
 * <p>
 * Supported versions are: 2.00, 2.10, 2.11, 2.12 (unofficial), 2.20 (unofficial),
 * 3.00, 3.01, 3.02, 3.03, 3.04, and 3.05.
 * </p>
 * @see <a href="https://files.igs.org/pub/data/format/rinex2.pdf">rinex 2.0</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex210.pdf">rinex 2.10</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex211.pdf">rinex 2.11</a>
 * @see <a href="http://www.aiub.unibe.ch/download/rinex/rinex212.txt">unofficial rinex 2.12</a>
 * @see <a href="http://www.aiub.unibe.ch/download/rinex/rnx_leo.txt">unofficial rinex 2.20</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex300.pdf">rinex 3.00</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex301.pdf">rinex 3.01</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex302.pdf">rinex 3.02</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex303.pdf">rinex 3.03</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex304.pdf">rinex 3.04</a>
 * @see <a href="https://files.igs.org/pub/data/format/rinex305.pdf">rinex 3.05</a>
 * @since 12.0
 */
public class RinexObservationParser {

    /** Default name pattern for rinex 2 observation files. */
    public static final String DEFAULT_RINEX_2_NAMES = "^\\w{4}\\d{3}[0a-x](?:\\d{2})?\\.\\d{2}[oO]$";

    /** Default name pattern for rinex 3 observation files. */
    public static final String DEFAULT_RINEX_3_NAMES = "^\\w{9}_\\w{1}_\\d{11}_\\d{2}\\w_\\d{2}\\w{1}_\\w{2}\\.rnx$";

    /** GPS time scale. */
    private static final String GPS = "GPS";

    /** Galileo time scale. */
    private static final String GAL = "GAL";

    /** GLONASS time scale. */
    private static final String GLO = "GLO";

    /** QZSS time scale. */
    private static final String QZS = "QZS";

    /** Beidou time scale. */
    private static final String BDT = "BDT";

    /** IRNSS time scale. */
    private static final String IRN = "IRN";

    /** Maximum number of satellites per line in Rinex 2 format . */
    private static final int MAX_SAT_PER_RINEX_2_LINE = 12;

    /** Maximum number of observations per line in Rinex 2 format. */
    private static final int MAX_OBS_PER_RINEX_2_LINE = 5;

    /** Set of time scales. */
    private final TimeScales timeScales;

    /** Simple constructor.
     * <p>
     * This constructor uses the {@link DataContext#getDefault() default data context}.
     * </p>
     */
    @DefaultDataContext
    public RinexObservationParser() {
        this(DataContext.getDefault().getTimeScales());
    }

    /**
     * Create a RINEX loader/parser with the given source of RINEX auxiliary data files.
     * @param timeScales the set of time scales to use when parsing dates.
     * @since 12.0
     */
    public RinexObservationParser(final TimeScales timeScales) {
        this.timeScales = timeScales;
    }

    /**
     * Parse RINEX observations messages.
     * @param source source providing the data to parse
     * @return parsed observations
     */
    public List<ObservationDataSet> parse(final DataSource source) {

        Stream<LineParser> candidateParsers = Stream.of(LineParser.HEADER_VERSION);

        // placeholders for parsed data
        final ParseInfo parseInfo = new ParseInfo(source.getName());

        try (Reader reader = source.getOpener().openReaderOnce();
             BufferedReader br = new BufferedReader(reader)) {
            ++parseInfo.lineNumber;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                final String l = line;
                final Optional<LineParser> selected = candidateParsers.filter(p -> p.canHandle.test(l)).findFirst();
                if (selected.isPresent()) {
                    try {
                        selected.get().parsingMethod.parse(line, parseInfo);
                    } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                        throw new OrekitException(e,
                                                  OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                  parseInfo.lineNumber, source.getName(), line);
                    }
                    ++parseInfo.lineNumber;
                    candidateParsers = selected.get().allowedNextProvider.apply(parseInfo);
                } else {
                    throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                              parseInfo.lineNumber, source.getName(), line);
                }
            }
        } catch (IOException ioe) {
            throw new OrekitException(ioe, LocalizedCoreFormats.SIMPLE_MESSAGE, ioe.getLocalizedMessage());
        }

        return parseInfo.observationDataSets;

    }

    /** Transient data used for parsing a RINEX observation messages file.
     * @since 12.0
     */
    private class ParseInfo {

        /** Name of the data source. */
        private final String name;

        /** Set of time scales for parsing dates. */
        private final TimeScales timeScales;

        /** Current line number of the navigation message. */
        private int lineNumber;

        /** Rinex header associated with this data set. */
        private final RinexObservationHeader header;

        /** List of Observation data sets. */
        private final List<ObservationDataSet> observationDataSets;

        /** Date of the observation. */
        private AbsoluteDate tObs;

        /** Receiver clock offset (seconds). */
        private double rcvrClkOffset;

        /** time scale for parsing dates. */
        private TimeScale timeScale;

        /** Number of observation types. */
        private int nbTypes;

        /** Number of satellites. */
        private int nbSat;

        /** Number of satellites in the current observations block. */
        private int nbSatObs;

        /** Number of scaling factors. */
        private int nbObsScaleFactor;

        /** Index of satellite in current observation. */
        private int indexObsSat;

        /** Line number of start of next observation. */
        private int nextObsStartLineNumber;

        /** Current satellite system. */
        private SatelliteSystem currentSystem;

        /** Number of satellites affected by phase shifts. */
        private int phaseShiftNbSat;

        /** Number of GLONASS satellites. */
        private int nbGlonass;

        /** Satellites affected by phase shift. */
        private final List<SatInSystem> satPhaseShift;

        /** Type of observation affected by phase shift. */
        private ObservationType phaseShiftTypeObs;

        /** Phase shift correction. */
        private double corrPhaseShift;

        /** Indicator for completed header. */
        private boolean headerCompleted;

        /** Indicator for skipping special records (eventFlag from 2 to 5). */
        private boolean specialRecord;

        /** Indicator for skipping cyckle slip records (enventFlag == 6). */
        private boolean cycleSlip;

        /** Scaling factors. */
        private final List<ObservationType> typesObsScaleFactor;

        /** Observation types for each satellite systems. */
        private final Map<SatelliteSystem, List<ObservationType>> mapTypeObs;

        /** Types of observations. */
        private final List<ObservationType> typesObs;

        /** Observations. */
        private final List<ObservationData> observations;

        /** Satellites in current observation. */
        private final List<SatInSystem> satObs;

        /** Constructor, build the ParseInfo object.
         * @param name name of the data source
         */
        ParseInfo(final String name) {
            // Initialize default values for fields
            this.name                   = name;
            this.timeScales             = RinexObservationParser.this.timeScales;
            this.header                 = new RinexObservationHeader();
            this.observationDataSets    = new ArrayList<>();
            this.lineNumber             = 0;
            this.tObs                   = AbsoluteDate.PAST_INFINITY;
            this.timeScale              = null;
            this.nbTypes                = -1;
            this.nbSat                  = -1;
            this.nbSatObs               = -1;
            this.nbGlonass              = -1;
            this.phaseShiftNbSat        = -1;
            this.nbObsScaleFactor       = -1;
            this.nextObsStartLineNumber = -1;
            this.mapTypeObs             = new HashMap<>();
            this.typesObs               = new ArrayList<>();
            this.observations           = new ArrayList<>();
            this.satPhaseShift          = new ArrayList<>();
            this.typesObsScaleFactor    = new ArrayList<>();
            this.satObs                 = new ArrayList<>();
        }

    }

    /** Parsers for specific lines. */
    private enum LineParser {

        /** Parser for version, file type and satellite system. */
        HEADER_VERSION(line -> RinexUtils.matchesLabel(line, "RINEX VERSION / TYPE"),
                       (line, parseInfo) ->  RinexUtils.parseVersionFileTypeSatelliteSystem(line, parseInfo.name, parseInfo.header,
                                                                                            2.00, 2.10, 2.11, 2.12, 2.20,
                                                                                            3.00, 3.01, 3.02, 3.03, 3.04, 3.05),
                       LineParser::headerNext),

        /** Parser for generating program and emiting agency. */
        HEADER_PROGRAM(line -> RinexUtils.matchesLabel(line, "PGM / RUN BY / DATE"),
                       (line, parseInfo) -> RinexUtils.parseProgramRunByDate(line, parseInfo.lineNumber, parseInfo.name,
                                                                             parseInfo.timeScales, parseInfo.header),
                       LineParser::headerNext),

        /** Parser for comments. */
        COMMENT(line -> RinexUtils.matchesLabel(line, "COMMENT"),
                       (line, parseInfo) -> RinexUtils.parseComment(line, parseInfo.header),
                       LineParser::commentNext),

        /** Parser for marker name. */
        MARKER_NAME(line -> RinexUtils.matchesLabel(line, "MARKER NAME"),
                    (line, parseInfo) ->  parseInfo.header.setMarkerName(RinexUtils.parseString(line, 0, RinexUtils.LABEL_INDEX)),
                    LineParser::headerNext),

        /** Parser for marker number. */
        MARKER_NUMBER(line -> RinexUtils.matchesLabel(line, "MARKER NUMBER"),
                      (line, parseInfo) -> parseInfo.header.setMarkerNumber(RinexUtils.parseString(line, 0, 20)),
                      LineParser::headerNext),

        /** Parser for marker type. */
        MARKER_TYPE(line -> RinexUtils.matchesLabel(line, "MARKER TYPE"),
                    (line, parseInfo) -> parseInfo.header.setMarkerType(RinexUtils.parseString(line, 0, 20)),
                    LineParser::headerNext),

        /** Parser for observer agency. */
        OBSERVER_AGENCY(line -> RinexUtils.matchesLabel(line, "OBSERVER / AGENCY"),
                        (line, parseInfo) -> {
                            parseInfo.header.setObserverName(RinexUtils.parseString(line, 0, 20));
                            parseInfo.header.setAgencyName(RinexUtils.parseString(line, 20, 40));
                        },
                        LineParser::headerNext),

        /** Parser for receiver tnumber, type and version. */
        REC_NB_TYPE_VERS(line -> RinexUtils.matchesLabel(line, "REC # / TYPE / VERS"),
                         (line, parseInfo) -> {
                             parseInfo.header.setReceiverNumber(RinexUtils.parseString(line, 0, 20));
                             parseInfo.header.setReceiverType(RinexUtils.parseString(line, 20, 20));
                             parseInfo.header.setReceiverVersion(RinexUtils.parseString(line, 40, 20));
                         },
                         LineParser::headerNext),

        /** Parser for antenna number and type. */
        ANT_NB_TYPE(line -> RinexUtils.matchesLabel(line, "ANT # / TYPE"),
                    (line, parseInfo) -> {
                        parseInfo.header.setAntennaNumber(RinexUtils.parseString(line, 0, 20));
                        parseInfo.header.setAntennaType(RinexUtils.parseString(line, 20, 20));
                    },
                    LineParser::headerNext),

        /** Parser for approximative position. */
        APPROX_POSITION_XYZ(line -> RinexUtils.matchesLabel(line, "APPROX POSITION XYZ"),
                            (line, parseInfo) -> {
                                parseInfo.header.setApproxPos(new Vector3D(RinexUtils.parseDouble(line, 0, 14),
                                                                           RinexUtils.parseDouble(line, 14, 14),
                                                                           RinexUtils.parseDouble(line, 28, 14)));
                            },
                            LineParser::headerNext),

        /** Parser for antenna reference point. */
        ANTENNA_DELTA_H_E_N(line -> RinexUtils.matchesLabel(line, "ANTENNA: DELTA H/E/N"),
                            (line, parseInfo) -> {
                                parseInfo.header.setAntennaHeight(RinexUtils.parseDouble(line, 0, 14));
                                parseInfo.header.setEccentricities(new Vector2D(RinexUtils.parseDouble(line, 14, 14),
                                                                                RinexUtils.parseDouble(line, 28, 14)));
                            },
                            LineParser::headerNext),

        /** Parser for antenna reference point. */
        ANTENNA_DELTA_X_Y_Z(line -> RinexUtils.matchesLabel(line, "ANTENNA: DELTA X/Y/Z"),
                            (line, parseInfo) -> {
                                parseInfo.header.setAntennaReferencePoint(new Vector3D(RinexUtils.parseDouble(line,  0, 14),
                                                                                       RinexUtils.parseDouble(line, 14, 14),
                                                                                       RinexUtils.parseDouble(line, 28, 14)));
                            },
                            LineParser::headerNext),

        /** Parser for antenna phase center. */
        ANTENNA_PHASECENTER(line -> RinexUtils.matchesLabel(line, "ANTENNA: PHASECENTER"),
                            (line, parseInfo) -> {
                                parseInfo.header.setObservationCode(RinexUtils.parseString(line, 2, 3));
                                parseInfo.header.setAntennaPhaseCenter(new Vector3D(RinexUtils.parseDouble(line, 5, 9),
                                                                                    RinexUtils.parseDouble(line, 14, 14),
                                                                                    RinexUtils.parseDouble(line, 28, 14)));
                            },
                            LineParser::headerNext),

        /** Parser for antenna bore sight. */
        ANTENNA_B_SIGHT_XYZ(line -> RinexUtils.matchesLabel(line, "ANTENNA: B.SIGHT XYZ"),
                            (line, parseInfo) -> {
                                parseInfo.header.setAntennaBSight(new Vector3D(RinexUtils.parseDouble(line,  0, 14),
                                                                               RinexUtils.parseDouble(line, 14, 14),
                                                                               RinexUtils.parseDouble(line, 28, 14)));
                            },
                            LineParser::headerNext),

        /** Parser for antenna zero direction. */
        ANTENNA_ZERODIR_AZI(line -> RinexUtils.matchesLabel(line, "ANTENNA: ZERODIR AZI"),
                            (line, parseInfo) -> parseInfo.header.setAntennaAzimuth(RinexUtils.parseDouble(line, 0, 14)),
                            LineParser::headerNext),

        /** Parser for antenna zero direction. */
        ANTENNA_ZERODIR_XYZ(line -> RinexUtils.matchesLabel(line, "ANTENNA: ZERODIR XYZ"),
                            (line, parseInfo) -> parseInfo.header.setAntennaZeroDirection(new Vector3D(RinexUtils.parseDouble(line, 0, 14),
                                                                                                       RinexUtils.parseDouble(line, 14, 14),
                                                                                                       RinexUtils.parseDouble(line, 28, 14))),
                            LineParser::headerNext),

        /** Parser for wavelength factors. */
        WAVELENGTH_FACT_L1_2(line -> RinexUtils.matchesLabel(line, "WAVELENGTH FACT L1/2"),
                             (line, parseInfo) -> {
                                 // optional line in Rinex 2 header, not stored for now
                             },
                             LineParser::headerNext),

        /** Parser for indicator of receiver clock offset application. */
        RCV_CLOCK_OFFS_APPL(line -> RinexUtils.matchesLabel(line, "RCV CLOCK OFFS APPL"),
                            (line, parseInfo) -> parseInfo.header.setClkOffset(RinexUtils.parseInt(line, 0, 6)),
                            LineParser::headerNext),

        /** Parser for observation interval. */
        INTERVAL(line -> RinexUtils.matchesLabel(line, "INTERVAL"),
                 (line, parseInfo) -> parseInfo.header.setInterval(RinexUtils.parseDouble(line, 0, 10)),
                 LineParser::headerNext),

        /** Parser for time of first observation. */
        TIME_OF_FIRST_OBS(line -> RinexUtils.matchesLabel(line, "TIME OF FIRST OBS"),
                          (line, parseInfo) -> {
                              if (parseInfo.header.getSatelliteSystem() == SatelliteSystem.MIXED) {
                                  // in case of mixed data, time scale must be specified in the Time of First line
                                  final String timeScaleStr = RinexUtils.parseString(line, 48, 3);

                                  if (timeScaleStr.equals(GPS)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getGPS();
                                  } else if (timeScaleStr.equals(GAL)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getGST();
                                  } else if (timeScaleStr.equals(GLO)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getGLONASS();
                                  } else if (timeScaleStr.equals(QZS)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getQZSS();
                                  } else if (timeScaleStr.equals(BDT)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getBDT();
                                  } else if (timeScaleStr.equals(IRN)) {
                                      parseInfo.timeScale = parseInfo.timeScales.getIRNSS();
                                  } else {
                                      throw new OrekitException(OrekitMessages.UNSUPPORTED_FILE_FORMAT, parseInfo.name);
                                  }
                              } else {
                                  parseInfo.timeScale = parseInfo.header.getSatelliteSystem().getDefaultTimeSystem(parseInfo.timeScales);
                                  if (parseInfo.timeScale == null) {
                                      throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                                parseInfo.lineNumber, parseInfo.name, line);
                                  }
                              }
                              parseInfo.header.setTFirstObs(new AbsoluteDate(RinexUtils.parseInt(line, 0, 6),
                                                                             RinexUtils.parseInt(line, 6, 6),
                                                                             RinexUtils.parseInt(line, 12, 6),
                                                                             RinexUtils.parseInt(line, 18, 6),
                                                                             RinexUtils.parseInt(line, 24, 6),
                                                                             RinexUtils.parseDouble(line, 30, 13),
                                                                             parseInfo.timeScale));
                          },
                          LineParser::headerNext),

        /** Parser for time of last observation. */
        TIME_OF_LAST_OBS(line -> RinexUtils.matchesLabel(line, "TIME OF LAST OBS"),
                         (line, parseInfo) -> {
                             parseInfo.header.setTLastObs(new AbsoluteDate(RinexUtils.parseInt(line, 0, 6),
                                                                           RinexUtils.parseInt(line, 6, 6),
                                                                           RinexUtils.parseInt(line, 12, 6),
                                                                           RinexUtils.parseInt(line, 18, 6),
                                                                           RinexUtils.parseInt(line, 24, 6),
                                                                           RinexUtils.parseDouble(line, 30, 13),
                                                                           parseInfo.timeScale));
                         },
                         LineParser::headerNext),

        /** Parser for leap seconds. */
        LEAP_SECONDS(line -> RinexUtils.matchesLabel(line, "LEAP SECONDS"),
                     (line, parseInfo) -> {
                         parseInfo.header.setLeapSeconds(RinexUtils.parseInt(line, 0, 6));
                         if (parseInfo.header.getFormatVersion() >= 3.0) {
                             parseInfo.header.setLeapSecondsFuture(RinexUtils.parseInt(line, 6, 6));
                             parseInfo.header.setLeapSecondsWeekNum(RinexUtils.parseInt(line, 12, 6));
                             parseInfo.header.setLeapSecondsDayNum(RinexUtils.parseInt(line, 18, 6));
                         }
                     },
                     LineParser::headerNext),

        /** Parser for PRN and number of observations . */
        PRN_NB_OF_OBS(line -> RinexUtils.matchesLabel(line, "PRN / # OF OBS"),
                      (line, parseInfo) ->  {
                          // optional line, not stored for now
                      },
                      LineParser::headerNext),

        /** Parser for number of satellites. */
        NB_OF_SATELLITES(line -> RinexUtils.matchesLabel(line, "# OF SATELLITES"),
                         (line, parseInfo) -> parseInfo.nbSat = RinexUtils.parseInt(line, 0, 6),
                         LineParser::headerNext),

        /** Parser for number and types of observations. */
        TYPES_OF_OBSERV(line -> RinexUtils.matchesLabel(line, "# / TYPES OF OBSERV") ||
                                RinexUtils.matchesLabel(line, "SYS / # / OBS TYPES"),
                           (line, parseInfo) -> {
                               final double version = parseInfo.header.getFormatVersion();
                               if (parseInfo.nbTypes < 0) {
                                   // first line of types of observations
                                   if (version < 3) {
                                       // Rinex 2 has only one system
                                       parseInfo.currentSystem = parseInfo.header.getSatelliteSystem();
                                       parseInfo.nbTypes       = RinexUtils.parseInt(line, 0, 6);
                                   } else {
                                       // Rinex 3 and above allows mixed systems
                                       parseInfo.currentSystem = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1));
                                       parseInfo.nbTypes       = RinexUtils.parseInt(line, 3, 3);
                                       if (parseInfo.currentSystem               != parseInfo.header.getSatelliteSystem() &&
                                           parseInfo.header.getSatelliteSystem() != SatelliteSystem.MIXED) {
                                           throw new OrekitException(OrekitMessages.INCONSISTENT_SATELLITE_SYSTEM,
                                                                     parseInfo.lineNumber, parseInfo.name,
                                                                     parseInfo.header.getSatelliteSystem(),
                                                                     parseInfo.currentSystem);
                                       }
                                   }
                               }

                               final int firstIndex = version < 3 ? 10 : 7;
                               final int increment  = version < 3 ?  6 : 4;
                               final int size       = version < 3 ?  2 : 3;
                               for (int i = firstIndex;
                                    (i + size) <= RinexUtils.LABEL_INDEX && parseInfo.typesObs.size() < parseInfo.nbTypes;
                                    i += increment) {
                                   final String type = RinexUtils.parseString(line, i, size);
                                   try {
                                       parseInfo.typesObs.add(ObservationType.valueOf(type));
                                   } catch (IllegalArgumentException iae) {
                                       throw new OrekitException(iae, OrekitMessages.UNKNOWN_RINEX_FREQUENCY,
                                                                 type, parseInfo.name, parseInfo.lineNumber);
                                   }
                               }

                               if (parseInfo.typesObs.size() == parseInfo.nbTypes) {
                                   // we have completed the list
                                   parseInfo.mapTypeObs.put(parseInfo.currentSystem, new ArrayList<>(parseInfo.typesObs));
                                   parseInfo.nbTypes = -1;
                                   parseInfo.typesObs.clear();
                               }

                           },
                           LineParser::headerNbTypesObs),

        /** Parser for center of mass. */
        CENTER_OF_MASS_XYZ(line -> RinexUtils.matchesLabel(line, "CENTER OF MASS: XYZ"),
                           (line, parseInfo) -> {
                               parseInfo.header.setCenterMass(new Vector3D(RinexUtils.parseDouble(line,  0, 14),
                                                                           RinexUtils.parseDouble(line, 14, 14),
                                                                           RinexUtils.parseDouble(line, 28, 14)));
                           },
                           LineParser::headerNext),

        /** Parser for unit of signal strength. */
        SIGNAL_STRENGTH_UNIT(line -> RinexUtils.matchesLabel(line, "SIGNAL STRENGTH UNIT"),
                             (line, parseInfo) -> parseInfo.header.setSignalStrengthUnit(RinexUtils.parseString(line, 0, 20)),
                             LineParser::headerNext),

        /** Parser for differential code bias corrections. */
        SYS_DCBS_APPLIED(line -> RinexUtils.matchesLabel(line, "SYS / DCBS APPLIED"),
                         (line, parseInfo) -> parseInfo.header.addAppliedDCBS(new AppliedDCBS(SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1)),
                                                                                              RinexUtils.parseString(line, 2, 17),
                                                                                              RinexUtils.parseString(line, 20, 40))),
                         LineParser::headerNext),

        /** Parser for phase center variations corrections. */
        SYS_PCVS_APPLIED(line -> RinexUtils.matchesLabel(line, "SYS / PCVS APPLIED"),
                         (line, parseInfo) -> parseInfo.header.addAppliedPCVS(new AppliedPCVS(SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1)),
                                                                                              RinexUtils.parseString(line, 2, 17),
                                                                                              RinexUtils.parseString(line, 20, 40))),
                         LineParser::headerNext),

        /** Parser for scale factor. */
        SYS_SCALE_FACTOR(line -> RinexUtils.matchesLabel(line, "SYS / SCALE FACTOR"),
                         (line, parseInfo) -> {

                             int scaleFactor = 1;
                             if (parseInfo.nbObsScaleFactor < 0) {
                                 // first line of scale factor
                                 parseInfo.currentSystem    = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1));
                                 scaleFactor                = RinexUtils.parseInt(line, 2, 4);
                                 parseInfo.nbObsScaleFactor = RinexUtils.parseInt(line, 8, 2);
                             }

                             if (parseInfo.nbObsScaleFactor == 0) {
                                 parseInfo.typesObsScaleFactor.addAll(parseInfo.mapTypeObs.get(parseInfo.currentSystem));
                             } else {
                                 for (int i = 11; i < RinexUtils.LABEL_INDEX && parseInfo.typesObsScaleFactor.size() < parseInfo.nbObsScaleFactor; i += 4) {
                                     parseInfo.typesObsScaleFactor.add(ObservationType.valueOf(RinexUtils.parseString(line, i, 3)));
                                 }
                             }

                             if (parseInfo.typesObsScaleFactor.size() >= parseInfo.nbObsScaleFactor) {
                                 // we have completed the list
                                 parseInfo.header.addScaleFactorCorrection(parseInfo.currentSystem,
                                                                           new ScaleFactorCorrection(scaleFactor,
                                                                                                     new ArrayList<>(parseInfo.typesObsScaleFactor)));
                                 parseInfo.nbObsScaleFactor = -1;
                                 parseInfo.typesObsScaleFactor.clear();
                             }

                         },
                         LineParser::headerNext),

        /** Parser for phase shift. */
        SYS_PHASE_SHIFT(line -> RinexUtils.matchesLabel(line, "SYS / PHASE SHIFT") ||
                                RinexUtils.matchesLabel(line, "SYS / PHASE SHIFTS"),
                        (line, parseInfo) -> {

                            if (parseInfo.phaseShiftNbSat < 0) {
                                // first line of phase shift
                                parseInfo.currentSystem     = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1));
                                final String to             = RinexUtils.parseString(line, 2, 3);
                                parseInfo.phaseShiftTypeObs = to.isEmpty() ? null : ObservationType.valueOf(to.length() < 3 ? "L" + to : to);
                                parseInfo.corrPhaseShift    = RinexUtils.parseDouble(line, 6, 8);
                                parseInfo.phaseShiftNbSat   = RinexUtils.parseInt(line, 16, 2);
                            }

                            for (int i = 19; i < RinexUtils.LABEL_INDEX && parseInfo.satPhaseShift.size() < parseInfo.phaseShiftNbSat; i += 4) {
                                final SatelliteSystem system = line.charAt(i) == ' ' ?
                                                               parseInfo.currentSystem :
                                                               SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, i, 1));
                                final int             prn    = RinexUtils.parseInt(line, i + 1, 2);
                                parseInfo.satPhaseShift.add(new SatInSystem(system, prn));
                            }

                            if (parseInfo.satPhaseShift.size() == parseInfo.phaseShiftNbSat) {
                                // we have completed the list
                                parseInfo.header.addPhaseShiftCorrection(new PhaseShiftCorrection(parseInfo.currentSystem,
                                                                                                  parseInfo.phaseShiftTypeObs,
                                                                                                  parseInfo.corrPhaseShift,
                                                                                                  new ArrayList<>(parseInfo.satPhaseShift)));
                                parseInfo.phaseShiftNbSat = -1;
                                parseInfo.satPhaseShift.clear();
                            }

                        },
                        LineParser::headerPhaseShift),

        /** Parser for GLONASS slot and frequency number. */
        GLONASS_SLOT_FRQ_NB(line -> RinexUtils.matchesLabel(line, "GLONASS SLOT / FRQ #"),
                            (line, parseInfo) -> {

                                if (parseInfo.nbGlonass < 0) {
                                    // first line of GLONASS satellite/frequency association
                                    parseInfo.nbGlonass = RinexUtils.parseInt(line, 0, 3);
                                }

                                for (int i = 4;
                                     i < RinexUtils.LABEL_INDEX && parseInfo.header.getGlonassChannels().size() < parseInfo.nbGlonass;
                                     i += 7) {
                                    final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, i, 1));
                                    final int             prn    = RinexUtils.parseInt(line, i + 1, 2);
                                    final int             k      = RinexUtils.parseInt(line, i + 4, 2);
                                    parseInfo.header.addGlonassChannel(new GlonassSatelliteChannel(new SatInSystem(system, prn), k));
                                }

                            },
                            LineParser::headerNext),

        /** Parser for GLONASS phase bias corrections. */
        GLONASS_COD_PHS_BIS(line -> RinexUtils.matchesLabel(line, "GLONASS COD/PHS/BIS"),
                            (line, parseInfo) -> {

                                // C1C signal
                                final String c1c = RinexUtils.parseString(line, 1, 3);
                                if (c1c.length() > 0) {
                                    parseInfo.header.setC1cCodePhaseBias(RinexUtils.parseDouble(line, 5, 8));
                                }

                                // C1P signal
                                final String c1p = RinexUtils.parseString(line, 14, 3);
                                if (c1p.length() > 0) {
                                    parseInfo.header.setC1pCodePhaseBias(RinexUtils.parseDouble(line, 18, 8));
                                }

                                // C2C signal
                                final String c2c = RinexUtils.parseString(line, 27, 3);
                                if (c2c.length() > 0) {
                                    parseInfo.header.setC2cCodePhaseBias(RinexUtils.parseDouble(line, 31, 8));
                                }

                                // C2P signal
                                final String c2p = RinexUtils.parseString(line, 40, 3);
                                if (c2p.length() > 0) {
                                    parseInfo.header.setC2pCodePhaseBias(RinexUtils.parseDouble(line, 44, 8));
                                }

                            },
                            LineParser::headerNext),

        /** Parser for observations scale factor. */
        OBS_SCALE_FACTOR(line -> RinexUtils.matchesLabel(line, "OBS SCALE FACTOR"),
                         (line, parseInfo) -> {
                             final int scaleFactor      = FastMath.max(1, RinexUtils.parseInt(line, 0,  6));
                             final int nbObsScaleFactor = RinexUtils.parseInt(line, 6, 6);
                             final List<ObservationType> types = new ArrayList<>(nbObsScaleFactor);
                             for (int i = 0; i < nbObsScaleFactor; i++) {
                                 types.add(ObservationType.valueOf(RinexUtils.parseString(line, 16 + (6 * i), 2)));
                             }
                             parseInfo.header.addScaleFactorCorrection(parseInfo.header.getSatelliteSystem(),
                                                                       new ScaleFactorCorrection(scaleFactor, types));
                         },
                         LineParser::headerNext),

        /** Parser for Rinex 2 data list of satellites. */
        RINEX_2_DATA_SAT_LIST(line -> true,
                              (line, parseInfo) -> {
                                  for (int index = 32; parseInfo.satObs.size() < parseInfo.nbSatObs && index < 68; index += 3) {
                                      // add one PRN to the list of observed satellites
                                      final SatelliteSystem system    = line.charAt(index) == ' ' ?
                                                                        parseInfo.header.getSatelliteSystem() :
                                                                        SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line,
                                                                                                                                    index,
                                                                                                                                    1));
                                      if (system != parseInfo.header.getSatelliteSystem() &&
                                          parseInfo.header.getSatelliteSystem() != SatelliteSystem.MIXED) {
                                          throw new OrekitException(OrekitMessages.INCONSISTENT_SATELLITE_SYSTEM,
                                                                    parseInfo.lineNumber, parseInfo.name,
                                                                    parseInfo.header.getSatelliteSystem(),
                                                                    system);
                                      }
                                      final int             prn       = RinexUtils.parseInt(line, index + 1, 2);
                                      final SatInSystem     satellite = new SatInSystem(system,
                                                                                        system == SatelliteSystem.SBAS ? prn + 100 : prn);
                                      parseInfo.satObs.add(satellite);
                                      // note that we *must* use parseInfo.header.getSatelliteSystem() as it was used to set up parseInfo.mapTypeObs
                                      // and it may be MIXED to be applied to all satellites systems
                                      final int nbObservables = parseInfo.mapTypeObs.get(parseInfo.header.getSatelliteSystem()).size();
                                      final int nbLines       = (nbObservables + MAX_OBS_PER_RINEX_2_LINE - 1) / MAX_OBS_PER_RINEX_2_LINE;
                                      parseInfo.nextObsStartLineNumber += nbLines;
                                  }
                              },
                              LineParser::first2),

        /** Parser for Rinex 2 data first line. */
        RINEX_2_DATA_FIRST(line -> true,
                           (line, parseInfo) -> {

                               // flag
                               final int eventFlag = RinexUtils.parseInt(line, 28, 1);

                               // number of sats
                               parseInfo.nbSatObs   = RinexUtils.parseInt(line, 29, 3);
                               final int nbLinesSat = (parseInfo.nbSatObs + MAX_SAT_PER_RINEX_2_LINE - 1) / MAX_SAT_PER_RINEX_2_LINE;

                               if (eventFlag < 2) {
                                   // regular observation
                                   parseInfo.specialRecord = false;
                                   parseInfo.cycleSlip     = false;
                                   if (parseInfo.nbSat != -1 && parseInfo.nbSatObs > parseInfo.nbSat) {
                                       // we check that the number of Sat in the observation is consistent
                                       throw new OrekitException(OrekitMessages.INCONSISTENT_NUMBER_OF_SATS,
                                                                 parseInfo.lineNumber, parseInfo.name,
                                                                 parseInfo.nbSatObs, parseInfo.nbSat);
                                   }
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + nbLinesSat;

                                   // read the Receiver Clock offset, if present
                                   parseInfo.rcvrClkOffset = RinexUtils.parseDouble(line, 68, 12);
                                   if (Double.isNaN(parseInfo.rcvrClkOffset)) {
                                       parseInfo.rcvrClkOffset = 0.0;
                                   }

                               } else if (eventFlag < 6) {
                                   // moving antenna / new site occupation / header information / external event
                                   // here, number of sats means number of lines to skip
                                   parseInfo.specialRecord = true;
                                   parseInfo.cycleSlip     = false;
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + parseInfo.nbSatObs + 1;
                               } else if (eventFlag == 6) {
                                   // cycle slip, we will ignore it during observations parsing
                                   parseInfo.specialRecord = false;
                                   parseInfo.cycleSlip     = true;
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + nbLinesSat;
                               } else {
                                   // unknown event flag
                                   throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                             parseInfo.lineNumber, parseInfo.name, line);
                               }

                               // parse the list of satellites observed
                               parseInfo.satObs.clear();
                               if (!parseInfo.specialRecord) {

                                   // observations epoch
                                   final int yy = RinexUtils.parseInt(line, 1, 2);
                                   parseInfo.tObs = new AbsoluteDate(yy >= 80 ? (yy + 1900) : (yy + 2000),
                                                                     RinexUtils.parseInt(line,  4, 2),
                                                                     RinexUtils.parseInt(line,  7, 2),
                                                                     RinexUtils.parseInt(line, 10, 2),
                                                                     RinexUtils.parseInt(line, 13, 2),
                                                                     RinexUtils.parseDouble(line, 15, 11),
                                                                     parseInfo.timeScale);

                                   // satellites list
                                   RINEX_2_DATA_SAT_LIST.parsingMethod.parse(line, parseInfo);

                               }

                               // prepare handling of observations for current epoch
                               parseInfo.indexObsSat = 0;
                               parseInfo.observations.clear();

                           },
                           LineParser::first2),

        /** Parser for Rinex 2 special record. */
        RINEX_2_IGNORED_SPECIAL_RECORD(line -> true,
                           (line, parseInfo) -> {
                               // nothing to do
                           },
                           LineParser::ignore2),

        /** Parser for Rinex 2 observation line. */
        RINEX_2_OBSERVATION(line -> true,
                            (line, parseInfo) -> {
                                final List<ObservationType> types = parseInfo.mapTypeObs.get(parseInfo.header.getSatelliteSystem());
                                for (int index = 0;
                                     parseInfo.observations.size() < types.size() && index < 80;
                                     index += 16) {
                                    final ObservationData observationData;
                                    if (parseInfo.cycleSlip) {
                                        // we are in a cycle slip data block (eventFlag = 6), we just ignore everything
                                        observationData = null;
                                    } else {
                                        // this is a regular observation line
                                        final ObservationType type = types.get(parseInfo.observations.size());
                                        double scaling = 1.0;
                                        for (final ScaleFactorCorrection scaleFactorCorrection :
                                            parseInfo.header.getScaleFactorCorrections(parseInfo.currentSystem)) {
                                            // check if the next Observation Type to read needs to be scaled
                                            if (scaleFactorCorrection.getTypesObsScaled().contains(type)) {
                                                scaling = 1.0 / scaleFactorCorrection.getCorrection();
                                            }
                                        }
                                        observationData = new ObservationData(type,
                                                                              scaling * RinexUtils.parseDouble(line, index, 14),
                                                                              RinexUtils.parseInt(line, index + 14, 1),
                                                                              RinexUtils.parseInt(line, index + 15, 1));
                                    }
                                    parseInfo.observations.add(observationData);
                                }

                                if (parseInfo.observations.size() == types.size()) {
                                    // we have finished handling observations/cycle slips for one satellite
                                    if (!parseInfo.cycleSlip) {
                                        parseInfo.observationDataSets.add(new ObservationDataSet(parseInfo.header,
                                                                                                 parseInfo.satObs.get(parseInfo.indexObsSat),
                                                                                                 parseInfo.tObs,
                                                                                                 parseInfo.rcvrClkOffset,
                                                                                                 new ArrayList<>(parseInfo.observations)));
                                    }
                                    parseInfo.indexObsSat++;
                                    parseInfo.observations.clear();
                                }

                            },
                            LineParser::observation2),

        /** Parser for Rinex 3 observation line. */
        RINEX_3_OBSERVATION(line -> true,
                            (line, parseInfo) -> {
                                final SatelliteSystem system = SatelliteSystem.parseSatelliteSystem(RinexUtils.parseString(line, 0, 1));
                                final int             prn    = RinexUtils.parseInt(line, 1, 2);
                                final SatInSystem sat = new SatInSystem(system,
                                                                        system == SatelliteSystem.SBAS ?
                                                                        prn + 100 :
                                                                        (system == SatelliteSystem.QZSS ? prn + 192 : prn));
                                final List<ObservationType> types = parseInfo.mapTypeObs.get(sat.getSystem());
                                for (int index = 3;
                                     parseInfo.observations.size() < types.size();
                                     index += 16) {
                                    final ObservationData observationData;
                                    if (parseInfo.cycleSlip) {
                                        // we are in a cycle slip data block (eventFlag = 6), we just ignore everything
                                        observationData = null;
                                    } else {
                                        // this is a regular observation line
                                        final ObservationType type = types.get(parseInfo.observations.size());
                                        double scaling = 1.0;
                                        for (final ScaleFactorCorrection scaleFactorCorrection :
                                            parseInfo.header.getScaleFactorCorrections(sat.getSystem())) {
                                            // check if the next Observation Type to read needs to be scaled
                                            if (scaleFactorCorrection.getTypesObsScaled().contains(type)) {
                                                scaling = 1.0 / scaleFactorCorrection.getCorrection();
                                            }
                                        }
                                        observationData = new ObservationData(type,
                                                                              scaling * RinexUtils.parseDouble(line, index, 14),
                                                                              RinexUtils.parseInt(line, index + 14, 1),
                                                                              RinexUtils.parseInt(line, index + 15, 1));
                                    }
                                    parseInfo.observations.add(observationData);
                                }

                                if (!parseInfo.cycleSlip) {
                                    parseInfo.observationDataSets.add(new ObservationDataSet(parseInfo.header,
                                                                                             sat,
                                                                                             parseInfo.tObs,
                                                                                             parseInfo.rcvrClkOffset,
                                                                                             new ArrayList<>(parseInfo.observations)));
                                }
                                parseInfo.observations.clear();

                            },
                            LineParser::observation3),

        /** Parser for Rinex 3 data first line. */
        RINEX_3_DATA_FIRST(line -> line.startsWith(">"),
                           (line, parseInfo) -> {

                               // flag
                               final int eventFlag = RinexUtils.parseInt(line, 31, 1);

                               // number of sats
                               parseInfo.nbSatObs   = RinexUtils.parseInt(line, 32, 3);

                               if (eventFlag < 2) {
                                   // regular observation
                                   parseInfo.specialRecord = false;
                                   parseInfo.cycleSlip     = false;
                                   if (parseInfo.nbSat != -1 && parseInfo.nbSatObs > parseInfo.nbSat) {
                                       // we check that the number of Sat in the observation is consistent
                                       throw new OrekitException(OrekitMessages.INCONSISTENT_NUMBER_OF_SATS,
                                                                 parseInfo.lineNumber, parseInfo.name,
                                                                 parseInfo.nbSatObs, parseInfo.nbSat);
                                   }
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + parseInfo.nbSatObs + 1;

                                   // read the Receiver Clock offset, if present
                                   parseInfo.rcvrClkOffset = RinexUtils.parseDouble(line, 41, 15);
                                   if (Double.isNaN(parseInfo.rcvrClkOffset)) {
                                       parseInfo.rcvrClkOffset = 0.0;
                                   }

                               } else if (eventFlag < 6) {
                                   // moving antenna / new site occupation / header information / external event
                                   // here, number of sats means number of lines to skip
                                   parseInfo.specialRecord = true;
                                   parseInfo.cycleSlip     = false;
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + parseInfo.nbSatObs + 1;
                               } else if (eventFlag == 6) {
                                   // cycle slip, we will ignore it during observations parsing
                                   parseInfo.specialRecord = false;
                                   parseInfo.cycleSlip     = true;
                                   parseInfo.nextObsStartLineNumber = parseInfo.lineNumber + parseInfo.nbSatObs + 1;
                               } else {
                                   // unknown event flag
                                   throw new OrekitException(OrekitMessages.UNABLE_TO_PARSE_LINE_IN_FILE,
                                                             parseInfo.lineNumber, parseInfo.name, line);
                               }

                               // parse the list of satellites observed
                               parseInfo.satObs.clear();
                               if (!parseInfo.specialRecord) {

                                   // observations epoch
                                   parseInfo.tObs = new AbsoluteDate(RinexUtils.parseInt(line,  2, 4),
                                                                     RinexUtils.parseInt(line,  7, 2),
                                                                     RinexUtils.parseInt(line, 10, 2),
                                                                     RinexUtils.parseInt(line, 13, 2),
                                                                     RinexUtils.parseInt(line, 16, 2),
                                                                     RinexUtils.parseDouble(line, 18, 11),
                                                                     parseInfo.timeScale);

                               }

                               // prepare handling of observations for current epoch
                               parseInfo.observations.clear();

                           },
                           parseInfo -> Stream.of(RINEX_3_OBSERVATION)),

        /** Parser for the end of header. */
        HEADER_END(line -> RinexUtils.matchesLabel(line, "END OF HEADER"),
                   (line, parseInfo) -> {

                       parseInfo.headerCompleted = true;

                       // get rinex format version
                       final double version = parseInfo.header.getFormatVersion();

                       // check mandatory header fields
                       if (version < 3) {
                           if (parseInfo.header.getMarkerName()                  == null ||
                               parseInfo.header.getObserverName()                == null ||
                               parseInfo.header.getReceiverNumber()              == null ||
                               parseInfo.header.getAntennaNumber()               == null ||
                               parseInfo.header.getTFirstObs()                   == null ||
                               version < 2.20 && parseInfo.header.getApproxPos() == null ||
                               version < 2.20 && Double.isNaN(parseInfo.header.getAntennaHeight()) ||
                               parseInfo.mapTypeObs.isEmpty()) {
                               throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, parseInfo.name);
                           }

                       } else {
                           if (parseInfo.header.getMarkerName()           == null ||
                                           parseInfo.header.getObserverName()         == null ||
                                           parseInfo.header.getReceiverNumber()       == null ||
                                           parseInfo.header.getAntennaNumber()        == null ||
                                           Double.isNaN(parseInfo.header.getAntennaHeight())  ||
                                           parseInfo.header.getTFirstObs()            == null ||
                                           parseInfo.mapTypeObs.isEmpty()                     ||
                                           version >= 3.01 && parseInfo.header.getPhaseShiftCorrections().isEmpty()) {
                               throw new OrekitException(OrekitMessages.INCOMPLETE_HEADER, parseInfo.name);
                           }
                       }
                   },
                   LineParser::headerEndNext);


        /** Predicate for identifying lines that can be parsed. */
        private final Predicate<String> canHandle;

        /** Parsing method. */
        private final ParsingMethod parsingMethod;

        /** Provider for next line parsers. */
        private final Function<ParseInfo, Stream<LineParser>> allowedNextProvider;

        /** Simple constructor.
         * @param canHandle predicate for identifying lines that can be parsed
         * @param parsingMethod parsing method
         * @param allowedNextProvider supplier for allowed parsers for next line
         */
        LineParser(final Predicate<String> canHandle, final ParsingMethod parsingMethod,
                   final Function<ParseInfo, Stream<LineParser>> allowedNextProvider) {
            this.canHandle           = canHandle;
            this.parsingMethod       = parsingMethod;
            this.allowedNextProvider = allowedNextProvider;
        }

        /** Get the allowed parsers for next lines while parsing comments.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> commentNext(final ParseInfo parseInfo) {
            return parseInfo.headerCompleted ? headerEndNext(parseInfo) : headerNext(parseInfo);
        }

        /** Get the allowed parsers for next lines while parsing Rinex header.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> headerNext(final ParseInfo parseInfo) {
            if (parseInfo.header.getFormatVersion() < 3) {
                return Stream.of(COMMENT, HEADER_PROGRAM, MARKER_NAME, MARKER_NUMBER, MARKER_TYPE, OBSERVER_AGENCY,
                                 REC_NB_TYPE_VERS, ANT_NB_TYPE, APPROX_POSITION_XYZ, ANTENNA_DELTA_H_E_N,
                                 ANTENNA_DELTA_X_Y_Z, ANTENNA_B_SIGHT_XYZ, CENTER_OF_MASS_XYZ, NB_OF_SATELLITES,
                                 WAVELENGTH_FACT_L1_2, RCV_CLOCK_OFFS_APPL, INTERVAL, TIME_OF_FIRST_OBS, TIME_OF_LAST_OBS,
                                 LEAP_SECONDS, PRN_NB_OF_OBS, TYPES_OF_OBSERV, OBS_SCALE_FACTOR, HEADER_END);
            } else {
                return Stream.of(COMMENT, HEADER_PROGRAM, MARKER_NAME, MARKER_NUMBER, MARKER_TYPE, OBSERVER_AGENCY,
                                 REC_NB_TYPE_VERS, ANT_NB_TYPE, APPROX_POSITION_XYZ, ANTENNA_DELTA_H_E_N,
                                 ANTENNA_DELTA_X_Y_Z, ANTENNA_PHASECENTER, ANTENNA_B_SIGHT_XYZ, ANTENNA_ZERODIR_AZI,
                                 ANTENNA_ZERODIR_XYZ, CENTER_OF_MASS_XYZ, NB_OF_SATELLITES, RCV_CLOCK_OFFS_APPL,
                                 INTERVAL, TIME_OF_FIRST_OBS, TIME_OF_LAST_OBS, LEAP_SECONDS, PRN_NB_OF_OBS,
                                 TYPES_OF_OBSERV, SIGNAL_STRENGTH_UNIT, SYS_DCBS_APPLIED,
                                 SYS_PCVS_APPLIED, SYS_SCALE_FACTOR, SYS_PHASE_SHIFT,
                                 GLONASS_SLOT_FRQ_NB, GLONASS_COD_PHS_BIS, HEADER_END);
            }
        }

        /** Get the allowed parsers for next lines while parsing header end.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> headerEndNext(final ParseInfo parseInfo) {
            return Stream.of(parseInfo.header.getFormatVersion() < 3 ?
                             RINEX_2_DATA_FIRST :
                             RINEX_3_DATA_FIRST);
        }

        /** Get the allowed parsers for next lines while parsing types of observations.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> headerNbTypesObs(final ParseInfo parseInfo) {
            if (parseInfo.typesObs.size() < parseInfo.nbTypes) {
                return Stream.of(COMMENT, TYPES_OF_OBSERV);
            } else {
                return headerNext(parseInfo);
            }
        }

        /** Get the allowed parsers for next lines while parsing phase shifts.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> headerPhaseShift(final ParseInfo parseInfo) {
            if (parseInfo.satPhaseShift.size() < parseInfo.phaseShiftNbSat) {
                return Stream.of(COMMENT, SYS_PHASE_SHIFT);
            } else {
                return headerNext(parseInfo);
            }
        }

        /** Get the allowed parsers for next lines while parsing Rinex 2 observations first lines.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> first2(final ParseInfo parseInfo) {
            if (parseInfo.specialRecord) {
                return Stream.of(RINEX_2_IGNORED_SPECIAL_RECORD);
            } else if (parseInfo.satObs.size() < parseInfo.nbSatObs) {
                return Stream.of(RINEX_2_DATA_SAT_LIST);
            } else {
                return Stream.of(RINEX_2_OBSERVATION);
            }
        }

        /** Get the allowed parsers for next lines while parsing Rinex 2 ignored special records.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> ignore2(final ParseInfo parseInfo) {
            if (parseInfo.lineNumber < parseInfo.nextObsStartLineNumber) {
                return Stream.of(RINEX_2_IGNORED_SPECIAL_RECORD);
            } else {
                return Stream.of(COMMENT, RINEX_2_DATA_FIRST);
            }
        }

        /** Get the allowed parsers for next lines while parsing Rinex 2 observations per se.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> observation2(final ParseInfo parseInfo) {
            if (parseInfo.lineNumber < parseInfo.nextObsStartLineNumber) {
                return Stream.of(RINEX_2_OBSERVATION);
            } else {
                return Stream.of(COMMENT, RINEX_2_DATA_FIRST);
            }
        }

        /** Get the allowed parsers for next lines while parsing Rinex 3 observations.
         * @param parseInfo holder for transient data
         * @return allowed parsers for next line
         */
        private static Stream<LineParser> observation3(final ParseInfo parseInfo) {
            if (parseInfo.lineNumber < parseInfo.nextObsStartLineNumber) {
                return Stream.of(RINEX_3_OBSERVATION);
            } else {
                return Stream.of(COMMENT, RINEX_3_DATA_FIRST);
            }
        }

    }

    /** Parsing method. */
    @FunctionalInterface
    private interface ParsingMethod {
        /** Parse a line.
         * @param line line to parse
         * @param parseInfo holder for transient data
         */
        void parse(String line, ParseInfo parseInfo);
    }

}
