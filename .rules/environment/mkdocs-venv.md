# MkDocs в локальном .venv без pip

## Применимость

Сборка документации этого репозитория на машине разработчика (`python -m mkdocs build --strict`
из корня) в окружении `.venv` Python 3.12.

## Условие

`.venv` создан без pip (`No module named pip`), а пакеты установлены в каталог
`.venv\packages`, который не подключён как site-packages. `python -m mkdocs` и консольные
скрипты `.venv\packages\bin\mkdocs.exe` при этом не находят модуль.

## Требуемое поведение

Запускай MkDocs с переменной `PYTHONPATH`, указывающей на каталог пакетов:

```powershell
$env:PYTHONPATH = '<репозиторий>\.venv\packages'
.\.venv\Scripts\python.exe -m mkdocs build --strict
```

Сборка должна завершиться без предупреждений; каталог `site/` генерируется при сборке.

## Исключения

Если после пересоздания окружения в `.venv` появится pip, следуй командам из
`docs/AGENTS.md`: `python -m pip install -r requirements-docs.txt`, затем
`python -m mkdocs build --strict` без `PYTHONPATH`.
