import { useEffect } from 'react';
import loadExternal from '../common/loadExternal';

var inited = false;
const Editor = () => {
  useEffect(() => {
    (async function () {
      if (inited) return;
      inited = true;

      try {
        // Picked up by workbench.js
        (window as any).product = {
          domElementId: 'vscode-web',
          productConfiguration: {
            nameShort: 'DataSpray Editor',
            nameLong: 'DataSpray Editor',
            applicationName: 'dataspray-editor',
            dataFolderName: '.vscode-web',
            version: '1.66.0',
            extensionsGallery: {
              serviceUrl: 'https://open-vsx.org/vscode/gallery',
              itemUrl: 'https://open-vsx.org/vscode/item',
              resourceUrlTemplate: 'https://openvsxorg.blob.core.windows.net/resources/{publisher}/{name}/{version}/{path}'
            },
            extensionEnabledApiProposals: {
              'vscodevscode-web-playground': [
                'fileSearchProvider',
                'textSearchProvider',
              ]
            }
          },
          folderUri: {
            scheme: 'memfs',
            path: '/sample-folder',
          },
          additionalBuiltinExtensions: [
            {
              scheme: 'http',
              path: '/myExt',
            }
          ],
        };

        loadExternal('/vscode-web/vs/workbench/workbench.web.main.css');
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
      <div id='vscode-web' style={{ width: 500, height: 200, margin: 15, border: '1px solid black' }} />
    </>
  )
}

export default Editor;
