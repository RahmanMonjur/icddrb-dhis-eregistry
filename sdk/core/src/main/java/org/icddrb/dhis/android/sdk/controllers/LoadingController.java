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

package org.icddrb.dhis.android.sdk.controllers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.icddrb.dhis.android.sdk.R;
import org.icddrb.dhis.android.sdk.controllers.metadata.MetaDataController;
import org.icddrb.dhis.android.sdk.controllers.tracker.TrackerController;
import org.icddrb.dhis.android.sdk.events.LoadingMessageEvent;
import org.icddrb.dhis.android.sdk.events.UiEvent;
import org.icddrb.dhis.android.sdk.network.APIException;
import org.icddrb.dhis.android.sdk.network.DhisApi;
import org.icddrb.dhis.android.sdk.persistence.Dhis2Application;
import org.icddrb.dhis.android.sdk.persistence.models.UnionFWA;
import org.icddrb.dhis.android.sdk.persistence.models.OrganisationUnitUser;
import org.icddrb.dhis.android.sdk.persistence.models.UnionFWADropDownItem;
import org.icddrb.dhis.android.sdk.persistence.models.User;
import org.icddrb.dhis.android.sdk.persistence.models.UserCredentials;
import org.icddrb.dhis.android.sdk.persistence.preferences.AppPreferences;
import org.icddrb.dhis.android.sdk.persistence.preferences.ResourceType;
import org.icddrb.dhis.android.sdk.utils.UiUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.icddrb.dhis.android.sdk.utils.NetworkUtils.unwrapResponse;

/**
 * @author Simen Skogly Russnes on 25.08.15.
 */
public final class LoadingController {

    public static final String CLASS_TAG = LoadingController.class.getSimpleName();
    public final static String LOAD = "load";

    private LoadingController() {
    }

    public static void enableLoading(Context context, ResourceType resourceType) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(DhisController.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(LOAD + resourceType, true);
        editor.commit();
    }

    /**
     * clears all loading flags
     *
     * @param context
     */
    public static void clearLoadFlags(Context context) {
        for (ResourceType resourceType : ResourceType.values()) {
            clearLoadFlag(context, resourceType);
        }
    }

    public static void clearLoadFlag(Context context, ResourceType resourceType) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(DhisController.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(LOAD + resourceType, false);
        editor.commit();
    }

    /**
     * returns whether or not a load flag has been enabled.
     *
     * @param context
     * @param flag
     * @return
     */
    public static boolean isLoadFlagEnabled(Context context, ResourceType flag) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(DhisController.PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(LOAD + flag, false);
    }

    public static boolean isInitialDataLoaded(Context context) {
        return (MetaDataController.isDataLoaded(context) && TrackerController.isDataLoaded(context));
    }

