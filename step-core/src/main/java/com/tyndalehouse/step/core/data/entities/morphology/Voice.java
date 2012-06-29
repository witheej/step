/*******************************************************************************
 * Copyright (c) 2012, Directors of the Tyndale STEP Project
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * Neither the name of the Tyndale House, Cambridge (www.TyndaleHouse.com)  
 * nor the names of its contributors may be used to endorse or promote 
 * products derived from this software without specific prior written 
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.tyndalehouse.step.core.data.entities.morphology;

import static com.tyndalehouse.step.core.utils.EnumUtils.getReverseMap;

import java.util.Map;

import com.tyndalehouse.step.core.models.HasCsvValueName;

/**
 * Voice of the word
 * 
 * @author chrisburrell
 * 
 */
// CHECKSTYLE:OFF
public enum Voice implements HasCsvValueName {
    ACTIVE("Active"),
    IMPERSONAL_ACTIVE("Impersonal active", "Impersonal Active"),
    MIDDLE_OR_PASSIVE("Either middle or passive", "Middle or Passive"),
    PASSIVE_DEPONENT("Passive depOnent", "Passive Deponent"),
    PASSIVE("Passive"),
    MIDDLE_OR_PASSIVE_DEPONENT("middle or Passive depoNent", "Middle or Passive Deponent"),
    MIDDLE("Middle"),
    MIDDLE_DEPONENT("middle Deponent", "Middle Deponent"),
    INDEFINITE_VOICE("Indefinite voice");

    private static Map<String, Voice> values = getReverseMap(values());
    private String csvValueName;
    private String displayName;

    Voice(final String csvValueName) {
        this(csvValueName, null);
    }

    Voice(final String csvValueName, final String displayName) {
        this.csvValueName = csvValueName;
        this.displayName = displayName;
    }

    /**
     * @return the displayName
     */
    public String getCsvValueName() {
        return this.csvValueName;
    }

    public static Voice resolveByCsvValueName(final String csvValueName) {
        return values.get(csvValueName);
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        if (this.displayName != null) {
            return this.displayName;
        }
        return this.csvValueName;
    }

    @Override
    public String toString() {
        return getCsvValueName();
    }
}