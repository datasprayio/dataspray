# coding: utf-8

from subprocess import Popen, STDOUT, PIPE
from dataspray.impl.config import getWorkingDirectory
from fastapi.responses import StreamingResponse
import time
from pydantic import StrictBytes as file

async def execute(
    cmd: str,
) -> file:
    def get_generator():
        proc = Popen(
            cmd,
            stdout=PIPE,
            stderr=STDOUT,
            cwd=getWorkingDirectory(),
            shell=True
        )
        start = time.time()

        for line in iter(proc.stdout.readline, ""):
            if not line:
                break
            yield line

        proc.stdout.close()
        try:
            return_code = proc.wait(3)
        except TimeoutExpired:
            proc.kill()
            return_code = proc.returncode

        elapsedInSec = time.time() - start
        if elapsedInSec < 1:
            elapsed = elapsedInSec * 1000
            elapsedUnit = 'millis'
        elif elapsedInSec >= 60:
            elapsed = elapsedInSec / 60
            elapsedUnit = 'minutes'
        else:
            elapsed = elapsedInSec
            elapsedUnit = 'seconds'

        yield "status %s in %.2f %s" % (return_code, elapsed, elapsedUnit)
    return StreamingResponse(
        get_generator(),
        media_type='application/binary',
    )
