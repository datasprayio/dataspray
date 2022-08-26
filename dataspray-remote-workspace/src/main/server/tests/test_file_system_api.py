# coding: utf-8

from fastapi.testclient import TestClient
from dataspray.impl.util import pathToUri
from dataspray.models.read_directory_response import ReadDirectoryResponse  # noqa: F401
from dataspray.models.stat_response import StatResponse
from dataspray.impl.config import getWorkingDirectory

def test_all(client: TestClient):

    # Create file1
    response = write_file(client, 'file1', 'file1 content1', False, False)
    assert response.status_code == 404
    response = write_file(client, 'file1', 'file1 content1', True, False)
    assert response.status_code == 200
    response = write_file(client, 'file1', 'file1 content2', True, False)
    assert response.status_code == 409
    response = write_file(client, 'file1', 'file1 content2', True, True)
    assert response.status_code == 200

    # Read file1
    response = read_file(client, 'file2')
    assert response.status_code == 404
    response = read_file(client, 'file1')
    assert response.status_code == 200
    assert response.content == b'file1 content2';

    # Create dir1
    response = create_directory(client, "dir1/dir1")
    assert response.status_code == 404
    response = create_directory(client, "dir1")
    assert response.status_code == 200
    response = create_directory(client, "dir1")
    assert response.status_code == 409

    # Copy file1 to dir1/file1
    response = copy(client, "file1", "dir1", False)
    assert response.status_code == 200
    response = copy(client, "file1", "dir1/file1", False)
    assert response.status_code == 409
    response = copy(client, "file1", "dir1/file1", True)
    assert response.status_code == 200

    # Copy dir1 to dir2
    response = copy(client, "dir1", "dir2/dir2", False)
    assert response.status_code == 404
    response = copy(client, "dir1", "dir2", False)
    assert response.status_code == 200
    response = copy(client, "dir1", "dir2", False)
    assert response.status_code == 409
    response = copy(client, "dir1", "dir2", True)
    assert response.status_code == 200

    # Copy dir2 to dir1
    response = copy(client, "dir2", "dir1/dir2", True)
    assert response.status_code == 200

    # Read dir1
    response = read_directory(client, "dir1")
    assert response.status_code == 200
    directory = ReadDirectoryResponse.parse_obj(response.json())
    assert len(directory.files) == 2
    assertDirectoryResponseContains(directory, 'file1', False)
    assertDirectoryResponseContains(directory, 'dir2', True)

    # Rename dir2 dir3
    response = rename(client, "dir5", "dir3", False)
    assert response.status_code == 404
    response = rename(client, "dir2", "dir3", False)
    assert response.status_code == 200

    # Rename file1 file3
    response = rename(client, "file1", "file3", False)
    assert response.status_code == 200

    # Stat dir5
    response = stat(client, "dir5")
    assert response.status_code == 404

    # Stat dir3
    response = stat(client, "dir3")
    assert response.status_code == 200
    statResponse = StatResponse.parse_obj(response.json())
    assert statResponse.is_dir
    assert not statResponse.is_readonly
    assert not statResponse.is_symbolic
    assert statResponse.size_in_bytes >= 0

    # Stat file3
    response = stat(client, "file3")
    assert response.status_code == 200
    statResponse = StatResponse.parse_obj(response.json())
    assert not statResponse.is_dir
    assert not statResponse.is_readonly
    assert not statResponse.is_symbolic
    assert statResponse.size_in_bytes == 14

    # Delete dir1
    response = delete(client, "dir5", False)
    assert response.status_code == 404
    response = delete(client, "dir1", False)
    assert response.status_code == 409
    response = delete(client, "dir1", True)
    assert response.status_code == 200

    # Delete dir3/file1
    response = delete(client, "dir3/file1", True)
    assert response.status_code == 200

    # Read .
    response = read_directory(client, ".")
    assert response.status_code == 200
    directory = ReadDirectoryResponse.parse_obj(response.json())
    assert len(directory.files) == 2
    assertDirectoryResponseContains(directory, 'file3', False)
    assertDirectoryResponseContains(directory, 'dir3', True)


def copy(client: TestClient, src: str, dst: str, overwrite: bool = True):
    response = client.request(
        "PATCH",
        "/filesystem/copy",
        params=[
            ("source", pathToUri(src)),
            ("destination", pathToUri(dst)),
            ("overwrite", overwrite),
        ],
    )
    return response


def create_directory(client: TestClient, path: str):
    response = client.request(
        "PUT",
        "/filesystem/createDirectory",
        params=[
            ("uri", pathToUri(path)),
        ],
    )
    return response

def delete(client: TestClient, path: str, recursive: bool = True):
    response = client.request(
        "DELETE",
        "/filesystem/delete",
        params=[
            ("uri", pathToUri(path)),
            ("recursive", recursive),
        ],
    )
    return response

def read_directory(client: TestClient, path: str):
    response = client.request(
        "GET",
        "/filesystem/readDirectory",
        params=[
            ("uri", pathToUri(path)),
        ],
    )
    return response

def read_file(client: TestClient, path: str):
    response = client.request(
        "GET",
        "/filesystem/readFile",
        params=[
            ("uri", pathToUri(path)),
        ],
    )
    return response

def rename(client: TestClient, oldPath: str, newPath: str, overwrite: bool = True):
    response = client.request(
        "PATCH",
        "/filesystem/rename",
        params=[
            ("old_uri", pathToUri(oldPath)),
            ("new_uri", pathToUri(newPath)),
            ("overwrite", overwrite),
        ],
    )
    return response

def stat(client: TestClient, path: str):
    response = client.request(
        "GET",
        "/filesystem/stat",
        params=[
            ("uri", pathToUri(path)),
        ],
    )
    return response

def write_file(client: TestClient, path: str, content: str, create: bool = False, overwrite: bool = True):
    response = client.request(
        "PUT",
        "/filesystem/writeFile",
        json=content,
        params=[
            ("uri", pathToUri(path)),
            ("create", create),
            ("overwrite", overwrite),
        ],
    )
    return response

def assertDirectoryResponseContains(response: ReadDirectoryResponse, fileName: str, isDir: bool):
    for element in response.files:
        if not element.name == fileName:
            continue
        if not element.is_dir == isDir:
            continue
        return True
    return False
