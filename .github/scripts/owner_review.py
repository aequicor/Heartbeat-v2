"""Approve the exact PR head attested to by the repository owner."""

import argparse
import json
import os
import re
import sys
from functools import partial
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


COMMAND = re.compile(
    r"/(?:reviewed|revied) (?:"
    r"(?P<sha>[0-9a-f]{7,40})"
    r"|\[(?P<linked_sha>[0-9a-f]{7,40})\]\((?P<link>https://github\.com/[^()\s]+)\)"
    r"|(?P<url>https://github\.com/[^()\s]+/(?P<url_sha>[0-9a-f]{40})))",
    re.ASCII,
)
CHECKLIST_HEADING = "## REVIEW CHECKLIST"
CHECKLIST_MARKER = "<!-- owner-review-head: "
TASK_ITEM = re.compile(r"- \[([ xX])\] (\S.*)")
COMMIT_GROUP = re.compile(
    r"\*\*\[([0-9a-f]{7,40})\]\((https://github\.com/[^()\s]+/commit/([0-9a-f]{40}))\)\*\* — \S.*"
)
CONVERSATIONS_QUERY = """query($owner: String!, $name: String!, $number: Int!, $after: String) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      reviewThreads(first: 100, after: $after) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          isResolved
          isOutdated
          path
          line
          comments(first: 1) {
            nodes {
              url
            }
          }
        }
      }
    }
  }
}"""


class ReviewRejected(Exception):
    """The comment or PR does not satisfy the review policy."""


def parse_command(body):
    match = COMMAND.fullmatch(body.strip())
    if not match:
        raise ReviewRejected("Оставьте одну строку `/reviewed <SHA>`. Укажите не менее 7 символов SHA или ссылку на коммит.")
    sha = match["sha"] or match["linked_sha"] or match["url_sha"]
    return sha, match["link"] or match["url"]


def validate_checklist(body, sha, repository_url):
    lines = [line.rstrip() for line in (body or "").splitlines() if line.strip()]
    if lines.count(CHECKLIST_HEADING) != 1:
        raise ReviewRejected("Добавьте в конец описания PR один раздел REVIEW CHECKLIST.")

    start = lines.index(CHECKLIST_HEADING) + 1
    marker = f"{CHECKLIST_MARKER}{sha} -->"
    if lines[start : start + 1] != [marker]:
        raise ReviewRejected("Обновите маркер `owner-review-head` в описании PR до полного SHA текущего HEAD.")

    format_error = "Поместите непустой список с галочками или группы коммитов с пунктами в последний раздел REVIEW CHECKLIST."
    entries = []
    commits = set()
    needs_item = False
    unchecked_count = 0
    for line in lines[start + 1 :]:
        item = TASK_ITEM.fullmatch(line)
        if item:
            entries.append(f"- [x] {item[2]}")
            unchecked_count += item[1] == " "
            needs_item = False
            continue

        group = COMMIT_GROUP.fullmatch(line)
        if not group or needs_item or (entries and not commits):
            raise ReviewRejected(format_error)
        label, url, commit = group.groups()
        if not commit.startswith(label) or url != f"{repository_url}/commit/{commit}":
            raise ReviewRejected("Укажите в заголовке группы SHA и ссылку на тот же коммит этого репозитория.")
        if commit in commits:
            raise ReviewRejected(f"Объедините проверки коммита `{label}` в одну группу.")
        commits.add(commit)
        entries.append(line)
        needs_item = True

    if not entries or needs_item:
        raise ReviewRejected(format_error)
    if unchecked_count:
        raise ReviewRejected(f"Отметьте все пункты в REVIEW CHECKLIST. Пунктов без отметки — {unchecked_count}.")
    return entries


def send(request):
    try:
        with urlopen(request, timeout=15) as response:
            return json.load(response)
    except HTTPError as error:
        raise ReviewRejected(f"Запрос к GitHub API завершился ошибкой HTTP {error.code}.") from error
    except URLError as error:
        raise ReviewRejected("GitHub API недоступен. Повторите команду позже.") from error


def headers(token):
    return {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def github_request(path, payload=None, *, token):
    return send(
        Request(
            f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}{path}",
            data=None if payload is None else json.dumps(payload).encode("utf-8"),
            headers=headers(token),
        )
    )


