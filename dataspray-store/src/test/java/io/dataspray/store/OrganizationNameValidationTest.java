/*
 * Copyright 2026 Matus Faro
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

package io.dataspray.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrganizationNameValidationTest {

    @Test
    public void testValidNames() {
        assertTrue(OrganizationStore.isOrganizationNameValid("acme"));
        assertTrue(OrganizationStore.isOrganizationNameValid("Acme_Corp_123"));
        assertTrue(OrganizationStore.isOrganizationNameValid("abc"));
        assertTrue(OrganizationStore.isOrganizationNameValid("a".repeat(64)));
    }

    @Test
    public void testInvalidNames() {
        assertFalse(OrganizationStore.isOrganizationNameValid(null));
        assertFalse(OrganizationStore.isOrganizationNameValid(""));
        assertFalse(OrganizationStore.isOrganizationNameValid("ab"));
        assertFalse(OrganizationStore.isOrganizationNameValid("a".repeat(65)));
        // Characters the authorizer strips when building policy ARNs: an org named
        // "acme*" would collide with "acme" and inherit its API access
        assertFalse(OrganizationStore.isOrganizationNameValid("acme*"));
        assertFalse(OrganizationStore.isOrganizationNameValid("acme!"));
        assertFalse(OrganizationStore.isOrganizationNameValid("acme space"));
        assertFalse(OrganizationStore.isOrganizationNameValid("acme/../../etc"));
        // Dash is the separator in customer SQS queue names (customer-<org>-<queue>)
        assertFalse(OrganizationStore.isOrganizationNameValid("acme-inc"));
    }
}
