/*
 * Copyright 2025 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.stream.control;

import com.google.common.collect.ImmutableMap;
import io.dataspray.store.StateStore;
import io.dataspray.store.util.WithCursor;
import io.dataspray.stream.control.model.*;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class StateResource extends AbstractResource implements StateApi {

    @Inject
    StateStore stateStore;

    @Override
    public StateListResponse listState(String organizationName, StateListRequest request) {
        log.info("Listing state for org: {}, keyPrefix: {}",
            organizationName, request.getKeyPrefix());

        // Validate organization access
        validateOrganizationAccess(organizationName);

        WithCursor<List<StateStore.StateEntry>> result = stateStore.listState(
            organizationName,
            Optional.ofNullable(request.getKeyPrefix())
                .map(list -> list.toArray(new String[0])),
            Optional.ofNullable(request.getCursor()),
            Optional.ofNullable(request.getLimit()).orElse(50)
        );

        List<StateEntry> entries = result.getData().stream()
            .map(this::toApiStateEntry)
            .collect(Collectors.toList());

        StateListResponse response = new StateListResponse();
        response.setEntries(entries);
        response.setNextCursor(result.getCursorOpt().orElse(null));
        return response;
    }

    @Override
    public StateEntry getState(String organizationName, StateGetRequest request) {
        log.info("Getting state for org: {}, key: {}",
            organizationName, request.getKeyParts());

        // Validate organization access
        validateOrganizationAccess(organizationName);

        return stateStore.getState(
            organizationName,
            request.getKeyParts().toArray(new String[0])
        )
        .map(this::toApiStateEntry)
        .orElseThrow(() -> new NotFoundException("State not found"));
    }

    @Override
    public StateEntry upsertState(String organizationName, StateUpsertRequest request) {
        log.info("Upserting state for org: {}, key: {}",
            organizationName, request.getKeyParts());

        // Validate organization access
        validateOrganizationAccess(organizationName);

        StateStore.StateEntry result = stateStore.upsertState(
            organizationName,
            request.getKeyParts().toArray(new String[0]),
            ImmutableMap.copyOf(request.getAttributes()),
            Optional.ofNullable(request.getTtlInSec())
        );

        return toApiStateEntry(result);
    }

    @Override
    public void deleteState(String organizationName, StateDeleteRequest request) {
        log.info("Deleting state for org: {}, key: {}",
            organizationName, request.getKeyParts());

        // Validate organization access
        validateOrganizationAccess(organizationName);

        stateStore.deleteState(
            organizationName,
            request.getKeyParts().toArray(new String[0])
        );
    }

    private StateEntry toApiStateEntry(StateStore.StateEntry entry) {
        StateEntry apiEntry = new StateEntry();
        apiEntry.setKeyParts(List.of(entry.getKeyParts()));
        apiEntry.setMergedKey(entry.getMergedKey());
        apiEntry.setAttributes(entry.getAttributes());
        apiEntry.setTtlInEpochSec(entry.getTtlInEpochSec().orElse(null));
        return apiEntry;
    }

    private void validateOrganizationAccess(String organizationName) {
        if (!getOrganizationNames().contains(organizationName)) {
            log.warn("User attempted to access state for unauthorized organization: {}", organizationName);
            throw new NotFoundException("Organization not found or access denied");
        }
    }
}
