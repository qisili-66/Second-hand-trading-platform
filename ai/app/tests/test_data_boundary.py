from pathlib import Path

from app.config import Settings


def test_ai_service_has_no_mysql_connection_configuration_or_driver_code():
    settings = Settings()
    source_root = Path(__file__).resolve().parents[1]
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in source_root.rglob("*.py")
        if "tests" not in path.parts
    )

    assert not any(name in vars(settings) for name in ("db_host", "db_port", "db_name", "db_user", "db_password"))
    assert "pymysql" not in source
    assert "mysql.connector" not in source
