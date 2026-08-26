from __future__ import annotations

import unittest
from unittest.mock import patch

from clients import rabbit


class _FakeChannel:
    def __init__(self, fail_publish: bool = False) -> None:
        self.is_closed = False
        self.fail_publish = fail_publish
        self.published = 0

    def confirm_delivery(self) -> None:
        return None

    def basic_publish(self, **_kwargs: object) -> None:
        self.published += 1
        if self.fail_publish:
            self.is_closed = True
            raise EOFError("broker closed connection")


class _FakeConnection:
    def __init__(self, channel: _FakeChannel) -> None:
        self.is_closed = False
        self._channel = channel

    def channel(self) -> _FakeChannel:
        return self._channel

    def close(self) -> None:
        self.is_closed = True


class RabbitPublisherTest(unittest.TestCase):
    def tearDown(self) -> None:
        rabbit._reset_publisher()

    def test_publish_rebuilds_connection_after_eof(self) -> None:
        first_channel = _FakeChannel(fail_publish=True)
        second_channel = _FakeChannel()
        connections = [
            _FakeConnection(first_channel),
            _FakeConnection(second_channel),
        ]

        with patch("clients.rabbit.BlockingConnection", side_effect=connections), \
                patch("clients.rabbit.time.sleep"):
            published = rabbit.publish_json("forum.ai.im.result", {"taskId": "1"})

        self.assertTrue(published)
        self.assertEqual(1, first_channel.published)
        self.assertEqual(1, second_channel.published)
        self.assertTrue(connections[0].is_closed)


if __name__ == "__main__":
    unittest.main()
