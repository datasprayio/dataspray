import { useEffect } from 'react';
import { loadExternal, loadExternalCss } from '../common/loadExternal';

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

        loadExternalCss('/vscode-web/out/vs/workbench/workbench.web.main.css');
        await loadExternal('/vscode-web/out/vs/loader.js');
        await loadExternal('/vscode-web/out/vs/webPackagePaths.js');

        Object.entries((self as any).webPackagePaths).forEach(([key, value]) => {
          (self as any).webPackagePaths[key] = `${window.location.origin}/vscode-web/node_modules/${key}/${value}`;
        });
        (window as any).require.config({
          baseUrl: `${window.location.origin}/vscode-web/out`,
          recordStats: true,
          trustedTypesPolicy: (window as any).trustedTypes?.createPolicy('amdLoader', {
            createScriptURL(value: any) {
              return value;
            }
          }),
          paths: (self as any).webPackagePaths
        });

        await loadExternal('/vscode-web/out/vs/workbench/workbench.web.main.nls.js');
        await loadExternal('/vscode-web/out/vs/workbench/workbench.web.main.js');
        await loadExternal('/vscode-web/out/vs/code/browser/workbench/workbench.js');
      } catch (err) {
        console.log('Failed to load editor', err);
      }
    })();
  });
  return (
    <>
      <div id='vscode-web' style={{ width: 1024, height: 768, margin: 15, border: '1px solid black' }} />
    </>
  )
}

export default Editor;
