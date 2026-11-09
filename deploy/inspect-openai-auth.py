import hashlib
import json
import os
import urllib.error
import urllib.parse
import urllib.request

key = os.environ.get("OPENAI_API_KEY", "")
expected_hash = os.environ.get("EXPECTED_OPENAI_KEY_HASH", "")
actual_hash = hashlib.sha256(key.encode("utf-8")).hexdigest() if key else ""
base_url = os.environ.get("AI_CHAT_BASE_URL", "")
model = os.environ.get("AI_CHAT_MODEL", "")

print(f"actions_secret_matches_container={bool(expected_hash and actual_hash == expected_hash)}")
print(f"container_key_length={len(key)}")
print(f"container_key_starts_with_sk={key.startswith('sk-')}")
print(f"container_key_has_assignment_prefix={key.startswith('OPENAI_API_KEY=')}")
print(f"container_key_has_surrounding_whitespace={key != key.strip()}")
print(f"chat_model={model}")
print(f"chat_base_url={base_url}")

parts = urllib.parse.urlsplit(base_url)
path = parts.path
if path.endswith("/chat/completions"):
    path = path[: -len("/chat/completions")] + "/models"
models_url = urllib.parse.urlunsplit((parts.scheme, parts.netloc, path, "", ""))
request = urllib.request.Request(models_url, headers={"Authorization": f"Bearer {key}"})

try:
    with urllib.request.urlopen(request, timeout=15) as response:
        print(f"openai_auth_status={response.status}")
except urllib.error.HTTPError as error:
    try:
        payload = json.loads(error.read().decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        payload = {}
    details = payload.get("error") if isinstance(payload, dict) else {}
    details = details if isinstance(details, dict) else {}
    print(f"openai_auth_status={error.code}")
    print(f"openai_error_type={details.get('type', '')}")
    print(f"openai_error_code={details.get('code', '')}")
    print(f"openai_error_param={details.get('param', '')}")
except urllib.error.URLError as error:
    print("openai_auth_status=transport_error")
    print(f"openai_transport_error={type(error.reason).__name__}")

model_url = f"{models_url.rstrip('/')}/{urllib.parse.quote(model, safe='')}"
model_request = urllib.request.Request(model_url, headers={"Authorization": f"Bearer {key}"})
try:
    with urllib.request.urlopen(model_request, timeout=15) as response:
        print(f"configured_model_status={response.status}")
except urllib.error.HTTPError as error:
    try:
        payload = json.loads(error.read().decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError):
        payload = {}
    details = payload.get("error") if isinstance(payload, dict) else {}
    details = details if isinstance(details, dict) else {}
    print(f"configured_model_status={error.code}")
    print(f"configured_model_error_type={details.get('type', '')}")
    print(f"configured_model_error_code={details.get('code', '')}")
except urllib.error.URLError as error:
    print("configured_model_status=transport_error")
    print(f"configured_model_transport_error={type(error.reason).__name__}")
