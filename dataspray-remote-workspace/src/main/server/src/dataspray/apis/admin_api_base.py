# coding: utf-8

from typing import ClassVar, Dict, List, Tuple  # noqa: F401



class BaseAdminApi:
    subclasses: ClassVar[Tuple] = ()

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)
        BaseAdminApi.subclasses = BaseAdminApi.subclasses + (cls,)
    def ping(
        self,
    ) -> None:
        ...
