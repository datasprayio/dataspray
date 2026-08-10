# coding: utf-8

from typing import Dict, List  # noqa: F401

from fastapi import (  # noqa: F401
    APIRouter,
    Body,
    Cookie,
    Depends,
    Form,
    Header,
    Path,
    Query,
    Response,
    Security,
    status,
)
from pydantic import StrictBytes as file

from dataspray.models.extra_models import TokenModel  # noqa: F401
from dataspray.models.read_directory_response import ReadDirectoryResponse
from dataspray.models.stat_response import StatResponse

from dataspray.impl.file_system_api import copy as copy_impl
from dataspray.impl.file_system_api import create_directory as create_directory_impl
from dataspray.impl.file_system_api import delete as delete_impl
from dataspray.impl.file_system_api import read_directory as read_directory_impl
from dataspray.impl.file_system_api import read_file as read_file_impl
from dataspray.impl.file_system_api import rename as rename_impl
from dataspray.impl.file_system_api import stat as stat_impl
from dataspray.impl.file_system_api import write_file as write_file_impl

router = APIRouter()


@router.patch(
    "/filesystem/copy",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
        404: {"description": "Source doesn&#39;t exist or parent of destination doesn&#39;t exist"},
        409: {"description": "Destination exists and the overwrite option is not true"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def copy(
    source: str = Query(None, description="", alias="source"),
    destination: str = Query(None, description="", alias="destination"),
    overwrite: bool = Query(None, description="", alias="overwrite"),
) -> None:
    return await copy_impl(source, destination, overwrite)


@router.put(
    "/filesystem/createDirectory",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
        404: {"description": "Parent of uri doesn&#39;t exist"},
        409: {"description": "Uri already exists"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def create_directory(
    uri: str = Query(None, description="", alias="uri"),
) -> None:
    return await create_directory_impl(uri)


@router.delete(
    "/filesystem/delete",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
        404: {"description": "Uri doesn&#39;t exist"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def delete(
    uri: str = Query(None, description="", alias="uri"),
    recursive: bool = Query(None, description="", alias="recursive"),
) -> None:
    return await delete_impl(uri, recursive)


@router.get(
    "/filesystem/readDirectory",
    responses={
        200: {"model": ReadDirectoryResponse, "description": "Ok"},
        404: {"description": "Uri doesn&#39;t exist"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def read_directory(
    uri: str = Query(None, description="", alias="uri"),
) -> ReadDirectoryResponse:
    return await read_directory_impl(uri)


@router.get(
    "/filesystem/readFile",
    responses={
        200: {"model": file, "description": "Ok"},
        404: {"description": "Uri doesn&#39;t exist"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def read_file(
    uri: str = Query(None, description="", alias="uri"),
) -> file:
    return await read_file_impl(uri)


@router.patch(
    "/filesystem/rename",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
        404: {"description": "oldUri doesn&#39;t exist or parent of newUri doesn&#39;t exist"},
        409: {"description": "newUri exists and the overwrite option is not true"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def rename(
    old_uri: str = Query(None, description="", alias="oldUri"),
    new_uri: str = Query(None, description="", alias="newUri"),
    overwrite: bool = Query(None, description="", alias="overwrite"),
) -> None:
    return await rename_impl(old_uri, new_uri, overwrite)


@router.get(
    "/filesystem/stat",
    responses={
        200: {"model": StatResponse, "description": "Ok"},
        404: {"description": "uri doesn&#39;t exist"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def stat(
    uri: str = Query(None, description="", alias="uri"),
) -> StatResponse:
    return await stat_impl(uri)


@router.put(
    "/filesystem/writeFile",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
        404: {"description": "uri doesn&#39;t exist and create is not set or parent of uri doesn&#39;t exist and create is set"},
        409: {"description": "uri already exists, create is set but overwrite is not set"},
        403: {"description": "Permissions aren&#39;t sufficient"},
    },
    tags=["FileSystem"],
    response_model_by_alias=True,
)
async def write_file(
    uri: str = Query(None, description="", alias="uri"),
    body: str = Body(None, description=""),
    create: bool = Query(None, description="", alias="create"),
    overwrite: bool = Query(None, description="", alias="overwrite"),
) -> None:
    return await write_file_impl(uri, body, create, overwrite)
