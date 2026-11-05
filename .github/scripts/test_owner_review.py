import copy
import unittest
from pathlib import Path
from unittest.mock import Mock

from owner_review import ReviewRejected, acknowledge, approve, post_feedback, validate


SHA = "a" * 40
EARLIER_SHA = "b" * 40
REPOSITORY_URL = "https://github.com/aequicor/Heartbeat-v2"
PR_URL = f"{REPOSITORY_URL}/pull/2"
TEMPLATE = Path(__file__).resolve().parents[1] / "pull_request_template.md"
EARLIER_GROUP = f"**[{EARLIER_SHA[:7]}]({REPOSITORY_URL}/commit/{EARLIER_SHA})** — Расписание"
HEAD_GROUP = f"**[{SHA[:7]}]({REPOSITORY_URL}/commit/{SHA})** — Проверка ревью"


class OwnerReviewTest(unittest.TestCase):
    def setUp(self):
        self.checked_body = (
            TEMPLATE.read_text(encoding="utf-8")
            .replace("<полный HEAD SHA>", SHA).rstrip()
            + "\n\n- [x] В расписании beta указан запуск в 01:00 UTC."
            + "\n- [x] Комментарий с коротким SHA отклоняется при незаполненном чек-листе."
        )
        self.comment = {
            "id": 10,
            "user": {"id": 1},
            "body": f"/reviewed {SHA}",
            "updated_at": "2026-11-04T10:00:00Z",
            "html_url": "https://github.com/aequicor/Heartbeat-v2/pull/2#issuecomment-10",
        }
        self.event = {
            "action": "created",
            "repository": {"id": 5, "owner": {"id": 1, "type": "User"}, "html_url": REPOSITORY_URL},
            "issue": {
                "number": 2,
                "pull_request": {"url": "https://example.org"},
                "body": self.checked_body,
            },
            "comment": copy.deepcopy(self.comment),
        }
        self.pr = {
            "html_url": PR_URL,
            "state": "open",
            "draft": False,
            "base": {"repo": {"id": 5}, "ref": "master"},
            "head": {"sha": SHA},
            "body": self.checked_body,
        }
        self.api = Mock(side_effect=self.response)
        self.app_api = Mock(side_effect=self.response)
        self.grouped_body = (
            self.checked_body.split("\n\n- [x]", 1)[0]
            + f"\n\n{EARLIER_GROUP}\n\n- [x] Расписание запускает beta в 01:00 UTC."
            + f"\n\n{HEAD_GROUP}\n\n- [x] Пропущенная отметка блокирует ревью."
            + "\n- [X] Устаревший SHA блокирует ревью."
        )

    def response(self, path, payload=None):
        return {
            "/issues/comments/10": self.comment,
            "/pulls/2": self.pr,
            f"/commits/{SHA[:7]}": {"sha": SHA},
            f"/statuses/{SHA}": {},
            "/issues/comments/10/reactions": {},
        }[path]

    def test_approves_and_posts_status_on_reviewed_commit(self):
        self.assertEqual(approve(self.event, self.api, self.app_api), SHA)
        status = self.app_api.call_args.args
        self.assertEqual(status[0], f"/statuses/{SHA}")
        self.assertEqual(status[1]["context"], "owner-review")
        self.assertEqual(self.app_api.call_count, 1)

    def test_short_sha_uses_full_head_for_status(self):
        self.comment["body"] = self.comment["body"].replace(SHA, SHA[:7], 1)
        self.event["comment"]["body"] = self.comment["body"]
        approve(self.event, self.api, self.app_api)
        self.api.assert_any_call(f"/commits/{SHA[:7]}")
        self.assertEqual(self.app_api.call_args.args[0], f"/statuses/{SHA}")

    def test_rejects_sha_shorter_than_seven_digits(self):
        self.event["comment"]["body"] = self.comment["body"].replace(SHA, SHA[:6], 1)
        with self.assertRaisesRegex(ReviewRejected, "не менее 7"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_accepts_short_sha_link_to_the_current_pr_commit(self):
        self.comment["body"] = f"/reviewed [{SHA[:7]}]({PR_URL}/commits/{SHA})"
        self.event["comment"]["body"] = self.comment["body"]
        self.assertEqual(validate(self.event, self.api), ("/pulls/2", SHA))

    def test_accepts_direct_links_and_the_revied_spelling(self):
        for body in (
            f"/reviewed {PR_URL}/commits/{SHA}",
            f"/reviewed {REPOSITORY_URL}/commit/{SHA}",
            f"/revied [{SHA[:7]}]({PR_URL}/commits/{SHA})",
        ):
            with self.subTest(body=body):
                self.comment["body"] = body
                self.event["comment"]["body"] = body
                self.assertEqual(validate(self.event, self.api), ("/pulls/2", SHA))

    def test_rejects_link_to_another_commit_or_repository(self):
        for body in (
            f"/reviewed [{SHA[:7]}]({PR_URL}/commits/{'b' * 40})",
            f"/reviewed https://github.com/other/repo/commit/{SHA}",
            f"/reviewed {REPOSITORY_URL}/pull/3/commits/{SHA}",
        ):
            with self.subTest(body=body):
                self.comment["body"] = body
                self.event["comment"]["body"] = body
                with self.assertRaisesRegex(ReviewRejected, "Ссылка должна вести на текущий коммит"):
                    approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_short_sha_resolved_to_another_commit(self):
        self.comment["body"] = self.comment["body"].replace(SHA, SHA[:7], 1)
        self.event["comment"]["body"] = self.comment["body"]

        def response_with_other_commit(path):
            if path == f"/commits/{SHA[:7]}":
                return {"sha": SHA[:7] + "b" * 33}
            return self.response(path)

        self.api.side_effect = response_with_other_commit
        with self.assertRaisesRegex(ReviewRejected, "Короткий SHA неоднозначен"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_non_owner(self):
        self.event["comment"]["user"]["id"] = 7
        with self.assertRaisesRegex(ReviewRejected, "владелец репозитория"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_rejects_incomplete_checklist(self):
        self.pr["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
        with self.assertRaisesRegex(ReviewRejected, "Пунктов без отметки"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_empty_checklist_or_following_section(self):
        empty_body = self.checked_body.split("\n\n- [x]", 1)[0]
        following_section = self.checked_body + "\n\n## Дополнительный раздел\n"
        for body in (empty_body, following_section):
            with self.subTest(body=body):
                self.pr["body"] = body
                with self.assertRaisesRegex(ReviewRejected, "последний раздел REVIEW CHECKLIST"):
                    approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_changed_checklist_after_the_comment(self):
        self.pr["body"] = self.checked_body.replace("01:00 UTC", "02:00 UTC")
        with self.assertRaisesRegex(ReviewRejected, "Чек-лист изменился"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_approves_checked_commit_groups(self):
        self.event["issue"]["body"] = self.pr["body"] = self.grouped_body
        self.assertEqual(approve(self.event, self.api, self.app_api), SHA)

    def test_rejects_invalid_commit_groups(self):
        _, _, head_items = self.grouped_body.partition(f"\n\n{HEAD_GROUP}")
        empty_last_group = self.grouped_body.removesuffix(head_items)
        cases = (
            (self.grouped_body + f"\n\n{EARLIER_GROUP}\n\n- [x] Повтор.", "в одну группу"),
            (empty_last_group, "последний раздел REVIEW CHECKLIST"),
            (self.grouped_body.replace(f"\n\n{EARLIER_GROUP}", ""), "последний раздел REVIEW CHECKLIST"),
            (
                self.grouped_body.replace(f"{REPOSITORY_URL}/commit/{EARLIER_SHA}", f"{REPOSITORY_URL}/commit/{SHA}"),
                "SHA и ссылку на тот же коммит",
            ),
            (
                self.grouped_body.replace(REPOSITORY_URL, "https://github.com/other/repo"),
                "SHA и ссылку на тот же коммит",
            ),
            (self.grouped_body.replace("- [X] ", "- [ ] "), "Пунктов без отметки"),
        )
        for body, reason in cases:
            with self.subTest(reason=reason, body=body):
                self.event["issue"]["body"] = self.pr["body"] = body
                with self.assertRaisesRegex(ReviewRejected, reason):
                    approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_commit_group_retargeted_after_the_comment(self):
        new_sha = "c" * 40
        self.event["issue"]["body"] = self.grouped_body
        new_group = EARLIER_GROUP.replace(EARLIER_SHA, new_sha).replace(EARLIER_SHA[:7], new_sha[:7])
        self.pr["body"] = self.grouped_body.replace(EARLIER_GROUP, new_group)
        with self.assertRaisesRegex(ReviewRejected, "Чек-лист изменился"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_checklist_must_be_complete_before_the_comment(self):
        self.event["issue"]["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
        with self.assertRaisesRegex(ReviewRejected, "Пунктов без отметки"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_checklist_for_previous_head(self):
        self.pr["body"] = self.checked_body.replace(SHA, "b" * 40, 1)
        with self.assertRaisesRegex(ReviewRejected, "owner-review-head"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_extra_lines_in_the_command_comment(self):
        self.event["comment"]["body"] += "\n- [x] All items reviewed."
        with self.assertRaisesRegex(ReviewRejected, "одну строку"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_rejects_edit(self):
        self.comment["updated_at"] = "2026-11-04T10:01:00Z"
        with self.assertRaisesRegex(ReviewRejected, "Комментарий изменился"):
            validate(self.event, self.api)

    def test_rejects_draft_or_closed_pr(self):
        for state, draft in (("open", True), ("closed", False)):
            with self.subTest(state=state, draft=draft):
                self.pr.update(state=state, draft=draft)
                with self.assertRaisesRegex(ReviewRejected, "Ready for review"):
                    validate(self.event, self.api)

    def test_rejects_wrong_target(self):
        self.pr["base"]["ref"] = "other"
        with self.assertRaisesRegex(ReviewRejected, "PR в master"):
            validate(self.event, self.api)

    def test_rejects_stale_sha(self):
        self.pr["head"]["sha"] = "b" * 40
        with self.assertRaisesRegex(ReviewRejected, "SHA устарел"):
            validate(self.event, self.api)

    def test_push_during_approval_cannot_publish_status(self):
        response = self.api.side_effect
        reads = []

        def response_with_push(path, payload=None):
            if path == "/pulls/2":
                reads.append(path)
                if len(reads) == 2:
                    self.pr["head"]["sha"] = "b" * 40
            return response(path, payload)

        self.api.side_effect = response_with_push
        with self.assertRaisesRegex(ReviewRejected, "SHA устарел"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_unchecking_during_approval_cannot_publish_status(self):
        response = self.api.side_effect
        reads = []

        def response_with_unchecked_item(path, payload=None):
            if path == "/pulls/2":
                reads.append(path)
                if len(reads) == 2:
                    self.pr["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
            return response(path, payload)

        self.api.side_effect = response_with_unchecked_item
        with self.assertRaisesRegex(ReviewRejected, "Пунктов без отметки"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_feedback_reports_result_and_links_to_the_command_and_run(self):
        run_url = f"{REPOSITORY_URL}/actions/runs/42"
        cases = (
            ("success", "success", "", False, "GitHub выполнит squash", "+1"),
            ("success", "failure", "", False, "Запрос автослияния завершился ошибкой", "+1"),
            ("skipped", "skipped", "SHA устарел.", False, "SHA устарел.", "-1"),
            ("skipped", "skipped", "", True, "токен приложения", "-1"),
            ("failure", "skipped", "", False, "Подробности есть в журнале запуска", "-1"),
        )
        for outcome, merge_outcome, reason, app_failed, expected, reaction in cases:
            with self.subTest(outcome=outcome, reason=reason):
                api = Mock()
                post_feedback(
                    self.event,
                    api,
                    outcome=outcome,
                    merge_outcome=merge_outcome,
                    reason=reason,
                    app_failed=app_failed,
                    run_url=run_url,
                )
                self.assertEqual(len(api.call_args_list), 2)
                path, payload = api.call_args_list[0].args
                self.assertEqual(path, "/issues/2/comments")
                self.assertIn(expected, payload["body"])
                self.assertIn(self.comment["html_url"], payload["body"])
                self.assertIn(run_url, payload["body"])
                reaction_path, reaction_payload = api.call_args_list[1].args
                self.assertEqual(reaction_path, "/issues/comments/10/reactions")
                self.assertEqual(reaction_payload, {"content": reaction})

    def test_acknowledge_reacts_with_eyes(self):
        acknowledge(self.event, self.api)
        self.api.assert_called_once_with("/issues/comments/10/reactions", {"content": "eyes"})

    def test_failed_reaction_does_not_block_feedback(self):
        def fail_reaction(path, payload=None):
            if path.endswith("/reactions"):
                raise ReviewRejected("Запрос к GitHub API завершился ошибкой HTTP 403.")
            return {}

        api = Mock(side_effect=fail_reaction)
        post_feedback(self.event, api, outcome="success", merge_outcome="success", reason="", app_failed=False, run_url="u")
        self.assertEqual(len(api.call_args_list), 2)


if __name__ == "__main__":
    unittest.main()
