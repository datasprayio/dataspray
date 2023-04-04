/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package jakarta.ws.rs;

import jakarta.ws.rs.core.Response;

/**
 * A runtime exception indicating that server has a {@link jakarta.ws.rs.core.Response.Status#CONFLICT conflict} with
 * the
 * requested resource.
 */
public class ConflictException extends ClientErrorException {

    private static final long serialVersionUID = 2805510376267021315L;

    /**
     * Construct a new "conflict" exception.
     */
    public ConflictException() {
        super(Response.Status.CONFLICT);
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     */
    public ConflictException(String message) {
        super(message, Response.Status.CONFLICT);
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param response error response.
     * @throws IllegalArgumentException in case the status code set in the response
     * is not HTTP {@code 409}.
     */
    public ConflictException(Response response) {
        super(WebApplicationException.validate(response, Response.Status.CONFLICT));
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     * @param response error response.
     * @throws IllegalArgumentException in case the status code set in the response
     * is not HTTP {@code 409}.
     */
    public ConflictException(String message, Response response) {
        super(message, WebApplicationException.validate(response, Response.Status.CONFLICT));
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param cause the underlying cause of the exception.
     */
    public ConflictException(Throwable cause) {
        super(Response.Status.CONFLICT, cause);
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     * @param cause the underlying cause of the exception.
     */
    public ConflictException(String message, Throwable cause) {
        super(message, Response.Status.CONFLICT, cause);
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param response error response.
     * @param cause the underlying cause of the exception.
     * @throws IllegalArgumentException in case the status code set in the response
     * is not HTTP {@code 409}.
     */
    public ConflictException(Response response, Throwable cause) {
        super(WebApplicationException.validate(response, Response.Status.CONFLICT), cause);
    }

    /**
     * Construct a new "conflict" exception.
     *
     * @param message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     * @param response error response.
     * @param cause the underlying cause of the exception.
     * @throws IllegalArgumentException in case the status code set in the response
     * is not HTTP {@code 409}.
     */
    public ConflictException(String message, Response response, Throwable cause) {
        super(message, WebApplicationException.validate(response, Response.Status.CONFLICT), cause);
    }
}
