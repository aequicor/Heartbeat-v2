# Документация Heartbeat

Heartbeat — KMP AI Studio для кодинга и исследований в проектах. Приложение рассчитано на Android, iOS, Linux, Windows и macOS. Расширение Chromium использует Kotlin/Wasm.

- [Окружение и первый запуск](setup.md) описывает требования, подготовку окружения и запуск dev-версии.
- [Архитектура](specs/architecture.md) описывает платформы и запуск приложения.
- [Выпуск](specs/releases.md) описывает каналы `alpha`, `beta` и `release`.
- [Работа агента](specs/agent-workflow.md) описывает путь от задачи до ручного ревью.

Роль агента и порядок чтения профильных инструкций описаны в репозитории. [Корневой AGENTS.md](https://github.com/aequicor/Heartbeat-v2/blob/master/AGENTS.md)

## Локальный предпросмотр

Из корня репозитория запусти команды в окружении Python 3.12.

```shell
python -m pip install -r requirements-docs.txt
python -m mkdocs build --strict
python -m mkdocs serve
```

Открой адрес из вывода `mkdocs serve`. GitHub запускает сборку для PR, готовых к ревью. Новый коммит отменяет предыдущую сборку того же PR. GitHub публикует сайт после обновления `master`.
