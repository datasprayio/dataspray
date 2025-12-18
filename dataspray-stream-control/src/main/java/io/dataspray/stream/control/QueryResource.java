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

import io.dataspray.store.QueryNotFoundException;
import io.dataspray.store.QueryStore;
import io.dataspray.stream.control.model.DatabaseSchemaResponse;
import io.dataspray.stream.control.model.DatabaseTable;
import io.dataspray.stream.control.model.QueryExecutionStatus;
import io.dataspray.stream.control.model.QueryHistoryResponse;
import io.dataspray.stream.control.model.QueryResultColumn;
import io.dataspray.stream.control.model.QueryResultsResponse;
import io.dataspray.stream.control.model.SubmitQueryRequest;
import io.dataspray.stream.control.model.SubmitQueryResponse;
import io.dataspray.web.resource.AbstractResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST resource for Athena query operations.
 */
@Slf4j
@ApplicationScoped
public class QueryResource extends AbstractResource implements QueryApi {

    @Inject
    QueryStore queryStore;

    @Override
    public SubmitQueryResponse submitQuery(String organizationName, SubmitQueryRequest request) {
        log.info("Submit query request for organization: {}", organizationName);

        // Validate organization access
        validateOrganizationAccess(organizationName);

        // Get username from security context
        String username = getUsername().orElse("unknown");

        // Submit query
        try {
            String queryExecutionId = queryStore.submitQuery(organizationName, request.getSqlQuery(), username);
            return new SubmitQueryResponse(queryExecutionId);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (QueryNotFoundException ex) {
            throw new NotFoundException(ex.getMessage(), ex);
        }
    }

    @Override
    public QueryExecutionStatus getQueryStatus(String organizationName, String queryExecutionId) {
        log.debug("Get query status: {} for organization: {}", queryExecutionId, organizationName);

        // Validate organization access
        validateOrganizationAccess(organizationName);

        // Get query execution
        try {
            QueryStore.QueryExecution execution = queryStore.getQueryExecution(organizationName, queryExecutionId);
            return mapToApiModel(execution);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (QueryNotFoundException ex) {
            throw new NotFoundException(ex.getMessage(), ex);
        }
    }

    @Override
    public QueryResultsResponse getQueryResults(String organizationName, String queryExecutionId,
                                                String nextToken, Integer maxResults) {
        log.debug("Get query results: {} for organization: {}", queryExecutionId, organizationName);

        // Validate organization access
        validateOrganizationAccess(organizationName);

        // Get results
        try {
            QueryStore.QueryResultPage page = queryStore.getQueryResults(
                    organizationName,
                    queryExecutionId,
                    Optional.ofNullable(nextToken),
                    Optional.ofNullable(maxResults).orElse(100)
            );
            return mapToApiModel(page);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (QueryNotFoundException ex) {
            throw new NotFoundException(ex.getMessage(), ex);
        }
    }

    @Override
    public QueryHistoryResponse getQueryHistory(String organizationName, Integer maxResults) {
        log.debug("Get query history for organization: {}", organizationName);

        // Validate organization access
        validateOrganizationAccess(organizationName);

        // Get history
        try {
            var history = queryStore.getQueryHistory(
                    organizationName,
                    Optional.ofNullable(maxResults).orElse(50)
            );
            return new QueryHistoryResponse(
                    history.stream()
                            .map(this::mapToApiModel)
                            .collect(Collectors.toList())
            );
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (QueryNotFoundException ex) {
            throw new NotFoundException(ex.getMessage(), ex);
        }
    }

    @Override
    public DatabaseSchemaResponse getDatabaseSchema(String organizationName) {
        log.debug("Get database schema for organization: {}", organizationName);

        // Validate organization access
        validateOrganizationAccess(organizationName);

        // Get schema
        try {
            QueryStore.DatabaseSchema schema = queryStore.getDatabaseSchema(organizationName);
            return mapToApiModel(schema);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (QueryNotFoundException ex) {
            throw new NotFoundException(ex.getMessage(), ex);
        }
    }

    /**
     * Validate that the current user has access to the organization.
     */
    private void validateOrganizationAccess(String organizationName) {
        // Check if user belongs to this organization
        if (!getOrganizationNames().contains(organizationName)) {
            throw new jakarta.ws.rs.ForbiddenException("Access denied to organization: " + organizationName);
        }
    }

    /**
     * Map QueryExecution to API model.
     */
    private QueryExecutionStatus mapToApiModel(QueryStore.QueryExecution execution) {
        return new QueryExecutionStatus(
                execution.getQueryExecutionId(),
                execution.getSqlQuery(),
                QueryExecutionStatus.StateEnum.valueOf(execution.getState().name()),
                toOffsetDateTime(execution.getSubmittedAt()),
                toOffsetDateTime(execution.getCompletedAt()),
                execution.getBytesScanned(),
                execution.getExecutionTimeMs(),
                execution.getErrorMessage(),
                execution.getUsername()
        );
    }

    /**
     * Convert Instant to OffsetDateTime (API uses OffsetDateTime for better serialization compatibility).
     */
    private static java.time.OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
    }

    /**
     * Map QueryResultPage to API model.
     */
    private QueryResultsResponse mapToApiModel(QueryStore.QueryResultPage page) {
        return new QueryResultsResponse(
                page.getColumns().stream()
                        .map(col -> new QueryResultColumn(col.getName(), col.getType()))
                        .collect(Collectors.toList()),
                page.getRows(),
                page.getNextToken()
        );
    }

    /**
     * Map DatabaseSchema to API model.
     */
    private DatabaseSchemaResponse mapToApiModel(QueryStore.DatabaseSchema schema) {
        return new DatabaseSchemaResponse(
                schema.getDatabaseName(),
                schema.getTables().stream()
                        .map(table -> new DatabaseTable(
                                table.getName(),
                                table.getColumns().stream()
                                        .map(col -> new QueryResultColumn(col.getName(), col.getType()))
                                        .collect(Collectors.toList())))
                        .collect(Collectors.toList())
        );
    }
}
