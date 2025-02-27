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
package org.orekit.files.ccsds.definitions;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.Precision;
import org.orekit.annotation.DefaultDataContext;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.files.ccsds.ndm.odm.ocm.Ocm;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.EquinoctialOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.orekit.utils.units.Unit;

/** Orbit element set type used in CCSDS {@link Ocm Orbit Comprehensive Messages}.
 * @see <a href="https://sanaregistry.org/r/orbital_elements">SANA registry for orbital elements</a>
 * @author Luc Maisonobe
 * @since 11.0
 */
public enum ElementsType {

    // CHECKSTYLE: stop MultipleStringLiterals check

    /** Spherical 6-element set (α,δ,β,A,r,v). */
    ADBARV("Spherical 6-element set (α,δ,β,A,r,v)",
           "°", "°", "°", "°", "km", "km/s"),

    /** Cartesian 3-element position (X, Y, Z). */
    CARTP("Cartesian 3-element position (X, Y, Z)",
          "km", "km", "km") {
        /** {@inheritDoc} */
        @Override
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            return new TimeStampedPVCoordinates(date,
                                                new Vector3D(elements[0], elements[1], elements[2]),
                                                Vector3D.ZERO,
                                                Vector3D.ZERO);
        }
    },

    /** Cartesian 6-element position and velocity (X, Y, Z, XD, YD, ZD). */
    CARTPV("Cartesian 6-element position and velocity (X, Y, Z, XD, YD, ZD)",
           "km", "km", "km", "km/s", "km/s", "km/s") {
        /** {@inheritDoc} */
        @Override
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            return new TimeStampedPVCoordinates(date,
                                                new Vector3D(elements[0], elements[1], elements[2]),
                                                new Vector3D(elements[3], elements[4], elements[5]),
                                                Vector3D.ZERO);
        }
    },

    /** Cartesian 9-element position, velocity and acceleration (X, Y, Z, XD, YD, ZD, XDD, YDD, ZDD). */
    CARTPVA("Cartesian 9-element position, velocity and acceleration (X, Y, Z, XD, YD, ZD, XDD, YDD, ZDD)",
            "km", "km", "km", "km/s", "km/s", "km/s", "km/s²", "km/s²", "km/s²") {
        /** {@inheritDoc} */
        @Override
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            return new TimeStampedPVCoordinates(date,
                                                new Vector3D(elements[0], elements[1], elements[2]),
                                                new Vector3D(elements[3], elements[4], elements[5]),
                                                new Vector3D(elements[6], elements[7], elements[8]));
        }
    },

    /** Delaunay elements (L, G, H, l, g, h). */
    DELAUNAY("Delaunay elements (L, G, H, l, g, h)",
             "km²/s", "km²/s", "km²/s", "°", "°", "°"),

    /** Modified Delaunay elements (Lm, Gm, Hm, lm, gm, hm). */
    DELAUNAYMOD("Delaunay elements (Lm, Gm, Hm, lm, gm, hm)",
                "√km", "√km", "√km", "°", "°", "°"),

    /** 12 elements eigenvalue/eigenvectors (EigMaj, EigMed, EigMin, EigVecMaj, EigVecMed, EigVecMin). */
    EIGVAL3EIGVEC3("12 elements eigenvalue/eigenvectors (EigMaj, EigMed, EigMin, EigVecMaj, EigVecMed, EigVecMin)",
                   "km", "km", "km", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a", "n/a"),

    /** Equinoctial elements (a, af, ag, L=M+ω+frΩ, χ, ψ, fr). */
    EQUINOCTIAL("Equinoctial elements (a, af, ag, L=M+ω+frΩ, χ, ψ, fr)",
                "km", "n/a", "n/a", "°", "n/a", "n/a", "n/a") {
        /** {@inheritDoc} */
        @Override
        @DefaultDataContext
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            if (elements[6] < 0) {
                // retrograde
                throw new OrekitException(OrekitMessages.CCSDS_UNSUPPORTED_RETROGRADE_EQUINOCTIAL,
                                          EQUINOCTIAL.name());
            }
            return new EquinoctialOrbit(elements[0], elements[1], elements[2],
                                        elements[5], elements[4], // BEWARE! the inversion here is intentional
                                        elements[3], PositionAngle.MEAN,
                                        FramesFactory.getGCRF(), date, mu).
                            getPVCoordinates();
        }
    },

    /** Modified equinoctial elements (p=a(1−e²), af, ag, L'=υ+ω+frΩ, χ, ψ, fr). */
    EQUINOCTIALMOD("Modified equinoctial elements (p=a(1−e²), af, ag, L'=υ+ω+frΩ, χ, ψ, fr)",
                   "km", "n/a", "n/a", "°", "n/a", "n/a", "n/a") {
        /** {@inheritDoc} */
        @Override
        @DefaultDataContext
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            if (elements[6] < 0) {
                // retrograde
                throw new OrekitException(OrekitMessages.CCSDS_UNSUPPORTED_RETROGRADE_EQUINOCTIAL,
                                          EQUINOCTIALMOD.name());
            }
            final double oMe2 = 1.0 - elements[1] * elements[1] - elements[2] * elements[2];
            return new EquinoctialOrbit(elements[0] / oMe2, elements[1], elements[2],
                                        elements[5], elements[4], // BEWARE! the inversion here is intentional
                                        elements[3], PositionAngle.TRUE,
                                        FramesFactory.getGCRF(), date, mu).
                            getPVCoordinates();
        }
    },

    /** Geodetic elements (λ, ΦGD, β, A, h, vre). */
    GEODETIC("Geodetic elements (λ, ΦGD, β, A, h, vre)",
             "°", "°", "°", "°", "km", "km/s"),

    /** Keplerian 6-element classical set (a, e, i, Ω, ω, ν). */
    KEPLERIAN("Keplerian 6-elemnt classical set (a, e, i, Ω, ω, ν)",
              "km", "n/a", "°", "°", "°", "°") {
        /** {@inheritDoc} */
        @Override
        @DefaultDataContext
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            return new KeplerianOrbit(elements[0], elements[1], elements[2],
                                      elements[4], elements[3], // BEWARE! the inversion here is intentional
                                      elements[5], PositionAngle.TRUE,
                                      FramesFactory.getGCRF(), date, mu).
                   getPVCoordinates();
        }
    },

    /** Keplerian 6-element classical set (a, e, i, Ω, ω, M). */
    KEPLERIANMEAN("Keplerian 6-elemnt classical set (a, e, i, Ω, ω, M)",
                  "km", "n/a", "°", "°", "°", "°") {
        /** {@inheritDoc} */
        @Override
        @DefaultDataContext
        public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
            return new KeplerianOrbit(elements[0], elements[1], elements[2],
                                      elements[4], elements[3], // BEWARE! the inversion here is intentional
                                      elements[5], PositionAngle.MEAN,
                                      FramesFactory.getGCRF(), date, mu).
                   getPVCoordinates();
        }
    },

    /** Modified spherical 6-element set (λ, δ, β, A, r, v). */
    LDBARV("Modified spherical 6-element set (λ, δ, β, A, r, v)",
           "°", "°", "°", "°", "km", "km/s"),

    /** Geosynchronous on-station tailored set (a, ex, ey, ix, iy, λ). */
    ONSTATION("Geosynchronous on-station tailored set (a, ex, ey, ix, iy, λ)",
              "km", "n/a", "n/a", "n/a", "n/a", "°"),

    /** Canonical counterpart of equinoctial 6-element set (λM=M+ω+Ω, gp, hp, Lp, Gp, Hp). */
    POINCARE("Canonical counterpart of equinoctial 6-element set (λM=M+ω+Ω, gp, hp, Lp, Gp, Hp)",
             "°", "km/√s", "km/√s", "km²/s", "km/√s", "km/√s");

    // CHECKSTYLE: resume MultipleStringLiterals check

    /** Description. */
    private final String description;

    /** Elements units. */
    private final List<Unit> units;

    /** Simple constructor.
     * @param description description
     * @param unitsSpecifications elements units specifications
     */
    ElementsType(final String description, final String... unitsSpecifications) {
        this.description = description;
        this.units       = Stream.of(unitsSpecifications).
                           map(s -> Unit.parse(s)).
                           collect(Collectors.toList());
    }

    /** Get the elements units.
     * @return elements units
     */
    public List<Unit> getUnits() {
        return units;
    }

    /** Check if parsed units are compatible with elements types.
     * @param parsedUnits units to check
     */
    public void checkUnits(final List<Unit> parsedUnits) {
        if (parsedUnits.size() != units.size()) {
            throw new OrekitException(OrekitMessages.CCSDS_ELEMENT_SET_WRONG_NB_COMPONENTS,
                                      name(), toString(), units.size());
        }
        for (int i = 0; i < units.size(); ++i) {
            if (!(units.get(i).sameDimension(parsedUnits.get(i)) &&
                  Precision.equals(units.get(i).getScale(), parsedUnits.get(i).getScale(), 1))) {
                throw new OrekitException(OrekitMessages.INCOMPATIBLE_UNITS,
                                          units.get(i).getName(),
                                          parsedUnits.get(i).getName());
            }
        }
    }

    /** Convert to Cartesian coordinates.
     * @param date elements date
     * @param elements elements values
     * @param mu gravitational parameter in m³/s²
     * @return Cartesian coordinates
     */
    public TimeStampedPVCoordinates toCartesian(final AbsoluteDate date, final double[] elements, final double mu) {
        throw new OrekitException(OrekitMessages.CCSDS_UNSUPPORTED_ELEMENT_SET_TYPE, name(), toString());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return description;
    }

}
