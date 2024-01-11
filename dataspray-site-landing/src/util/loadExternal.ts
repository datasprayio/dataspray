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

import {isSsr} from "./isoUtil";

export const loadExternal = (url: string) => new Promise<void>((resolve, reject) => {
    if (isSsr()) return;
    const script = document.createElement('script');
    script.src = url;
    script.async = true;
    script.onload = ev => resolve();
    script.onerror = err => reject(Error(`${url} failed to load: ${err}`));
    document.head.appendChild(script);
});

export const loadExternalCss = (url: string) => new Promise<void>((resolve, reject) => {
    if (isSsr()) return;
    const css = document.createElement('link');
    css.href = url;
    css.type = 'text/css';
    css.rel = 'stylesheet';
    css.media = 'screen,print';
    css.onload = ev => resolve();
    css.onerror = err => reject(Error(`${url} failed to load: ${err}`));
    document.head.appendChild(css);
});