def github_graphql(query, variables, *, token):
    url = os.environ.get("GITHUB_GRAPHQL_URL") or f"{os.environ['GITHUB_API_URL']}/graphql"
    payload = send(
        Request(
            url,
            data=json.dumps({"query": query, "variables": variables}).encode("utf-8"),
            headers=headers(token),
        )
    )
    if payload.get("errors"):
        raise ReviewRejected("GitHub API отклонил запрос GraphQL.")
    return payload["data"]


def unresolved_threads(graphql, number):
    """Return review conversations that GitHub still counts as open."""
    owner, _, name = os.environ["GITHUB_REPOSITORY"].partition("/")
    threads = []
    cursor = None
    while True:
        data = graphql(
            CONVERSATIONS_QUERY,
            {"owner": owner, "name": name, "number": number, "after": cursor},
        )
        page = data["repository"]["pullRequest"]["reviewThreads"]
        threads.extend(thread for thread in page["nodes"] if not thread["isResolved"])
        cursor = page["pageInfo"]["endCursor"]
        if not page["pageInfo"]["hasNextPage"] or not cursor:
            return threads


def describe_threads(threads):
    def label(thread):
        where = thread.get("path") or "PR"
        if thread.get("line"):
            where = f"{where}:{thread['line']}"
        comments = thread["comments"]["nodes"]
        text = f"[`{where}`]({comments[0]['url']})" if comments else f"`{where}`"
        return f"{text} (устаревшее)" if thread["isOutdated"] else text

    shown = ", ".join(label(thread) for thread in threads[:5])
    note = f"Незакрытых обсуждений — {len(threads)}. Закройте их: {shown}"
    if len(threads) > 5:
        note += f" и ещё {len(threads) - 5}"
    return f"{note}."


def conversation_summary(graphql, number):
    """Describe open conversations without blocking the review result."""
    try:
        threads = unresolved_threads(graphql, number)
    except ReviewRejected as error:
        print(f"Conversation check was skipped: {error}", file=sys.stderr)
        return ""
    return describe_threads(threads) if threads else ""



# GitHub reaction contents are limited to the emoji below. ✅ and ❌ are not reactions,
# so the outcome is signalled with 👍 (+1) for success and 👎 (-1) for rejection.
REACTION_ACK = "eyes"
REACTION_SUCCESS = "+1"
REACTION_FAILURE = "-1"


def react(api, comment_id, content):
    """Add a reaction without letting a failed call block the review."""
    try:
        api(f"/issues/comments/{comment_id}/reactions", {"content": content})
    except ReviewRejected as error:
        print(f"Reaction {content} was not added: {error}", file=sys.stderr)


def acknowledge(event, api):
    react(api, event["comment"]["id"], REACTION_ACK)


def validate(event, api):
    repository = event["repository"]
    owner = repository["owner"]
    comment = event["comment"]
    issue = event["issue"]
    if event["action"] != "created" or not issue.get("pull_request"):
        raise ReviewRejected("Оставьте новый комментарий в PR.")
    if owner["type"] != "User" or comment["user"]["id"] != owner["id"]:
        raise ReviewRejected("Подтвердить ревью может только владелец репозитория.")

    submitted_sha, commit_url = parse_command(comment["body"])
    pr_path = f"/pulls/{issue['number']}"
    current_comment = api(f"/issues/comments/{comment['id']}")
    if (
        current_comment["user"]["id"] != owner["id"]
        or current_comment["body"] != comment["body"]
        or current_comment["updated_at"] != comment["updated_at"]
    ):
        raise ReviewRejected("Комментарий изменился. Отправьте новый комментарий с подтверждением.")

    pr = api(pr_path)
    if pr["base"]["repo"]["id"] != repository["id"] or pr["base"]["ref"] != "master":
        raise ReviewRejected("Подтверждение доступно для PR в master этого репозитория.")
    if pr["state"] != "open" or pr["draft"]:
        raise ReviewRejected("PR должен быть открыт и переведён в Ready for review.")
    sha = pr["head"]["sha"]
    if not sha.startswith(submitted_sha):
        raise ReviewRejected(f"SHA устарел. Проверьте текущий коммит `{sha[:7]}` и подтвердите его.")
    if commit_url and commit_url not in (
        f"{repository['html_url']}/commit/{sha}",
        f"{pr['html_url']}/commits/{sha}",
    ):
        raise ReviewRejected("Ссылка должна вести на текущий коммит этого PR. Можно указать SHA обычным текстом.")
    if submitted_sha != sha and api(f"/commits/{submitted_sha}")["sha"] != sha:
        raise ReviewRejected(f"Короткий SHA неоднозначен. Укажите полный SHA `{sha}`.")
    submitted_checklist = validate_checklist(issue.get("body"), sha, repository["html_url"])
    current_checklist = validate_checklist(pr.get("body"), sha, repository["html_url"])
    if current_checklist != submitted_checklist:
        raise ReviewRejected("Чек-лист изменился после команды. Проверьте его и отправьте новый комментарий.")

    return pr_path, sha


