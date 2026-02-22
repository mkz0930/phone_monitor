"""CLI for the Horse/Claw conversation vector database."""

import json
import shutil
from datetime import datetime
from pathlib import Path

import click

from .db import ConversationDB, DB_DIR


def _json_out(data):
    click.echo(json.dumps(data, indent=2, ensure_ascii=False))


@click.group()
@click.pass_context
def cli(ctx):
    """Horse/Claw conversation vector database."""
    ctx.ensure_object(dict)
    # Only initialize DB if not running a command that doesn't need it (like backup)
    # But backup needs DB_DIR which is imported.
    # We'll initialize lazily inside commands if startup is slow, but for now it's fine.
    ctx.obj["db"] = ConversationDB()


@cli.command()
@click.option("--content", "-c", required=True, help="Message content")
@click.option("--sender", "-s", required=True, type=click.Choice(["Horse", "Claw"]))
@click.option("--session", "-k", required=True, help="Session key")
@click.option("--order", "-o", required=True, type=int, help="Message order in session")
@click.option("--channel", default="manual", help="Channel (default: manual)")
@click.option("--timestamp", "-t", default=None, help="ISO timestamp (auto-generated if omitted)")
@click.option("--id", "message_id", default=None, help="Message ID (auto-generated if omitted)")
@click.pass_context
def insert(ctx, content, sender, session, order, channel, timestamp, message_id):
    """Insert a message into the database."""
    db: ConversationDB = ctx.obj["db"]
    msg_id = db.insert(
        content=content,
        sender=sender,
        session_key=session,
        order=order,
        channel=channel,
        timestamp=timestamp,
        message_id=message_id,
    )
    click.echo(f"Inserted message {msg_id}")


@cli.command()
@click.argument("query")
@click.option("-n", default=5, help="Number of results")
@click.option("--sender", "-s", default=None, type=click.Choice(["Horse", "Claw"]))
@click.pass_context
def search(ctx, query, n, sender):
    """Semantic similarity search."""
    db: ConversationDB = ctx.obj["db"]
    results = db.search(query, n=n, sender=sender)
    _json_out(results)


@cli.command()
@click.option("--start", required=True, help="Start timestamp (ISO)")
@click.option("--end", required=True, help="End timestamp (ISO)")
@click.option("--sender", "-s", default=None, type=click.Choice(["Horse", "Claw"]))
@click.pass_context
def history(ctx, start, end, sender):
    """Query messages by time range."""
    db: ConversationDB = ctx.obj["db"]
    results = db.query_by_time(start, end, sender=sender)
    _json_out(results)


@cli.command("session")
@click.argument("session_key")
@click.pass_context
def get_session(ctx, session_key):
    """Get all messages in a session."""
    db: ConversationDB = ctx.obj["db"]
    results = db.query_by_session(session_key)
    _json_out(results)


@cli.command("sessions")
@click.pass_context
def list_sessions(ctx):
    """List all session keys."""
    db: ConversationDB = ctx.obj["db"]
    for s in db.list_sessions():
        click.echo(s)


@cli.command("import")
@click.option("--source", "-s", default="feishu", type=click.Choice(["feishu", "all"]), help="Source to import from")
@click.pass_context
def import_cmd(ctx, source):
    """Import conversation sessions into the database."""
    click.echo(f"Starting import from {source}...")
    
    # Lazy import to avoid circular dependencies/path issues
    try:
        if source == "feishu":
            from .import_feishu_sessions import import_feishu_sessions
            import_feishu_sessions()
        elif source == "all":
            from .import_all_sessions import import_all_sessions
            import_all_sessions()
    except ImportError as e:
        click.echo(f"Import failed: {e}", err=True)
    except Exception as e:
        click.echo(f"An error occurred: {e}", err=True)


@cli.command()
@click.argument("message_id")
@click.pass_context
def get(ctx, message_id):
    """Get a message by ID."""
    db: ConversationDB = ctx.obj["db"]
    msg = db.get(message_id)
    if msg:
        _json_out(msg)
    else:
        click.echo("Not found", err=True)
        raise SystemExit(1)


@cli.command()
@click.argument("message_id")
@click.pass_context
def delete(ctx, message_id):
    """Delete a message by ID."""
    db: ConversationDB = ctx.obj["db"]
    if db.delete(message_id):
        click.echo(f"Deleted {message_id}")
    else:
        click.echo("Not found", err=True)
        raise SystemExit(1)


@cli.command()
@click.pass_context
def stats(ctx):
    """Show database stats."""
    db: ConversationDB = ctx.obj["db"]
    click.echo(f"Total messages: {db.count()}")
    click.echo(f"Sessions: {len(db.list_sessions())}")


@cli.command()
@click.pass_context
def backup(ctx):
    """Backup the database to a zip file."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_dir = Path(__file__).parent / "backups"
    backup_dir.mkdir(exist_ok=True)
    
    archive_name = backup_dir / f"conversation_db_backup_{timestamp}"
    
    click.echo(f"Backing up {DB_DIR} to {archive_name}.zip...")
    
    if not DB_DIR.exists():
        click.echo("Database directory does not exist.", err=True)
        return

    try:
        shutil.make_archive(str(archive_name), "zip", DB_DIR)
        click.echo(f"Backup created: {archive_name}.zip")
    except Exception as e:
        click.echo(f"Backup failed: {e}", err=True)


if __name__ == "__main__":
    cli()
