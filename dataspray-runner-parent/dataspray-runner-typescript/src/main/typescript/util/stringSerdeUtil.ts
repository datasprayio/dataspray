/*
 * Copyright 2024 Matus Faro
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

const DELIMITER = ':';
const ESCAPER = '\\';

export const mergeStrings = (ss: string[]): string => {
    if (ss == null || ss.length == 0) {
        return '';
    }
    let result = '';
    for (let i = 0; i < ss.length; i++) {
        const s = ss[i];
        for (let j = 0; j < s.length; j++) {
            const c = s.charAt(j);
            switch (c) {
                case ESCAPER:
                    result += ESCAPER + ESCAPER;
                    break;
                case DELIMITER:
                    result += ESCAPER + DELIMITER;
                    break;
                default:
                    result += c;
                    break;
            }
        }
        if (i + 1 < ss.length) {
            result += DELIMITER;
        }
    }
    return result;
}

export const unMergeString = (s: string): string[] => {
    if (s == null) {
        return [];
    }
    let results: string[] = [];
    let result = '';
    let nextCharEscaped = false;
    for (let i = 0; i < s.length; i++) {
        const c = s.charAt(i);
        if (nextCharEscaped) {
            nextCharEscaped = false;
            result += c;
            continue;
        }
        switch (c) {
            case ESCAPER:
                nextCharEscaped = true;
                break;
            case DELIMITER:
                results.push(result);
                result = '';
                break;
            default:
                result += c;
                break;
        }
    }
    results.push(result);
    return results;
}
