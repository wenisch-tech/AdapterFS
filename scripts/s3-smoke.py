#!/usr/bin/env python3
"""Exercise AdapterFS S3 SigV4, range, presigned, copy and multipart operations."""
import datetime, hashlib, hmac, os, urllib.error, urllib.parse, urllib.request, xml.etree.ElementTree as ET

ENDPOINT=os.getenv("S3_ENDPOINT","http://127.0.0.1:19000").rstrip("/")
ACCESS=os.getenv("AWS_ACCESS_KEY_ID","AFSTEST"); SECRET=os.getenv("AWS_SECRET_ACCESS_KEY","test-secret")
REGION="us-east-1"
def sign(method,path,body=b"",query="",extra=None,presign=False):
    now=datetime.datetime.now(datetime.timezone.utc); stamp=now.strftime("%Y%m%dT%H%M%SZ"); day=stamp[:8]
    host=urllib.parse.urlsplit(ENDPOINT).netloc; headers={"host":host,**(extra or {})}; payload=hashlib.sha256(body).hexdigest()
    scope=f"{day}/{REGION}/s3/aws4_request"
    if presign:
        params={"X-Amz-Algorithm":"AWS4-HMAC-SHA256","X-Amz-Credential":f"{ACCESS}/{scope}","X-Amz-Date":stamp,"X-Amz-Expires":"60","X-Amz-SignedHeaders":"host"}
        query=urllib.parse.urlencode(params,quote_via=urllib.parse.quote)
    else: headers["x-amz-date"]=stamp;headers["x-amz-content-sha256"]=payload
    signed=";".join(sorted(headers));canonical_headers="".join(f"{k}:{headers[k]}\n" for k in sorted(headers))
    canonical=f"{method}\n{path}\n{query}\n{canonical_headers}\n{signed}\n{'UNSIGNED-PAYLOAD' if presign else payload}"
    string=f"AWS4-HMAC-SHA256\n{stamp}\n{scope}\n{hashlib.sha256(canonical.encode()).hexdigest()}"
    key=hmac.new(("AWS4"+SECRET).encode(),day.encode(),hashlib.sha256).digest()
    for value in (REGION,"s3","aws4_request"):key=hmac.new(key,value.encode(),hashlib.sha256).digest()
    signature=hmac.new(key,string.encode(),hashlib.sha256).hexdigest()
    if presign:return f"{ENDPOINT}{path}?{query}&X-Amz-Signature={signature}"
    headers["Authorization"]=f"AWS4-HMAC-SHA256 Credential={ACCESS}/{scope}, SignedHeaders={signed}, Signature={signature}"
    return f"{ENDPOINT}{path}"+("?"+query if query else ""),headers
def request(method,path,body=b"",query="",extra=None):
    url,headers=sign(method,path,body,query,extra);req=urllib.request.Request(url,data=body if method in ("PUT","POST") else None,headers=headers,method=method)
    with urllib.request.urlopen(req) as response:return response.read(),dict(response.headers)
request("GET","/")
request("PUT","/files/s3-smoke.txt",b"AdapterFS S3")
assert request("GET","/files/s3-smoke.txt")[0]==b"AdapterFS S3"
assert request("GET","/files/s3-smoke.txt",extra={"range":"bytes=0-6"})[0]==b"Adapter"
request("PUT","/files/s3-copy.txt",extra={"x-amz-copy-source":"/files/s3-smoke.txt"})
begin,_=request("POST","/files/multipart.txt",query="uploads=")
upload_id=ET.fromstring(begin).findtext("{*}UploadId")
for number,value in ((1,b"multi"),(2,b"part")): request("PUT","/files/multipart.txt",value,query=f"partNumber={number}&uploadId={upload_id}")
complete=b"<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>x</ETag></Part><Part><PartNumber>2</PartNumber><ETag>y</ETag></Part></CompleteMultipartUpload>"
request("POST","/files/multipart.txt",complete,query=f"uploadId={upload_id}")
assert request("GET","/files/multipart.txt")[0]==b"multipart"
with urllib.request.urlopen(sign("GET","/files/s3-smoke.txt",presign=True)) as response: assert response.read()==b"AdapterFS S3"
read_only_bucket=os.getenv("S3_READ_ONLY_BUCKET")
if read_only_bucket:
    try:
        request("PUT", f"/{read_only_bucket}/blocked.txt", b"blocked")
        raise AssertionError("read-only bucket accepted a write")
    except urllib.error.HTTPError as error:
        assert error.code == 403, error.code
print("S3 smoke test passed")
