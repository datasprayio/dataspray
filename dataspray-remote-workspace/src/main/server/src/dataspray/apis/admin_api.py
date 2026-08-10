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

from dataspray.impl.admin_api import ping as ping_impl

router = APIRouter()


@router.get(
    "/ping",
    responses={
        200: {"description": "HTTP 200 Ok Request was successful. No response value supplied."},
    },
    tags=["Admin"],
    response_model_by_alias=True,
)
async def ping(
) -> None:
    return await ping_impl()
