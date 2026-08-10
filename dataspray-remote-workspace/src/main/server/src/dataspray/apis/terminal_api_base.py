# coding: utf-8

from typing import ClassVar, Dict, List, Tuple  # noqa: F401



class BaseTerminalApi:
    subclasses: ClassVar[Tuple] = ()

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)
        BaseTerminalApi.subclasses = BaseTerminalApi.subclasses + (cls,)
    def execute(
        self,
        body: str,
    ) -> file:
        ...
