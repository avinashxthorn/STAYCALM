#!/usr/bin/env python3
"""Decode the permanent key without logging it; pin its public certificate."""
import base64
import hashlib
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
encoded = os.environ.get('STAYCALM_KEYSTORE_BASE64', '')
password = os.environ.get('STAYCALM_KEYSTORE_PASSWORD', '')
env_file = Path(os.environ['GITHUB_ENV'])
version = 100000 + int(os.environ.get('GITHUB_RUN_NUMBER', '1'))
with env_file.open('a') as out:
    out.write(f'STAYCALM_VERSION_CODE={version}\n')
if not encoded or not password:
    with env_file.open('a') as out:
        out.write('STAYCALM_SIGNING_READY=false\n')
    print('Permanent signing is not configured. Tests can run; release publishing is blocked.')
    raise SystemExit(0)
key = Path(os.environ['RUNNER_TEMP']) / 'staycalm-release.p12'
key.write_bytes(base64.b64decode(encoded, validate=True))
key.chmod(0o600)
try:
    cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(key), '-alias', 'staycalm',
        '-storepass:env', 'STAYCALM_KEYSTORE_PASSWORD'], check=True, capture_output=True).stdout
    expected = (root / 'signing/certificate-sha256.txt').read_text().strip().lower()
    if hashlib.sha256(cert).hexdigest() != expected:
        raise ValueError('Certificate mismatch')
except Exception:
    key.unlink(missing_ok=True)
    raise SystemExit('Signing key validation failed. Use the original permanent keystore and password; do not replace the signing identity.')
with env_file.open('a') as out:
    out.write(f'STAYCALM_KEYSTORE_PATH={key}\nSTAYCALM_SIGNING_READY=true\n')
print('Permanent signing certificate validated.')
