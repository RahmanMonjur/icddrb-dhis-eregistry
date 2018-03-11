/*
 *  Copyright (c) 2016, University of Oslo
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *  * list of conditions and the following disclaimer.
 *  *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  * Neither the name of the HISP project nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.icddrb.dhis.android.eregistry.fragments.programoverview;

import android.content.Context;

import org.icddrb.dhis.android.sdk.controllers.metadata.MetaDataController;
import org.icddrb.dhis.android.sdk.controllers.tracker.TrackerController;
import org.icddrb.dhis.android.sdk.persistence.loaders.Query;
import org.icddrb.dhis.android.sdk.persistence.models.Enrollment;
import org.icddrb.dhis.android.sdk.persistence.models.Event;
import org.icddrb.dhis.android.sdk.persistence.models.Program;
import org.icddrb.dhis.android.sdk.persistence.models.ProgramIndicator;
import org.icddrb.dhis.android.sdk.persistence.models.ProgramStage;
import org.icddrb.dhis.android.sdk.persistence.models.TrackedEntityAttribute;
import org.icddrb.dhis.android.sdk.persistence.models.TrackedEntityAttributeValue;
import org.icddrb.dhis.android.sdk.persistence.models.TrackedEntityInstance;
import org.icddrb.dhis.android.sdk.persistence.models.UserAccount;
import org.icddrb.dhis.android.sdk.ui.adapters.rows.dataentry.IndicatorRow;
import org.icddrb.dhis.android.sdk.utils.Utils;
import org.icddrb.dhis.android.sdk.utils.comparators.EventDateComparator;
import org.icddrb.dhis.android.sdk.utils.services.ProgramIndicatorService;
import org.icddrb.dhis.android.eregistry.ui.rows.programoverview.ProgramStageEventRow;
import org.icddrb.dhis.android.eregistry.ui.rows.programoverview.ProgramStageLabelRow;
import org.icddrb.dhis.android.eregistry.ui.rows.programoverview.ProgramStageRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

class ProgramOverviewFragmentQuery implements Query<ProgramOverviewFragmentForm> {

    public static final String CLASS_TAG = ProgramOverviewFragmentQuery.class.getSimpleName();

    private final String mProgramId;
    private final long mTrackedEntityInstanceId;

    public ProgramOverviewFragmentQuery(String programId, long trackedEntityInstanceId) {
        mProgramId = programId;
        mTrackedEntityInstanceId = trackedEntityInstanceId;
    }

    @Override
    public ProgramOverviewFragmentForm query(Context context) {
        ProgramOverviewFragmentForm programOverviewFragmentForm = new ProgramOverviewFragmentForm();
        programOverviewFragmentForm.setProgramIndicatorRows(new LinkedHashMap<ProgramIndicator, IndicatorRow>());
        Program program = MetaDataController.getProgram(mProgramId);
        TrackedEntityInstance trackedEntityInstance = TrackerController.getTrackedEntityInstance(mTrackedEntityInstanceId);

        programOverviewFragmentForm.setProgram(program);
        programOverviewFragmentForm.setTrackedEntityInstance(trackedEntityInstance);
        programOverviewFragmentForm.setDateOfEnrollmentLabel(program.getEnrollmentDateLabel());
        programOverviewFragmentForm.setIncidentDateLabel(program.getIncidentDateLabel());

        if(trackedEntityInstance == null) {
            return programOverviewFragmentForm;
        }
        List<Enrollment> enrollments = TrackerController.getEnrollments(mProgramId, trackedEntityInstance);
        Enrollment activeEnrollment = null;
        if(enrollments!=null) {
            for(Enrollment enrollment: enrollments) {
                if(enrollment.getStatus().equals(Enrollment.ACTIVE)) {
                    activeEnrollment = enrollment;
                }
            }
        }
        if (activeEnrollment==null) {
            return programOverviewFragmentForm;
        }

        programOverviewFragmentForm.setEnrollment(activeEnrollment);
        programOverviewFragmentForm.setDateOfEnrollmentValue(Utils.removeTimeFromDateString(activeEnrollment.getEnrollmentDate()));
        programOverviewFragmentForm.setIncidentDateValue(Utils.removeTimeFromDateString(activeEnrollment.getIncidentDate()));

        // Norway
        List<TrackedEntityAttributeValue> trackedEntityAttributeValues =
                TrackerController.getTrackedEntityAttributeValues(trackedEntityInstance.getLocalId());
        if(trackedEntityAttributeValues!=null) {
            for(TrackedEntityAttributeValue a : trackedEntityAttributeValues) {
                TrackedEntityAttribute e = MetaDataController.getTrackedEntityAttribute(a.getTrackedEntityAttributeId());
                // System.out.println("Norway - Label: " + ((e==null) ? "null" : e.getDisplayName()) + " Value: " + a.getValue());

                if (e != null) {
                    switch (e.getName()) {
                        case "Full name":
                            programOverviewFragmentForm.setAttribute1Label(e.getDisplayName());
                            programOverviewFragmentForm.setAttribute1Value(a.getValue());
                            break;
                        case "FWA Name":
                            programOverviewFragmentForm.setAttribute2Label(e.getDisplayName());
                            programOverviewFragmentForm.setAttribute2Value(a.getValue());
                            break;
                        case "Village name (English)":
                            programOverviewFragmentForm.setAttribute3Label(e.getDisplayName());
                            programOverviewFragmentForm.setAttribute3Value(a.getValue());
                            break;
                    }
                }
            }

            /*if(trackedEntityAttributeValues.size() > 0) {
                programOverviewFragmentForm.setAttribute1Label(MetaDataController.
                        getTrackedEntityAttribute(trackedEntityAttributeValues.get(0).getTrackedEntityAttributeId()).
                        getName());
                programOverviewFragmentForm.setAttribute1Value(trackedEntityAttributeValues.get(0).getValue());
            }
            if(trackedEntityAttributeValues.size() > 1) {
                programOverviewFragmentForm.setAttribute2Label(MetaDataController.
                        getTrackedEntityAttribute(trackedEntityAttributeValues.get(1).getTrackedEntityAttributeId()).
                        getName());
                programOverviewFragmentForm.setAttribute2Value(trackedEntityAttributeValues.get(1).getValue());
            }*/
        }

        List<ProgramStageRow> programStageRows = getProgramStageRows(activeEnrollment);
        programOverviewFragmentForm.setProgramStageRows(programStageRows);

        List<ProgramIndicator> programIndicators = programOverviewFragmentForm.getProgram().getProgramIndicators();
        if(programIndicators != null ) {
            for(ProgramIndicator programIndicator : programIndicators) {
                if(!programIndicator.isDisplayInForm()){
                    continue;
                }
                String value = ProgramIndicatorService.getProgramIndicatorValue(programOverviewFragmentForm.getEnrollment(), programIndicator);
                if(value==null) {
                    continue;
                }
                IndicatorRow indicatorRow = new IndicatorRow(programIndicator, value,
                        programIndicator.getDisplayDescription());
                programOverviewFragmentForm.getProgramIndicatorRows().put(programIndicator,
                        indicatorRow);
            }
        }else{
            programOverviewFragmentForm.getProgramIndicatorRows().clear();
        }
        return programOverviewFragmentForm;
    }

    // Norway
    private boolean canAddProgramStage(ProgramStage ps) {
        UserAccount userAccount = MetaDataController.getUserAccount();
        if (userAccount.getUserGroups() != null) {
            // System.out.println("Norway - UG access: " + (userAccount.getUserGroups().size()));
            if (userAccount != null && ps.userHasAccess(userAccount.getUserGroups())) {
                return true;
            }
        }
        return false;
    }

    private List<ProgramStageRow> getProgramStageRows(Enrollment enrollment) {
        List<ProgramStageRow> rows = new ArrayList<>();
        List<Event> events = enrollment.getEvents(true);
        HashMap<String, List<Event>> eventsByStage = new HashMap<>();
        for(Event event: events) {
            List<Event> eventsForStage = eventsByStage.get(event.getProgramStageId());
            if(eventsForStage==null) {
                eventsForStage = new ArrayList<>();
                eventsByStage.put(event.getProgramStageId(), eventsForStage);
            }
            eventsForStage.add(event);
        }
        Program program = MetaDataController.getProgram(mProgramId);
        for(ProgramStage programStage: program.getProgramStages()) {
            List<Event> eventsForStage = eventsByStage.get(programStage.getUid());
            ProgramStageLabelRow labelRow = new ProgramStageLabelRow(programStage);
            // System.out.println("Norway - PS access: " + (programStage.getUserGroupAccesses().size()));

            if (canAddProgramStage(programStage)) {
                //System.out.println("Norway - Adding row: " + labelRow.getProgramStage().getDisplayName() + " id: " + labelRow.getProgramStage().getUid());
                rows.add(labelRow);
            } else {
                // System.out.println("Norway - Cannot Add row: " + labelRow.getProgramStage().getDisplayName() + " id: " + labelRow.getProgramStage().getUid());
            }


            if(eventsForStage==null) {
                continue;
            } else {
                EventDateComparator comparator = new EventDateComparator();
                Collections.sort(eventsForStage, comparator);
            }
            for(Event event: eventsForStage) {
                //System.out.println("Norway - Event: " + event.getDisplayName());
                ProgramStageEventRow row = new ProgramStageEventRow(event);
                row.setLabelRow(labelRow);
                labelRow.getEventRows().add(row);
                rows.add(row);
            }
        }
        return rows;
    }

    private static <T> boolean isListEmpty(List<T> items) {
        return items == null || items.isEmpty();
    }
}