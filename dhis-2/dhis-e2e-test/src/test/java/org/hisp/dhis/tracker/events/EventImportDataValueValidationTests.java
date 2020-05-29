/*
 * Copyright (c) 2004-2020, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis.tracker.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.hamcrest.Matchers;
import org.hisp.dhis.ApiTest;
import org.hisp.dhis.Constants;
import org.hisp.dhis.actions.RestApiActions;
import org.hisp.dhis.actions.metadata.ProgramActions;
import org.hisp.dhis.actions.tracker.EventActions;
import org.hisp.dhis.dto.ApiResponse;
import org.hisp.dhis.helpers.QueryParamsBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
public class EventImportDataValueValidationTests
    extends ApiTest
{
    private ProgramActions programActions;

    private EventActions eventActions;

    private RestApiActions dataElementActions;

    private static String OU_ID = Constants.ORG_UNIT_IDS[0];

    private String programId;

    private String programStageId;

    private String mandatoryDataElementId;

    @BeforeAll
    public void beforeAll()
    {
        programActions = new ProgramActions();
        eventActions = new EventActions();
        dataElementActions = new RestApiActions( "/dataElements" );

        setupData();
    }

    @Test
    public void shouldNotImportEventsWithoutCompulsoryDataElements()
    {
        JsonObject events = eventActions.createEventBody( OU_ID, programId, programStageId );

        ApiResponse response = eventActions.post( events );

        response.validate().statusCode( 200 )
            .body( "status", equalTo( "OK" ) )
            .body( "response.ignored", equalTo( 1 ) );
    }

    @Test
    public void shouldImportEventsWithCompulsoryDataElements()
    {
        JsonObject events = eventActions.createEventBody( OU_ID, programId, programStageId );

        addDataValue( events, mandatoryDataElementId, "TEXT VALUE" );

        ApiResponse response = eventActions.post( events );

        response.validate().statusCode( 200 )
            .body( "status", equalTo( "OK" ) )
            .body( "response.imported", equalTo( 1 ) );

        String eventID = response.extractString( "response.importSummaries.reference[0]" );
        assertNotNull( eventID, "Failed to extract eventId" );

        eventActions.get( eventID )
            .validate()
            .statusCode( 200 )
            .body( "dataValues", not( Matchers.emptyArray() ) );
    }

    private void setupData()
    {
        ApiResponse response = programActions.createEventProgram( OU_ID );

        programId = response.extractUid();
        assertNotNull( programId, "Failed to create a program" );

        programStageId = programActions.get( programId, new QueryParamsBuilder().add( "fields=*" ) )
            .extractString( "programStages.id[0]" );
        assertNotNull( programStageId, "Failed to create a programStage" );

        String dataElementId = dataElementActions
            .get( "?fields=id&filter=domainType:eq:TRACKER&filter=valueType:eq:TEXT&pageSize=1" )
            .extractString( "dataElements.id[0]" );

        assertNotNull( dataElementId, "Failed to find data elements" );
        mandatoryDataElementId = dataElementId;

        programActions.addDataElement( programStageId, dataElementId, true ).validate().statusCode( 200 );
    }

    private void addDataValue( JsonObject body, String dataElementId, String value )
    {
        JsonArray dataValues = new JsonArray();

        JsonObject dataValue = new JsonObject();

        dataValue.addProperty( "dataElement", dataElementId );
        dataValue.addProperty( "value", value );

        dataValues.add( dataValue );
        body.add( "dataValues", dataValues );
    }

}
