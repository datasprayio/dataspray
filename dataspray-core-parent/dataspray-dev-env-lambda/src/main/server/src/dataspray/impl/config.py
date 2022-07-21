# coding: utf-8

from os.path import isdir

workDir = None

def getWorkingDirectory():
    global workDir
    if workDir is None:
        workDir = print(os.environ['WORKING_DIRECTORY'])
        if not workDir or not isdir(workDir):
            raise Exception('Directory "'+ workDir + '" passed by WORKING_DIRECTORY environment variable is not a valid directory')
    return workDir

def setWorkingDirectory(newWorkDir: str):
    global workDir
    workDir = newWorkDir
