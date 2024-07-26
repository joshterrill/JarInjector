# JarInjector

Inject java code into existing jar files

## Proof of concept

### Build test jar:

```bash
cd hello-world/
mvn clean package
java -jar target/hello-world.jar
# Hello, World!
```

### Inject test jar:

```bash
# from project root dir
mvn clean package
java -jar target/jar-injector.jar
# Usage: java -jar jar-injector.jar <input-jar> <output-jar> [class-to-modify]
# Or: java -jar jar-injector.jar <input-jar> -dShowClasses

java -jar target/jar-injector.jar ./hello-world/target/hello-world.jar -dShowClasses
# Classes and methods defined in the JAR:
# Class: com.hello.HelloWorld
#   Method: main
#   Method: PublicTestMethod
#   Method: PrivateTestMethod

java -jar target/jar-injector.jar ./hello-world/target/hello-world.jar ./output.jar
java -jar ./output.jar
# Injected code before main
# Hello, World!
```

## License

MIT