# coding: utf-8

import errno
from shutil import copy2, rmtree
from os import makedirs, remove, listdir, rename, stat
from os.path import isfile, islink, join, exists, dirname, normpath
from stat import *
from fastapi import FastAPI, HTTPException
from dataspray.models.read_directory_response import ReadDirectoryResponse
from dataspray.models.read_directory_response_files_inner import ReadDirectoryResponseFilesInner
from dataspray.models.stat_response import StatResponse
from dataspray.impl.util import uriToPath
from pydantic import StrictBytes as file

async def copy(
    sourceUri: str,
    destinationUri: str,
    overwrite: bool,
) -> None:
    source = uriToPath(sourceUri)
    destination = uriToPath(destinationUri)
    if exists(destination):
        if overwrite:
            if isfile(destination):
                remove(destination)
            else:
                rmtree(destination)
        else:
            raise HTTPException(status_code=409)
    try:
        copy2(source, destination)
    except FileNotFoundError:
        raise HTTPException(status_code=404)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)

async def create_directory(
    uri: str,
) -> None:
    path = uriToPath(uri)
    try:
        makedirs(dirname(path))
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.EEXIST:
            raise HTTPException(status_code=409)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=409)
        raise HTTPException(status_code=400)

async def delete(
    uri: str,
    recursive: bool,
) -> None:
    path = uriToPath(uri)
    try:
        remove(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)

async def read_directory(
    uri: str,
) -> ReadDirectoryResponse:
    path = uriToPath(uri)
    try:
        fileNames = listdir(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)
    files = list()
    for fileName in fileNames:
        filePath = normpath(join(path, fileName))
        files.append(ReadDirectoryResponseFilesInner(
            name=fileName,
            is_dir=not isfile(filePath),
            is_symbolic=not islink(filePath),
        ))
    return ReadDirectoryResponse(files)

async def read_file(
    uri: str,
) -> file:
    path = uriToPath(uri)
    try:
        with open(path, 'rb') as fileReader:
            return fileReader.read()
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)

async def rename(
    oldUri: str,
    newUri: str,
    overwrite: bool,
) -> None:
    old = uriToPath(oldUri)
    new = uriToPath(newUri)
    if exists(new):
        if overwrite:
             if isfile(new):
                 remove(new)
             else:
                 rmtree(new)
        else:
            raise HTTPException(status_code=409)
    try:
        rename(old, new)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)

async def stat(
    uri: str,
) -> StatResponse:
    path = uriToPath(uri)
    try:
        statinfo = stat(path)
    except OSError as e:
        if e.errno == errno.EPERM:
            raise HTTPException(status_code=403)
        if e.errno == errno.ENOENT:
            raise HTTPException(status_code=404)
        raise HTTPException(status_code=400)
    return StatResponse(
        ctime_in_ms_epoch=statinfo.st_ctime,
        mtime_in_ms_epoch=statinfo.st_mtime,
        isReadonly=statinfo.st_file_attributes & FILE_ATTRIBUTE_READONLY,
        size_in_bytes=statinfo.st_size,
        is_dir=S_ISDIR(statinfo.st_mode),
        is_symbolic=S_ISLNK(statinfo.st_mode),
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
        raise HTTPException(status_code=400)
