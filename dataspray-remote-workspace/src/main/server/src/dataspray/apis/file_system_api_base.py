# coding: utf-8

from typing import ClassVar, Dict, List, Tuple  # noqa: F401

from dataspray.models.read_directory_response import ReadDirectoryResponse
from dataspray.models.stat_response import StatResponse


class BaseFileSystemApi:
    subclasses: ClassVar[Tuple] = ()

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)
        BaseFileSystemApi.subclasses = BaseFileSystemApi.subclasses + (cls,)
    def copy(
        self,
        source: str,
        destination: str,
        overwrite: bool,
    ) -> None:
        ...


    def create_directory(
        self,
        uri: str,
    ) -> None:
        ...


    def delete(
        self,
        uri: str,
        recursive: bool,
    ) -> None:
        ...


    def read_directory(
        self,
        uri: str,
    ) -> ReadDirectoryResponse:
        ...


    def read_file(
        self,
        uri: str,
    ) -> file:
        ...


    def rename(
        self,
        old_uri: str,
        new_uri: str,
        overwrite: bool,
    ) -> None:
        ...


    def stat(
        self,
        uri: str,
    ) -> StatResponse:
        ...


    def write_file(
        self,
        uri: str,
        body: str,
        create: bool,
        overwrite: bool,
    ) -> None:
        ...
