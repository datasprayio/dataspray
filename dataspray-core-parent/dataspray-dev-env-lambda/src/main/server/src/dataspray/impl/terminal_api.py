# coding: utf-8

import subprocess
import shlex
from dataspray.impl.config import getWorkingDirectory

async def execute(
    cmd: str,
) -> str:
    # TODO stream data as it comes in, different pipes for out err and status
    result = subprocess.run(shlex.split(cmd), cwd=getWorkingDirectory(), shell=True)
    return (result.stdout or '') + (result.stderr or '')
