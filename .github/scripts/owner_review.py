"""Approve the exact PR head attested to by the repository owner."""

import argparse
import json
import os
import re
from functools import partial
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


COMMAND = re.compile(r"/reviewed ([0-9a-f]{7,40})", re.ASCII)
CHECKLIST_HEADING = "## REVIEW CHECKLIST"
CHECKLIST_MARKER = "<!-- owner-review-head: "
CHECKED_ITEM = re.compile(r"- \[[xX]\] (\S.*)")


class ReviewRejected(Exception):
    """The comment or PR does not satisfy the review policy."""


def reviewed_sha(body):
    match = COMMAND.fullmatch(body.strip())
    if not match:
        raise ReviewRejected("Post one line with /reviewed and at least 7 hex digits of the head SHA.")
    return match.group(1)


def validate_checklist(body, sha):
    lines = [line.rstrip() for line in (body or "").splitlines() if line.strip()]
    if lines.count(CHECKLIST_HEADING) != 1:
        raise ReviewRejected("Add one final REVIEW CHECKLIST section to the PR description.")

    start = lines.index(CHECKLIST_HEADING) + 1
    marker = f"{CHECKLIST_MARKER}{sha} -->"
    if lines[start : start + 1] != [marker]:
        raise ReviewRejected("Prepare the checklist in the PR description for the current head.")

    items = [CHECKED_ITEM.fullmatch(line) for line in lines[start + 1 :]]
    if not items or not all(items):
        raise ReviewRejected("Complete every checklist item and keep REVIEW CHECKLIST last.")
    return [item.group(1) for item in items]


def github_request(path, payload=None, *, token):
    request = Request(
        f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}{path}",
        data=None if payload is None else json.dumps(payload).encode("utf-8"),
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
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

    submitted_sha = reviewed_sha(comment["body"])
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
    sha = pr["head"]["sha"]
    if not sha.startswith(submitted_sha):
        raise ReviewRejected("The review SHA is stale. Review the current head.")
    if submitted_sha != sha and api(f"/commits/{submitted_sha}")["sha"] != sha:
        raise ReviewRejected("The short SHA does not resolve to the current head. Use the full SHA.")
    submitted_items = validate_checklist(issue.get("body"), sha)
    current_items = validate_checklist(pr.get("body"), sha)
    if current_items != submitted_items:
        raise ReviewRejected("The checklist changed after the comment. Review it and comment again.")

    return pr_path, sha


def approve(event, read_api, app_api):
    pr_path, sha = validate(event, read_api)
    comment_url = event["comment"]["html_url"]
    review = app_api(
        f"{pr_path}/reviews",
        {"event": "APPROVE", "commit_id": sha, "body": f"Owner attestation: {comment_url}"},
    )
    if review["state"] != "APPROVED" or review["commit_id"] != sha:
        raise ReviewRejected("GitHub did not record the approval for the reviewed SHA.")

    # A push during approval must never receive a status on its new head.
    _, current_sha = validate(event, read_api)
    if current_sha != sha:
        raise ReviewRejected("The head changed during approval. Review the current head.")
    app_api(
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
    read_api = partial(github_request, token=os.environ["GH_TOKEN"])

    try:
        if args.action == "validate":
            validate(event, read_api)
        else:
            app_api = partial(github_request, token=os.environ["OWNER_REVIEW_WRITE_TOKEN"])
            approve(event, read_api, app_api)
    except ReviewRejected as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
