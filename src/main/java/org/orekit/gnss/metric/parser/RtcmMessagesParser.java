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
package org.orekit.gnss.metric.parser;

import java.util.List;

/** Parser for RTCM encoded messages.
 * @author Luc Maisonobe
 * @author Bryan Cazabonne
 * @since 11.0
 */
public class RtcmMessagesParser extends MessagesParser {

    /**
     * Constructor.
     * @param messages list of needed messages
     */
    public RtcmMessagesParser(final List<Integer> messages) {
        super(messages);
    }

    /** {@inheritDoc} */
    @Override
    protected String parseMessageNumber(final EncodedMessage message) {
        // RTCM Message number
        return RtcmDataField.DF002.stringValue(message, 0);
    }

    /** {@inheritDoc} */
    @Override
    protected MessageType getMessageType(final String messageNumber) {
        return RtcmMessageType.getMessageType(messageNumber);
    }

}
