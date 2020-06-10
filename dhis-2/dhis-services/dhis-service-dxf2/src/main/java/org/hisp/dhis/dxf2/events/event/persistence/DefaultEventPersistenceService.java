package org.hisp.dhis.dxf2.events.event.persistence;

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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.events.event.EventStore;
import org.hisp.dhis.dxf2.events.importer.context.WorkContext;
import org.hisp.dhis.dxf2.events.importer.mapper.ProgramStageInstanceMapper;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Luciano Fiandesio
 */
@Service
@Slf4j
public class  DefaultEventPersistenceService
    implements
    EventPersistenceService
{
    private final EventStore jdbcEventStore;

    private final IdentifiableObjectManager manager;

    public DefaultEventPersistenceService( EventStore jdbcEventStore, IdentifiableObjectManager manager )
    {
        checkNotNull( jdbcEventStore );
        checkNotNull( manager );

        this.jdbcEventStore = jdbcEventStore;
        this.manager = manager;
    }

    @Override
    @Transactional
    public void save( WorkContext context, List<Event> events )
    {
        /*
         * Save Events, Notes and Data Values
         */
        ProgramStageInstanceMapper mapper = new ProgramStageInstanceMapper( context );

        jdbcEventStore.saveEvents( events.stream().map( mapper::map ).collect( Collectors.toList() ) );

        updateTeis( context, events );
    }

    /**
     * Updates the list of given events using a single transaction.
     *
     * @param context a {@see WorkContext}
     * @param events a List of {@see Event}
     */

    @Override
    @Transactional
    public void update( final WorkContext context, final List<Event> events ) {

        if ( isNotEmpty( events ) )
        {
            final Map<Event, ProgramStageInstance> eventProgramStageInstanceMap = convertToProgramStageInstances(
                new ProgramStageInstanceMapper( context ), events );

            jdbcEventStore.updateEvents( new ArrayList<>( eventProgramStageInstanceMap.values() ) );

            updateTeis( context, events );
        }
    }
    
    private void updateTeis( final WorkContext context, final List<Event> events )
    {
        if ( !context.getImportOptions().isSkipLastUpdated() )
        {
            jdbcEventStore.updateTrackedEntityInstances( events.stream()
                //
                // filter out TEIs which have the canUpdate flag set to false
                //
                .filter( e -> context.getTrackedEntityInstanceMap().get( e.getUid() ).getRight() )
                .map( Event::getTrackedEntityInstance )
                .collect( Collectors.toList() ), context.getImportOptions().getUser() );
        }
    }

    /**
     * Deletes the list of events using a single transaction.
     *
     * @param context a {@see WorkContext}
     * @param events a List of {@see Event}
     */
    @Override
    @Transactional
    public void delete( final WorkContext context, final List<Event> events )
    {
        if ( isNotEmpty( events ) )
        {
            jdbcEventStore.delete( events );
        }
    }

    /**
     * Deletes the event using a single transaction.
     *
     * @param context a {@see WorkContext}
     * @param event the event to delete {@see Event}
     */
    @Override
    @Transactional
    public void delete( final WorkContext context, final Event event )
    {
        if ( event != null )
        {
            jdbcEventStore.delete( singletonList( event ) );
        }
    }

    private void updateTrackedEntityInstance( final TrackedEntityInstance tei, final User user )
    {
        final TrackedEntityInstance loadedTei = manager.get( TrackedEntityInstance.class, tei.getUid() );

        loadedTei.setCreatedAtClient( tei.getCreatedAtClient() );
        loadedTei.setLastUpdatedAtClient( tei.getLastUpdatedAtClient() );
        loadedTei.setInactive( tei.isInactive() );
        loadedTei.setLastSynchronized( tei.getLastSynchronized() );
        loadedTei.setCreated( tei.getCreated() );
        loadedTei.setLastUpdated( tei.getLastUpdated() );
        loadedTei.setUser( tei.getUser() );
        loadedTei.setLastUpdatedBy( tei.getLastUpdatedBy() );

        manager.update( loadedTei, user );
    }

    private Map<Event, ProgramStageInstance> convertToProgramStageInstances( ProgramStageInstanceMapper mapper,
        List<Event> events )
    {
        Map<Event, ProgramStageInstance> map = new HashMap<>();
        for ( Event event : events )
        {
            ProgramStageInstance psi = mapper.map( event );
            map.put( event, psi );
        }

        return map;
    }
}
