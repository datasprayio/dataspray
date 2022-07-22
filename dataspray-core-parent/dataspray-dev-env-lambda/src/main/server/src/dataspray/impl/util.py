# coding: utf-8

from urllib.parse import urlparse
from dataspray.impl.config import getWorkingDirectory
from os.path import join, relpath, normpath, isabs
from fastapi import HTTPException

URI_SCHEME = 'io.dataspray.remote'

def uriToPath(uriStr: str) -> str:
    try:
        uri = urlparse(uriStr)
    except ValueError:
        print('Uri cannot be parsed', uriStr, e)
        raise HTTPException(status_code=400)
    if not uri.scheme == URI_SCHEME:
        print('Uri has wrong schema', uriStr)
        raise HTTPException(status_code=400)
    return normpath(join(getWorkingDirectory(), uri.path.strip("/")))

def pathToUri(path: str) -> str:
    pathRel = path if not isabs(path) else relpath(path, getWorkingDirectory())
    return URI_SCHEME + ':///' + normpath(pathRel)