    /**
     * Loads initial data (Meta Data and Data Values). Which data is enabled is defined by the
     * enableLoading method
     */
    static void loadInitialData(Context context, DhisApi dhisApi)
            throws APIException, IllegalStateException {
        UiUtils.postProgressMessage(context.getString(org.icddrb.dhis.android.sdk.R.string.finishing_up),
                LoadingMessageEvent.EventType.STARTUP);
        if (!MetaDataController.isDataLoaded(context)) {
            Log.d(CLASS_TAG, "loading initial metadata");
            loadMetaData(context, SyncStrategy.DOWNLOAD_ALL, dhisApi);
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.INITIAL_SYNCING_END));
            String message = "";
            message = context.getString(R.string.finishing_up);
            UiUtils.postProgressMessage(message, LoadingMessageEvent.EventType.STARTUP);
            loadDataValues(context, SyncStrategy.DOWNLOAD_ALL, dhisApi);
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.INITIAL_SYNCING_END));
        } else if (!TrackerController.isDataLoaded(context)) {
            Log.d(CLASS_TAG, "loading initial datavalues");
            String message = "";
            message = context.getString(R.string.finishing_up);
            UiUtils.postProgressMessage(message, LoadingMessageEvent.EventType.STARTUP);
            loadDataValues(context, SyncStrategy.DOWNLOAD_ALL, dhisApi);
        }
    }

    /**
     * Initiates loading of metadata from the server. To update existing data, rather use
     * synchronizeMetaData to save data.
     *
     * @param context
     */
    static void loadMetaData(Context context, SyncStrategy syncStrategy, DhisApi dhisApi) throws APIException {

        if (context == null) {
            Log.i(CLASS_TAG, "Unable to load metadata. We have no valid context.");
            return;
        }

        Log.d(CLASS_TAG, "loading metadata!");
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_START));
        try {
            MetaDataController.loadMetaData(context, dhisApi, syncStrategy);
        } catch (APIException e) {
            //to make sure we stop showing loading indicator
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
            throw e;
        }
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
    }

    static void loadDataValues(Context context, SyncStrategy syncStrategy, DhisApi dhisApi) throws APIException {
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_START));
        try {
            TrackerController.loadDataValues(context, dhisApi, syncStrategy);
        } catch (APIException e) {
            //to make sure we stop showing loading indicator
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
            throw e;
        }
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
    }

    /// Norway
    static HashMap<String, List<OrganisationUnitUser>> getUnionUsers(List<OrganisationUnitUser> orgOptionSets) {
        List<String> ids = new ArrayList<>();
        HashMap<String, String> levels = new HashMap<>();
        HashMap<String, List<OrganisationUnitUser>> idMap = new HashMap<>();

        if (orgOptionSets.size() > 0) {
            // First do Unions
            for (OrganisationUnitUser org : orgOptionSets) {
                if (org.getLevel() == 5) {
                    ids.add(org.getId());
                    List<OrganisationUnitUser> n = new ArrayList<>();
                    n.add(org);
                    idMap.put(org.getId(), n);
                }
            }

            // Do next level
            for (OrganisationUnitUser org : orgOptionSets) {
                if (org.getLevel() == 6 && inArray(ids,org.getParent()) ) {
                    idMap.get(org.getParent()).add(org);
                    levels.put(org.getId(), org.getParent());
                }
            }
            // Do last level
            for (OrganisationUnitUser org : orgOptionSets) {
                if (org.getLevel() == 7 && levels.containsKey(org.getParent())) {
                    idMap.get(levels.get(org.getParent())).add(org);
                }
            }
        }
        return idMap;
    }

    static boolean inArray(List<String> a, String i) {
        for (String s : a) {
            if (s.trim().equalsIgnoreCase(i.trim())) return true;
        }
        return false;
    }

    static void loadUnionData(Context context, SyncStrategy syncStrategy, DhisApi dhisApi) throws APIException {
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_START));
        try {
            List<OrganisationUnitUser> orgOptionSets = unwrapResponse(dhisApi.getOrgsAndUsers(), "organisationUnits");

            System.out.println("Norway - Org unit size: " + orgOptionSets.size());
            if (orgOptionSets.size() > 0) {
                UnionFWA dropData = new UnionFWA();
                HashMap<String, List<OrganisationUnitUser>> unionUsers = getUnionUsers(orgOptionSets);
                System.out.println("Norway - users org size: " + unionUsers.size());

                for (OrganisationUnitUser org : orgOptionSets) {
                    if (unionUsers.containsKey(org.getId())) {
                        if (!unionUsers.get(org.getId()).isEmpty()) {
                            for(OrganisationUnitUser o : unionUsers.get(org.getId())) {
                                List<User> userOptionSets =  o.getUsers();
                                //System.out.println("Norway - Getting users for " + o.getLabel() + "(" + o.getId() + ") found " + userOptionSets.size() + " users");
                                if (userOptionSets.size() > 0) {
                                    List<UnionFWADropDownItem> users = new ArrayList<>();
                                    for (User user : userOptionSets) {
                                        UserCredentials uc = user.getUserCredential();
                                        if (uc.hasRole("FWA: Family Welfare Assistant") ||
                                                uc.hasRole("Field Worker Program Access (no authorities)") ||
                                                uc.hasRole("FWV Test")) {
                                            //System.out.println("Norway - Adding user " + user.getDisplayName() + " for " + o.getLabel());
                                            users.add(new UnionFWADropDownItem(user.getUid(), user.getDisplayName()));
                                        }
                                    }
                                    if (users.size() > 0) {
                                        dropData.addUsers(org.getId(), users);
                                    }
                                }
                            }
                        }
                    }
                    if (org.getLevel() == 5) {
                        dropData.addOrg(org.getId(), org.getLabel());
                    }
                }

                AppPreferences prefs = new AppPreferences(context);
                prefs.putDropdownInfo(dropData);

            } else {
                System.out.println("Norway - no orgs found");
            }


        } catch (APIException e) {
            //to make sure we stop showing loading indicator
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
            throw e;
        }
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
    }



    static void syncRemotelyDeletedData(Context context, DhisApi dhisApi) throws APIException {
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_START));
        try {
            TrackerController.syncRemotelyDeletedData(context, dhisApi);
        } catch (APIException e) {
            //to make sure we stop showing loading indicator
            Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
            throw e;
        }
        Dhis2Application.getEventBus().post(new UiEvent(UiEvent.UiEventType.SYNCING_END));
    }
}
