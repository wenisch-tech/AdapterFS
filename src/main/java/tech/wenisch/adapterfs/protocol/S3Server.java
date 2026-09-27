package tech.wenisch.adapterfs.protocol;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tech.wenisch.adapterfs.auth.CredentialsService;
import tech.wenisch.adapterfs.config.AdapterFsProperties;
import tech.wenisch.adapterfs.fs.FilesystemService;

@Component
public class S3Server {
    private static final DateTimeFormatter S3_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final AdapterFsProperties properties; private final CredentialsService credentials; private final FilesystemService files;
    private HttpServer server; private Path multipart;
    public S3Server(AdapterFsProperties properties, CredentialsService credentials, FilesystemService files) {
        this.properties = properties; this.credentials = credentials; this.files = files;
    }
    @PostConstruct void start() throws IOException {
        if (!properties.getS3().isEnabled()) return;
        multipart = properties.getStateDirectory().resolve("multipart"); Files.createDirectories(multipart);
        server = HttpServer.create(new InetSocketAddress(properties.getS3().getPort()), 0);
        server.createContext("/", this::handle); server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
    }
    @PreDestroy void stop() { if (server != null) server.stop(1); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authenticate(exchange)) { error(exchange, 403, "SignatureDoesNotMatch", "The request signature is invalid"); return; }
            String[] location = exchange.getRequestURI().getPath().substring(1).split("/", 2);
            if (location.length == 0 || location[0].isBlank()) { listBuckets(exchange); return; }
            String bucket = decode(location[0]); String key = location.length == 1 ? "" : decode(location[1]);
            Map<String,String> query = query(exchange.getRequestURI().getRawQuery());
            if (key.isBlank() && exchange.getRequestMethod().equals("GET")) listObjects(exchange, bucket, query);
            else if (query.containsKey("uploads") && exchange.getRequestMethod().equals("POST")) beginMultipart(exchange, bucket, key);
            else if (query.containsKey("uploadId") && query.containsKey("partNumber") && exchange.getRequestMethod().equals("PUT")) uploadPart(exchange, query);
            else if (query.containsKey("uploadId") && exchange.getRequestMethod().equals("POST")) completeMultipart(exchange, bucket, key, query.get("uploadId"));
            else if (query.containsKey("uploadId") && exchange.getRequestMethod().equals("DELETE")) abortMultipart(exchange, query.get("uploadId"));
            else switch (exchange.getRequestMethod()) {
                case "GET", "HEAD" -> getObject(exchange, bucket, key);
                case "PUT" -> putObject(exchange, bucket, key);
                case "DELETE" -> { files.delete(bucket, key); send(exchange, 204, null, new byte[0]); }
                default -> error(exchange, 405, "MethodNotAllowed", "Unsupported S3 operation");
            }
        } catch (java.nio.file.NoSuchFileException e) { error(exchange, 404, "NoSuchKey", e.getMessage()); }
        catch (IllegalArgumentException e) { error(exchange, 400, "InvalidRequest", e.getMessage()); }
        catch (FilesystemService.ReadOnlyException e) { error(exchange, 403, "AccessDenied", e.getMessage()); }
        catch (Exception e) { error(exchange, 500, "InternalError", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
        finally { exchange.close(); }
    }
    private void listBuckets(HttpExchange exchange) throws IOException {
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Owner><ID>adapterfs</ID><DisplayName>AdapterFS</DisplayName></Owner><Buckets>");
        for (var item : files.exports()) body.append("<Bucket><Name>").append(xml(item.name())).append("</Name><CreationDate>1970-01-01T00:00:00.000Z</CreationDate></Bucket>");
        body.append("</Buckets></ListAllMyBucketsResult>"); xml(exchange, 200, body.toString());
    }
    private void listObjects(HttpExchange exchange, String bucket, Map<String,String> query) throws IOException {
        String prefix = query.getOrDefault("prefix", ""); String delimiter = query.get("delimiter"); int max = parseInt(query.getOrDefault("max-keys", "1000"), 1000);
        Path root = files.rootPath(bucket); List<Path> objects;
        try (var walk = Files.walk(root)) { objects = walk.filter(Files::isRegularFile).filter(p -> !Files.isSymbolicLink(p)).sorted().toList(); }
        StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>").append(xml(bucket)).append("</Name><Prefix>").append(xml(prefix)).append("</Prefix><MaxKeys>").append(max).append("</MaxKeys><IsTruncated>false</IsTruncated>");
        var common = new java.util.LinkedHashSet<String>(); int count=0;
        for (Path object : objects) {
            String key = root.relativize(object).toString().replace('\\','/'); if (!key.startsWith(prefix)) continue;
            if (delimiter != null) { String rest=key.substring(prefix.length()); int at=rest.indexOf(delimiter); if(at>=0){common.add(prefix+rest.substring(0,at+delimiter.length()));continue;} }
            if (count++ >= max) break;
            body.append("<Contents><Key>").append(xml(key)).append("</Key><LastModified>").append(Files.getLastModifiedTime(object).toInstant()).append("</LastModified><ETag>\"").append(md5(object)).append("\"</ETag><Size>").append(Files.size(object)).append("</Size><StorageClass>STANDARD</StorageClass></Contents>");
        }
        for(String value:common) body.append("<CommonPrefixes><Prefix>").append(xml(value)).append("</Prefix></CommonPrefixes>");
        body.append("</ListBucketResult>"); xml(exchange,200,body.toString());
    }
    private void getObject(HttpExchange exchange, String bucket, String key) throws IOException {
        Path source=files.readable(bucket,key); long size=Files.size(source), start=0,end=size-1; int status=200;
        String range=exchange.getRequestHeaders().getFirst("Range"); if(range!=null&&range.startsWith("bytes=")){String[] p=range.substring(6).split("-",2);start=Long.parseLong(p[0]);if(p.length>1&&!p[1].isBlank())end=Math.min(end,Long.parseLong(p[1]));if(start>end||start>=size){error(exchange,416,"InvalidRange","Requested range is invalid");return;}status=206;exchange.getResponseHeaders().set("Content-Range","bytes "+start+"-"+end+"/"+size);}
        exchange.getResponseHeaders().set("ETag","\""+md5(source)+"\""); exchange.getResponseHeaders().set("Accept-Ranges","bytes"); exchange.getResponseHeaders().set("Last-Modified",DateTimeFormatter.RFC_1123_DATE_TIME.format(Files.getLastModifiedTime(source).toInstant().atZone(ZoneOffset.UTC)));
        long length=end-start+1; exchange.sendResponseHeaders(status,exchange.getRequestMethod().equals("HEAD")?-1:length);
        if(!exchange.getRequestMethod().equals("HEAD"))try(var input=Files.newInputStream(source);var output=exchange.getResponseBody()){input.skipNBytes(start);input.transferTo(new LimitedOutputStream(output,length));}
    }
    private void putObject(HttpExchange exchange,String bucket,String key)throws IOException{
        String copy=exchange.getRequestHeaders().getFirst("x-amz-copy-source");
        if(copy!=null){String[] source=decode(copy.startsWith("/")?copy.substring(1):copy).split("/",2);try(var input=Files.newInputStream(files.readable(source[0],source[1]))){files.upload(bucket,key,input,true);}xml(exchange,200,"<CopyObjectResult><LastModified>"+Instant.now()+"</LastModified><ETag>\""+md5(files.readable(bucket,key))+"\"</ETag></CopyObjectResult>");return;}
        files.upload(bucket,key,exchange.getRequestBody(),true);String etag=md5(files.readable(bucket,key));exchange.getResponseHeaders().set("ETag","\""+etag+"\"");send(exchange,200,null,new byte[0]);
    }
    private void beginMultipart(HttpExchange exchange,String bucket,String key)throws IOException{
        String id=UUID.randomUUID().toString();Path dir=multipart.resolve(id);Files.createDirectory(dir);Files.writeString(dir.resolve("target"),bucket+"\n"+key,StandardOpenOption.CREATE_NEW);xml(exchange,200,"<InitiateMultipartUploadResult><Bucket>"+xml(bucket)+"</Bucket><Key>"+xml(key)+"</Key><UploadId>"+id+"</UploadId></InitiateMultipartUploadResult>");
    }
    private void uploadPart(HttpExchange exchange,Map<String,String> query)throws Exception{
        Path dir=uploadDirectory(query.get("uploadId"));int part=parseInt(query.get("partNumber"),-1);if(part<1||part>10000)throw new IllegalArgumentException("Invalid part number");Path target=dir.resolve(String.format("%05d.part",part));MessageDigest digest=MessageDigest.getInstance("MD5");try(var output=Files.newOutputStream(target)){exchange.getRequestBody().transferTo(new java.security.DigestOutputStream(output,digest));}String etag=HexFormat.of().formatHex(digest.digest());exchange.getResponseHeaders().set("ETag","\""+etag+"\"");send(exchange,200,null,new byte[0]);
    }
    private void completeMultipart(HttpExchange exchange,String bucket,String key,String id)throws IOException{
        Path dir=uploadDirectory(id);List<Path> parts;try(var stream=Files.list(dir)){parts=stream.filter(p->p.getFileName().toString().endsWith(".part")).sorted().toList();}if(parts.isEmpty())throw new IllegalArgumentException("No uploaded parts");List<InputStream> streams=new ArrayList<>();for(Path part:parts)streams.add(Files.newInputStream(part));try(InputStream joined=new java.io.SequenceInputStream(java.util.Collections.enumeration(streams))){files.upload(bucket,key,joined,true);}finally{for(InputStream in:streams)in.close();deleteTree(dir);}String etag=md5(files.readable(bucket,key));xml(exchange,200,"<CompleteMultipartUploadResult><Bucket>"+xml(bucket)+"</Bucket><Key>"+xml(key)+"</Key><ETag>\""+etag+"\"</ETag></CompleteMultipartUploadResult>");
    }
    private void abortMultipart(HttpExchange exchange,String id)throws IOException{deleteTree(uploadDirectory(id));send(exchange,204,null,new byte[0]);}
    private Path uploadDirectory(String id)throws IOException{if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid upload id");Path dir=multipart.resolve(id);if(!Files.isDirectory(dir))throw new java.nio.file.NoSuchFileException(id);return dir;}

    private boolean authenticate(HttpExchange exchange) {
        try {
            Map<String,String> query=query(exchange.getRequestURI().getRawQuery());String authorization=exchange.getRequestHeaders().getFirst("Authorization");boolean presigned=query.containsKey("X-Amz-Signature");
            String credential,signedHeaders,signature,date,payloadHash;
            if(presigned){credential=query.get("X-Amz-Credential");signedHeaders=query.get("X-Amz-SignedHeaders");signature=query.get("X-Amz-Signature");date=query.get("X-Amz-Date");payloadHash="UNSIGNED-PAYLOAD";long expires=Long.parseLong(query.getOrDefault("X-Amz-Expires","0"));Instant request=Instant.from(S3_DATE.parse(date));if(expires<1||expires>604800||Instant.now().isAfter(request.plusSeconds(expires)))return false;}
            else {if(authorization==null||!authorization.startsWith("AWS4-HMAC-SHA256 "))return false;Map<String,String> fields=new LinkedHashMap<>();for(String part:authorization.substring(17).split(", *")){String[] pair=part.split("=",2);fields.put(pair[0],pair[1]);}credential=fields.get("Credential");signedHeaders=fields.get("SignedHeaders");signature=fields.get("Signature");date=exchange.getRequestHeaders().getFirst("x-amz-date");payloadHash=exchange.getRequestHeaders().getFirst("x-amz-content-sha256");if(payloadHash==null)payloadHash=sha256(new byte[0]);}
            String[] scope=credential.split("/");if(scope.length!=5||!scope[0].equals(credentials.get().s3AccessKey())||!scope[4].equals("aws4_request"))return false;
            String canonicalQuery=canonicalQuery(exchange.getRequestURI().getRawQuery(),presigned);StringBuilder canonicalHeaders=new StringBuilder();
            for(String header:signedHeaders.split(";")){String value=exchange.getRequestHeaders().getFirst(header);if(value==null&&header.equals("host"))value=exchange.getRequestHeaders().getFirst("Host");if(value==null)return false;canonicalHeaders.append(header.toLowerCase()).append(':').append(value.trim().replaceAll("\\s+"," ")).append('\n');}
            String canonical=exchange.getRequestMethod()+"\n"+canonicalUri(exchange.getRequestURI().getRawPath())+"\n"+canonicalQuery+"\n"+canonicalHeaders+"\n"+signedHeaders+"\n"+payloadHash;
            String scopeValue=String.join("/",scope[1],scope[2],scope[3],scope[4]);String stringToSign="AWS4-HMAC-SHA256\n"+date+"\n"+scopeValue+"\n"+sha256(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] key=hmac(("AWS4"+credentials.get().s3SecretKey()).getBytes(StandardCharsets.UTF_8),scope[1]);key=hmac(key,scope[2]);key=hmac(key,scope[3]);key=hmac(key,"aws4_request");String expected=HexFormat.of().formatHex(hmac(key,stringToSign));return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII));
        } catch(Exception e){return false;}
    }
    private static String canonicalQuery(String raw,boolean removeSignature){if(raw==null)return "";return java.util.Arrays.stream(raw.split("&")).filter(v->!removeSignature||!v.startsWith("X-Amz-Signature=")).sorted().reduce((a,b)->a+"&"+b).orElse("");}
    private static String canonicalUri(String raw){return raw==null||raw.isBlank()?"/":raw;}
    private static byte[] hmac(byte[] key,String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}
    private static String sha256(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static String md5(Path path)throws IOException{try{MessageDigest d=MessageDigest.getInstance("MD5");try(var input=Files.newInputStream(path)){input.transferTo(new java.security.DigestOutputStream(OutputStreamNull.INSTANCE.stream,d));}return HexFormat.of().formatHex(d.digest());}catch(Exception e){throw new IOException(e);}}
    private static Map<String,String> query(String raw){Map<String,String> result=new LinkedHashMap<>();if(raw!=null)for(String item:raw.split("&",-1)){String[] pair=item.split("=",2);result.put(decode(pair[0]),pair.length==1?"":decode(pair[1]));}return result;}
    private static String decode(String value){return URLDecoder.decode(value,StandardCharsets.UTF_8);}
    private static int parseInt(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}
    private static void xml(HttpExchange exchange,int status,String value)throws IOException{send(exchange,status,"application/xml",value.getBytes(StandardCharsets.UTF_8));}
    private static void error(HttpExchange exchange,int status,String code,String message)throws IOException{xml(exchange,status,"<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>"+xml(code)+"</Code><Message>"+xml(message)+"</Message><RequestId>"+UUID.randomUUID()+"</RequestId></Error>");}
    private static void send(HttpExchange exchange,int status,String type,byte[] body)throws IOException{if(type!=null)exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(status,body.length);try(var output=exchange.getResponseBody()){output.write(body);}}
    private static String xml(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void deleteTree(Path root)throws IOException{try(var walk=Files.walk(root)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    private enum OutputStreamNull { INSTANCE; final java.io.OutputStream stream=new java.io.OutputStream(){public void write(int b){}public void write(byte[] b,int o,int l){}}; }
    private static class LimitedOutputStream extends java.io.FilterOutputStream {long remaining;LimitedOutputStream(java.io.OutputStream out,long remaining){super(out);this.remaining=remaining;}public void write(int b)throws IOException{if(remaining-->0)out.write(b);}public void write(byte[] b,int o,int l)throws IOException{int n=(int)Math.min(l,remaining);if(n>0){out.write(b,o,n);remaining-=n;}}}
}
