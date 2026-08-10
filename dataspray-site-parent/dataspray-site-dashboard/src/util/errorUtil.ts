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

/**
 * Extract a human-readable error message from an unknown thrown value.
 *
 * The generated typescript-fetch client throws {@code ResponseError} carrying a raw
 * {@code Response} object (there is no {@code e.response.data}). This helper attempts
 * to parse the response body as JSON to find an error message, falling back to the
 * HTTP status, the error's own message, and finally the provided fallback.
 */
export async function getErrorMessage(e: unknown, fallback: string = 'Unknown error'): Promise<string> {
    const response: Response | undefined = (e as any)?.response instanceof Response
        ? (e as any).response
        : undefined;

    if (response) {
        try {
            const body = await response.clone().json();
            const message = body?.error?.message ?? body?.message;
            if (message && typeof message === 'string') {
                return message;
            }
        } catch {
            // Body missing or not JSON; fall back to status below
        }
        return `${response.status}${response.statusText ? ' ' + response.statusText : ''}`;
    }

    const message = (e as any)?.message;
    if (message && typeof message === 'string') {
        return message;
    }

    return fallback;
}
