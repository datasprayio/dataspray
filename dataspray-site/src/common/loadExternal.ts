import { isSsr } from "./isoUtil";

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
