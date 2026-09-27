#!/bin/sh
set -eu
image="${1:?container image required}"
work_dir=$(mktemp -d)
cleanup(){
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "AdapterFS smoke container logs:" >&2
    docker logs adapterfs-smoke >&2 || true
  fi
  docker rm -f adapterfs-smoke >/dev/null 2>&1 || true
  rm -rf "$work_dir"
  exit "$status"
}
trap cleanup EXIT
mkdir -p "$work_dir/files" "$work_dir/state"
# The image runs as UID 65532. mktemp creates a 0700 parent directory, which
# otherwise prevents that non-root user from traversing the mounted paths.
chmod 0777 "$work_dir" "$work_dir/files" "$work_dir/state"
printf 'protocol smoke test\n' > "$work_dir/files/hello.txt"
docker run -d --name adapterfs-smoke -p 18080:8080 -p 19000:9000 -p 12222:2222 -p 12121:2121 -p 30000-30009:30000-30009 -e ADAPTERFS_FTP_ENABLED=true -e ADAPTERFS_AUTH_USERNAME=test -e ADAPTERFS_AUTH_PASSWORD=test-password -e ADAPTERFS_AUTH_S3_ACCESS_KEY=AFSTEST -e ADAPTERFS_AUTH_S3_SECRET_KEY=test-secret -v "$work_dir/files:/data" -v "$work_dir/state:/var/lib/adapterfs" "$image"
ready=false
for attempt in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then echo "AdapterFS did not become ready" >&2; exit 1; fi
curl -fsS -u test:test-password -X PROPFIND -H 'Depth: 1' http://127.0.0.1:18080/dav/files/ | grep -q hello.txt
curl -fsS -u test:test-password http://127.0.0.1:18080/api/v1/exports/files/download?path=hello.txt | grep -q 'protocol smoke test'
ssh-keyscan -p 12222 127.0.0.1 >/dev/null
python3 scripts/s3-smoke.py
curl -fsS --user test:test-password ftp://127.0.0.1:12121/files/ | grep -q hello.txt
curl -fsS --user test:test-password sftp://127.0.0.1:12222/files/hello.txt --insecure | grep -q 'protocol smoke test'
docker logs adapterfs-smoke 2>&1 | grep -q 'Started AdapterFsApplication'