def approve(event, read_api, app_api):
    # The App publishes only the required status. Pull request comments belong to
    # GitHub Actions, so the conversation keeps a single bot author.
    _, sha = validate(event, read_api)
    comment_url = event["comment"]["html_url"]

    # A push during approval must never receive a status on its new head.
    _, current_sha = validate(event, read_api)
    if current_sha != sha:
        raise ReviewRejected("HEAD изменился во время обработки. Проверьте текущий коммит и подтвердите его.")
    app_api(
        f"/statuses/{sha}",
        {
            "state": "success",
            "context": "owner-review",
            "description": "Repository owner reviewed this commit.",
            "target_url": comment_url,
        },
    )
    return sha


def post_feedback(event, api, *, outcome, merge_outcome, reason, app_failed, run_url, conversations=""):
    if outcome == "success":
        result = "**Ревью принято.** Статус `owner-review` выставлен для проверенного коммита."
        if conversations:
            result += f" {conversations}"
            if merge_outcome == "success":
                result += " GitHub выполнит squash после закрытия обсуждений."
            else:
                result += " Автослияние не запрошено. Включите его или выполните слияние после закрытия обсуждений."
        elif merge_outcome == "success":
            result += " GitHub выполнит squash, когда пройдут обязательные проверки и будут закрыты обсуждения."
        elif merge_outcome == "failure":
            result += " Запрос автослияния завершился ошибкой. Подробности есть в журнале запуска."
    elif reason:
        result = f"**Ревью не принято.** {reason}"
    elif app_failed:
        result = "**Ревью не принято.** GitHub Actions не смог получить токен приложения."
    else:
        result = "**Ревью не принято.** Проверка завершилась с ошибкой. Подробности есть в журнале запуска."

    command_url = event["comment"]["html_url"]
    body = f"{result}\n\n[Команда]({command_url}) · [Журнал проверки]({run_url})"
    api(f"/issues/{event['issue']['number']}/comments", {"body": body})
    react(api, event["comment"]["id"], REACTION_SUCCESS if outcome == "success" else REACTION_FAILURE)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("validate", "approve", "feedback", "acknowledge"))
    args = parser.parse_args()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    read_api = partial(github_request, token=os.environ["GH_TOKEN"])
    read_graphql = partial(github_graphql, token=os.environ["GH_TOKEN"])

    try:
        if args.action == "acknowledge":
            acknowledge(event, read_api)
        elif args.action == "validate":
            validate(event, read_api)
        elif args.action == "approve":
            app_api = partial(github_request, token=os.environ["OWNER_REVIEW_WRITE_TOKEN"])
            sha = approve(event, read_api, app_api)
            with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
                print(f"reviewed_sha={sha}", file=stream)
        else:
            post_feedback(
                event,
                read_api,
                outcome=os.environ["REVIEW_OUTCOME"],
                merge_outcome=os.environ["MERGE_OUTCOME"],
                reason=os.environ["REVIEW_ERROR"],
                app_failed=os.environ["APP_OUTCOME"] == "failure",
                run_url=f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}"
                f"/actions/runs/{os.environ['GITHUB_RUN_ID']}",
                conversations=conversation_summary(read_graphql, event["issue"]["number"]),
            )
    except ReviewRejected as error:
        if args.action != "feedback" and (output := os.environ.get("GITHUB_OUTPUT")):
            with Path(output).open("a", encoding="utf-8") as stream:
                print(f"error={error}", file=stream)
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
