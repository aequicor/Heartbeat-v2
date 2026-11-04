"""Approve the exact PR head attested to by the repository owner."""

import argparse
import json
import os
import re
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CHECKLIST = (
    "Проверены весь diff и соответствие требованиям.",
    "Проверены границы модулей, интерфейсы и потоки данных.",
    "Проверена обработка ошибок.",
    "Проверены результаты тестов.",
)
COMMAND = re.compile(r"/reviewed ([0-9a-f]{40})", re.ASCII)


class ReviewRejected(Exception):
    """The comment or PR does not satisfy the review policy."""


def reviewed_sha(body):
    lines = [line.strip() for line in body.splitlines() if line.strip()]
    match = COMMAND.fullmatch(lines[0]) if lines else None
    if not match:
        raise ReviewRejected("Expected /reviewed followed by the full head SHA.")
    checklist = [f"- [x] {item}" for item in CHECKLIST]
    if lines[1:] != checklist:
        raise ReviewRejected("Complete the four checklist lines in the template order.")
    return match.group(1)


def github_request(path, payload=None):
    request = Request(
        f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}{path}",
        data=None if payload is None else json.dumps(payload).encode("utf-8"),
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urlopen(request, timeout=15) as response:
            return json.load(response)
    except HTTPError as error:
        raise ReviewRejected(f"GitHub API {path} returned HTTP {error.code}.") from error
    except URLError as error:
        raise ReviewRejected(f"GitHub API {path} is unavailable.") from error


def validate(event, api):
    repository = event["repository"]
    owner = repository["owner"]
    comment = event["comment"]
    issue = event["issue"]
    if event["action"] != "created" or not issue.get("pull_request"):
        raise ReviewRejected("Expected a new comment on a pull request.")
    if owner["type"] != "User" or comment["user"]["id"] != owner["id"]:
        raise ReviewRejected("Only the repository owner can attest to a review.")

    sha = reviewed_sha(comment["body"])
    pr_path = f"/pulls/{issue['number']}"
    current_comment = api(f"/issues/comments/{comment['id']}")
    if (
        current_comment["user"]["id"] != owner["id"]
        or current_comment["body"] != comment["body"]
        or current_comment["updated_at"] != comment["updated_at"]
    ):
        raise ReviewRejected("The comment changed. Submit a new review comment.")

    pr = api(pr_path)
    if pr["base"]["repo"]["id"] != repository["id"] or pr["base"]["ref"] != "master":
        raise ReviewRejected("The pull request must target this repository's master.")
    if pr["state"] != "open" or pr["draft"]:
        raise ReviewRejected("The pull request must be open and ready for review.")
    if pr["head"]["sha"] != sha:
        raise ReviewRejected("The review SHA is stale. Review the current head.")

    return pr_path, sha


def approve(event, api):
    pr_path, sha = validate(event, api)
    comment_url = event["comment"]["html_url"]
    review = api(
        f"{pr_path}/reviews",
        {"event": "APPROVE", "commit_id": sha, "body": f"Owner attestation: {comment_url}"},
    )
    if review["state"] != "APPROVED" or review["commit_id"] != sha:
        raise ReviewRejected("GitHub did not record the approval for the reviewed SHA.")

    # A push during approval must never receive a status on its new head.
    validate(event, api)
    api(
        f"/statuses/{sha}",
        {
            "state": "success",
            "context": "owner-review",
            "description": "Repository owner reviewed this commit.",
            "target_url": comment_url,
        },
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "approve"))
    args = parser.parse_args()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))

    try:
        if args.action == "validate":
            validate(event, github_request)
        else:
            approve(event, github_request)
    except ReviewRejected as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
