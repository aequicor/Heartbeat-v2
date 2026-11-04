import copy
import unittest
from pathlib import Path
from unittest.mock import Mock

from owner_review import ReviewRejected, approve, validate


SHA = "a" * 40
TEMPLATE = Path(__file__).resolve().parents[1] / "pull_request_template.md"


class OwnerReviewTest(unittest.TestCase):
    def setUp(self):
        self.checked_body = (
            TEMPLATE.read_text(encoding="utf-8")
            .replace("<полный HEAD SHA>", SHA)
            .replace("- [ ] ", "- [x] ")
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
            "repository": {"id": 5, "owner": {"id": 1, "type": "User"}},
            "issue": {
                "number": 2,
                "pull_request": {"url": "https://example.org"},
                "body": self.checked_body,
            },
            "comment": copy.deepcopy(self.comment),
        }
        self.pr = {
            "state": "open",
            "draft": False,
            "base": {"repo": {"id": 5}, "ref": "master"},
            "head": {"sha": SHA},
            "body": self.checked_body,
        }
        self.api = Mock(side_effect=self.response)
        self.app_api = Mock(side_effect=self.response)

    def response(self, path, payload=None):
        return {
            "/issues/comments/10": self.comment,
            "/pulls/2": self.pr,
            f"/commits/{SHA[:7]}": {"sha": SHA},
            "/pulls/2/reviews": {"state": "APPROVED", "commit_id": SHA},
            f"/statuses/{SHA}": {},
        }[path]

    def test_approves_and_posts_status_on_reviewed_commit(self):
        approve(self.event, self.api, self.app_api)
        review = self.app_api.call_args_list[0].args
        self.assertEqual(review[0], "/pulls/2/reviews")
        self.assertEqual(review[1]["commit_id"], SHA)
        status = self.app_api.call_args.args
        self.assertEqual(status[0], f"/statuses/{SHA}")
        self.assertEqual(status[1]["context"], "owner-review")

    def test_short_sha_uses_full_head_for_approval_and_status(self):
        self.comment["body"] = self.comment["body"].replace(SHA, SHA[:7], 1)
        self.event["comment"]["body"] = self.comment["body"]
        approve(self.event, self.api, self.app_api)
        self.api.assert_any_call(f"/commits/{SHA[:7]}")
        self.assertEqual(self.app_api.call_args_list[0].args[1]["commit_id"], SHA)
        self.assertEqual(self.app_api.call_args.args[0], f"/statuses/{SHA}")

    def test_rejects_sha_shorter_than_seven_digits(self):
        self.event["comment"]["body"] = self.comment["body"].replace(SHA, SHA[:6], 1)
        with self.assertRaisesRegex(ReviewRejected, "at least 7"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_rejects_short_sha_resolved_to_another_commit(self):
        self.comment["body"] = self.comment["body"].replace(SHA, SHA[:7], 1)
        self.event["comment"]["body"] = self.comment["body"]

        def response_with_other_commit(path):
            if path == f"/commits/{SHA[:7]}":
                return {"sha": SHA[:7] + "b" * 33}
            return self.response(path)

        self.api.side_effect = response_with_other_commit
        with self.assertRaisesRegex(ReviewRejected, "does not resolve"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_non_owner(self):
        self.event["comment"]["user"]["id"] = 7
        with self.assertRaisesRegex(ReviewRejected, "repository owner"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_rejects_incomplete_checklist(self):
        self.pr["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
        with self.assertRaisesRegex(ReviewRejected, "checklist"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_checklist_must_be_complete_before_the_comment(self):
        self.event["issue"]["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
        with self.assertRaisesRegex(ReviewRejected, "checklist"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_checklist_for_previous_head(self):
        self.pr["body"] = self.checked_body.replace(SHA, "b" * 40, 1)
        with self.assertRaisesRegex(ReviewRejected, "current head"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_not_called()

    def test_rejects_extra_lines_in_the_command_comment(self):
        self.event["comment"]["body"] += "\n- [x] All items reviewed."
        with self.assertRaisesRegex(ReviewRejected, "one line"):
            validate(self.event, self.api)
        self.api.assert_not_called()

    def test_rejects_edit(self):
        self.comment["updated_at"] = "2026-11-04T10:01:00Z"
        with self.assertRaisesRegex(ReviewRejected, "comment changed"):
            validate(self.event, self.api)

    def test_rejects_draft_or_closed_pr(self):
        for state, draft in (("open", True), ("closed", False)):
            with self.subTest(state=state, draft=draft):
                self.pr.update(state=state, draft=draft)
                with self.assertRaisesRegex(ReviewRejected, "ready for review"):
                    validate(self.event, self.api)

    def test_rejects_wrong_target(self):
        self.pr["base"]["ref"] = "other"
        with self.assertRaisesRegex(ReviewRejected, "target"):
            validate(self.event, self.api)

    def test_rejects_stale_sha(self):
        self.pr["head"]["sha"] = "b" * 40
        with self.assertRaisesRegex(ReviewRejected, "stale"):
            validate(self.event, self.api)

    def test_push_during_approval_cannot_publish_status(self):
        def response_with_push(path, payload=None):
            if path == "/pulls/2/reviews":
                self.pr["head"]["sha"] = "b" * 40
            return self.response(path, payload)

        self.app_api.side_effect = response_with_push
        with self.assertRaisesRegex(ReviewRejected, "stale"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_called_once()

    def test_unchecking_during_approval_cannot_publish_status(self):
        def response_with_unchecked_item(path, payload=None):
            if path == "/pulls/2/reviews":
                self.pr["body"] = self.checked_body.replace("- [x] ", "- [ ] ", 1)
            return self.response(path, payload)

        self.app_api.side_effect = response_with_unchecked_item
        with self.assertRaisesRegex(ReviewRejected, "checklist"):
            approve(self.event, self.api, self.app_api)
        self.app_api.assert_called_once()


if __name__ == "__main__":
    unittest.main()
