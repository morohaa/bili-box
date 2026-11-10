#!/usr/bin/env python3
"""Create one persistent Android release key and verify a private local backup."""

import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile


def private_write(path: Path, data: bytes) -> None:
    with path.open("xb") as stream:
        os.chmod(path, 0o600)
        stream.write(data)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-dir", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    key_dir = root / ".release-signing"
    key_file = key_dir / "bilibox-release.p12"
    properties_file = root / "signing.properties"
    backup_dir = args.backup_dir.expanduser().resolve()
    if backup_dir == root or root in backup_dir.parents:
        parser.error("The backup must be outside the repository.")
    if key_file.exists() or properties_file.exists() or backup_dir.exists():
        parser.error("Signing material or backup already exists; refusing to replace a release key.")
    java_home = os.environ.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else shutil.which("keytool")
    if not keytool:
        parser.error("Set JAVA_HOME to a JDK containing keytool.")

    # Never put the password in command arguments, terminal output, or Git.
    password = secrets.token_urlsafe(48)
    key_env = dict(os.environ, BILIBOX_KEYTOOL_PASSWORD=password)

    def run_keytool(*arguments: str) -> bytes:
        result = subprocess.run(
            [keytool, *arguments, "-storepass:env", "BILIBOX_KEYTOOL_PASSWORD"],
            env=key_env, capture_output=True, check=True,
        )
        return result.stdout

    key_dir.mkdir(mode=0o700, exist_ok=True)
    os.chmod(key_dir, 0o700)
    backup_dir.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="keygen-", dir=key_dir) as temporary:
        generated_key = Path(temporary) / key_file.name
        run_keytool(
            "-genkeypair", "-keystore", str(generated_key), "-storetype", "PKCS12",
            "-alias", "bilibox-release", "-keyalg", "RSA", "-keysize", "4096",
            "-sigalg", "SHA256withRSA", "-validity", "10000",
            "-dname", "CN=BiliBox, OU=Android, O=BiliBox", "-noprompt",
        )
        certificate = run_keytool(
            "-exportcert", "-keystore", str(generated_key), "-alias", "bilibox-release",
        )
        fingerprint = hashlib.sha256(certificate).hexdigest()

        def properties(store_path: str) -> bytes:
            return (
                f"storeFile={store_path}\nstorePassword={password}\n"
                f"keyAlias=bilibox-release\nkeyPassword={password}\n"
            ).encode("utf-8")

        # Finish and validate the backup before installing the working copy.
        backup_dir.mkdir(mode=0o700)
        try:
            backup_key = backup_dir / key_file.name
            private_write(backup_key, generated_key.read_bytes())
            private_write(backup_dir / "signing.properties", properties(key_file.name))
            private_write(backup_dir / "certificate.der", certificate)
            private_write(backup_dir / "certificate.sha256", (fingerprint + "\n").encode())
            restored_certificate = run_keytool(
                "-exportcert", "-keystore", str(backup_key), "-alias", "bilibox-release",
            )
            if restored_certificate != certificate or backup_key.read_bytes() != generated_key.read_bytes():
                raise RuntimeError("Backup verification failed.")
        except Exception:
            shutil.rmtree(backup_dir)
            raise

        private_write(key_file, generated_key.read_bytes())
        private_write(properties_file, properties(".release-signing/" + key_file.name))
        private_write(key_dir / "certificate.der", certificate)
        private_write(key_dir / "certificate.sha256", (fingerprint + "\n").encode())

    print(f"Release key: {key_file}")
    print(f"Verified local backup: {backup_dir}")
    print(f"Certificate SHA-256: {fingerprint}")
    print("Private files use owner-only permissions. Store an additional copy off this computer.")


if __name__ == "__main__":
    main()
