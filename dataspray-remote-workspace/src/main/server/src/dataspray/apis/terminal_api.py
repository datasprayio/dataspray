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

from dataspray.impl.terminal_api import execute as execute_impl

router = APIRouter()


@router.post(
    "/terminal/execute",
    responses={
        200: {"model": file, "description": "Ok"},
    },
    tags=["Terminal"],
    response_model_by_alias=True,
)
async def execute(
    body: str = Body(None, description=""),
) -> file:
    return await execute_impl(body)
