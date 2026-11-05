"""Block Codex task prompts until a linked worktree is clean and current."""

import json
import os
from pathlib import Path
import subprocess
import sys


SYNC_PROMPT = "Синхронизируй worktree"


def git(cwd, *args, check=True):
    return subprocess.run(
        ["git", "-C", cwd, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
        timeout=15,
        check=check,
    )


def block(reason):
    return {"decision": "block", "reason": reason}


def check_worktree(event):
    cwd = event["cwd"]
    git_dir, common_dir = git(
        cwd, "rev-parse", "--path-format=absolute", "--git-dir", "--git-common-dir"
    ).stdout.splitlines()
    if Path(git_dir).resolve() == Path(common_dir).resolve():
        return {}

    if git(cwd, "status", "--porcelain", "--untracked-files=all").stdout:
        return block("В worktree есть незакоммиченные изменения. Сначала приведи его в чистое состояние.")

    git(cwd, "fetch", "--no-tags", "origin", "refs/heads/master:refs/remotes/origin/master")
    ancestor = git(cwd, "merge-base", "--is-ancestor", "origin/master", "HEAD", check=False)
    if ancestor.returncode == 0:
        return {}
    if ancestor.returncode != 1:
        return block("Git не смог проверить, содержит ли HEAD актуальный origin/master.")

    if event["prompt"].strip().casefold() == SYNC_PROMPT.casefold():
        return {
            "hookSpecificOutput": {
                "hookEventName": "UserPromptSubmit",
                "additionalContext": (
                    "Выполни только синхронизацию чистого worktree по .github/AGENTS.md. "
                    "Влей origin/master и сам разреши конфликты. Обычную задачу начни после новой проверки."
                ),
            }
        }

    return block(
        "Ветка worktree отстаёт от origin/master. Отправь отдельный запрос "
        f"«{SYNC_PROMPT}», затем повтори исходную задачу."
    )


def main():
    try:
        response = check_worktree(json.load(sys.stdin))
    except subprocess.TimeoutExpired:
        response = block("Проверка Git превысила время ожидания. Повтори запрос.")
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        command = getattr(error, "cmd", None)
        operation = command[3] if command else "проверка"
        response = block(f"Не удалось выполнить Git preflight ({operation}). Проверь доступ к репозиторию и origin.")
    print(json.dumps(response))


if __name__ == "__main__":
    main()
