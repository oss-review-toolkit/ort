You can build a native-image binary as mentioned in the http4s deployment [section] (https://github.com/drocsid/http4s/blob/docs/deployment/docs/src/main/tut/deployment.md) . You will need to follow the directions there to provide GraalVM / native-image plugin and provide a muslC bundle. Then populate the UseMuslC path with it's location.

```
native-image  -H:+ReportExceptionStackTraces -H:+AddAllCharsets --allow-incomplete-classpath --no-fallback --initialize-at-build-time --enable-http --enable-https --enable-all-security-services --verbose -jar "./target/scala-2.13/http4s-template-assembly-0.0.1-SNAPSHOT.jar" http4s-templateBinaryImage
```

