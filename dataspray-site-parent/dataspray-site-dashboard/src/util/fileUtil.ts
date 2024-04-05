export const downloadStringFile = (filename: string, mediaType: string, data: string) => {
    downloadBlobFile(filename, new Blob([data], {type: mediaType}));
}

export const downloadBlobFile = (filename: string, data: Blob) => {
    if ((window.navigator as any)['msSaveBlob']) {
        (window.navigator as any)['msSaveBlob'](data, filename);
    } else {
        const elem = window.document.createElement('a');
        elem.href = window.URL.createObjectURL(data);
        elem.download = filename;
        document.body.appendChild(elem);
        elem.click();
        document.body.removeChild(elem);
    }
}
