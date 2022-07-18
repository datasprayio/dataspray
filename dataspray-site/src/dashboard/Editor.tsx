import { useEffect } from 'react';
import 'vscode-web/dist/out/vs/workbench/workbench.web.main.css';
import loadExternal from '../common/loadExternal';

var inited = false;
const Editor = () => {
  useEffect(() => {
    (async function () {
      if (inited) return;
      inited = true;
      try {
        await loadExternal('/vscode-web/vs/loader.js');
        await loadExternal('/vscode-web/vs/webPackagePaths.js');

        Object.keys((self as any).webPackagePaths).map(function (key, index) {
          (self as any).webPackagePaths[key] = `${window.location.origin}/node_modules/vscode-web/dist/node_modules/${key}/${(self as any).webPackagePaths[key]}`;
        });
        (window as any).require.config({
          baseUrl: `${window.location.origin}/vscode-web`,
          recordStats: true,
          trustedTypesPolicy: (window as any).trustedTypes?.createPolicy('amdLoader', {
            createScriptURL(value: any) {
              return value;
            }
          }),
          paths: (self as any).webPackagePaths
        });

        await loadExternal('/vscode-web/vs/workbench/workbench.web.main.nls.js');
        await loadExternal('/vscode-web/vs/workbench/workbench.web.main.js');
        await loadExternal('/vscode-web/vs/code/browser/workbench/workbench.js');
      } catch (err) {
        console.log('Failed to load editor', err);
      }
    })();
  });
  return (
    <>
      Hello world

    </>
  )
}

export default Editor;
