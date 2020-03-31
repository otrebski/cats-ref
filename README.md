# cats-ref
Demo project for using [Cats Effects Ref](https://typelevel.org/cats-effect/concurrency/ref.html)


## Running
Start false gov site:
```bash
docker run --rm -dit --name my-apache-app -p 8080:80 -v "$PWD/src/main/resources/":/usr/local/apache2/htdocs/ httpd:2.4
```

Run application:
```bash
sbt "runMain server.ServerApp" 
```

Watch web output
```bash
watch -d curl  -s  http://localhost:8090                       
```

Modify content of `src/main/resources/web/koronawirus/wykaz-zarazen-koronawirusem-sars-cov-2` and watch changes in `curl` command.
