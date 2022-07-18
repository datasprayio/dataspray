import { isSsr } from "./isoUtil";

const loadExternal = (url: string) => new Promise<void>((resolve, reject) => {
  if (isSsr()) return;
  const script = document.createElement('script');
  script.src = url;
  script.async = true;
  script.onload = ev => resolve();
  script.onerror = err => reject(Error(`${url} failed to load: ${err}`));
  document.head.appendChild(script);
});

export default loadExternal;
