# coding: utf-8

import errno
from shutil import copy2, copytree, rmtree
from os import mkdir, rmdir, remove, listdir, scandir, rename as filerename, stat as filestat, name as osname
from os.path import isfile, islink, join, exists, normpath, dirname
from stat import *
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from dataspray.models.read_directory_response import ReadDirectoryResponse
from dataspray.models.read_directory_response_files_inner import ReadDirectoryResponseFilesInner
from dataspray.models.stat_response import StatResponse
from dataspray.impl.util import uriToPath
from dataspray.apis.file_system_api import file
from pathlib import Path

async def copy(
    sourceUri: str,
    destinationUri: str,
    overwrite: bool,
) -> None:
    source = uriToPath(sourceUri)
    destination = uriToPath(destinationUri)
    if not exists(Path(destination).parent):
        raise HTTPException(status_code=404)
    if exists(destination):
        if overwrite:
            if isfile(destination):
                remove(destination)
            else:
                rmtree(destination)
        elif isfile(destination):
            raise HTTPException(status_code=409)
    try:
        if isfile(source):
            copy2(source, destination)
        else:
            copytree(source, destination, copy_function=copy2)
    except FileNotFoundError:
        raise HTTPException(status_code=404)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.EEXIST:
            raise HTTPException(status_code=409)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)

async def create_directory(
    uri: str,
) -> None:
    path = uriToPath(uri)
    try:
        mkdir(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.EEXIST:
            raise HTTPException(status_code=409)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)

async def delete(
    uri: str,
    recursive: bool,
) -> None:
    path = uriToPath(uri)
    try:
         if isfile(path):
             remove(path)
         else:
            if not recursive:
                rmdir(path)
            else:
                rmtree(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        if e.errno == errno.ENOTEMPTY:
            raise HTTPException(status_code=409)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)

async def read_directory(
    uri: str,
) -> ReadDirectoryResponse:
    path = uriToPath(uri)
    files = list()
    try:
        for file in scandir(path):
            files.append(ReadDirectoryResponseFilesInner(
                name=file.name,
                isDir=file.is_dir(),
                isSymbolic=file.is_symlink(),
            ))
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)
    return ReadDirectoryResponse(files=files)

async def read_file(
    uri: str,
) -> file:
    path = uriToPath(uri)

    try:
        fileReader = open(path, 'rb')
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)

    def get_generator(chunk_size=1024):
        try:
            while True:
                data = fileReader.read(chunk_size)
                if not data:
                    break
                yield data
        finally:
            fileReader.close()

    return StreamingResponse(
        get_generator(),
        media_type='application/binary',
    )

async def rename(
    oldUri: str,
    newUri: str,
    overwrite: bool,
) -> None:
    old = uriToPath(oldUri)
    new = uriToPath(newUri)
    if not exists(old):
        raise HTTPException(status_code=404)
    if exists(new):
        if overwrite:
             if isfile(new):
                 remove(new)
             else:
                 rmtree(new)
        else:
            raise HTTPException(status_code=409)
    try:
        filerename(old, new)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)

async def stat(
    uri: str,
) -> StatResponse:
    path = uriToPath(uri)
    try:
        statinfo = filestat(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)
    return StatResponse(
        ctimeInMsEpoch=statinfo.st_ctime,
        mtimeInMsEpoch=statinfo.st_mtime,
        isReadonly=osname == 'nt' and statinfo.st_file_attributes & FILE_ATTRIBUTE_READONLY,
        sizeInBytes=statinfo.st_size,
        isDir=S_ISDIR(statinfo.st_mode),
        isSymbolic=S_ISLNK(statinfo.st_mode),
    )

async def write_file(
    uri: str,
    body: str,
    create: bool,
    overwrite: bool,
) -> None:
    path = uriToPath(uri)
    pathExists = exists(path)
    if not pathExists and not create:
        raise HTTPException(status_code=404)
    if pathExists and create and not overwrite:
        raise HTTPException(status_code=409)
    try:
        f = open(path, "w" if overwrite else "a")
        f.write(body)
        f.close()
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        print('Unhandled exception', e)
        raise HTTPException(status_code=400)
