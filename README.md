Catnapper
=========

RSS combinator for nyaa.se animes (torrents).
Fetches your favorite nyaa anime series and creates a single RSS feed to be subscribed by your RSS reader of choice.
This feed is slightly rearranged so that each feed item will contain the link to its __torrent file for quicker access__.

The URL for each anime must be entered once by you like:<br>
`http://www.nyaa.si/?page=rss&term=HorribleSubs+sword+art+online+ii+720p+mkv`

__Requirements__:
- Java 8+ (JRE or JDK)
- each JEE 6+ Web Profile compliant Servlet Container / application server (e.g. Tomcat, Jetty)

__Configuration__:

Have a look at the _example.json_ to get an idea how user feeds are created (`WEB-INF/conf/example.json`).
Also take a look at the deployment descriptor for some simple configuration (`WEB-INF/web.xml`).
The path where those files need to be located is `WEB-INF/conf/` unless specified differently in the deployment descriptor!

If you use any other application server as Tomcat or Jetty you may need to adjust the path for the log files (`logback.xml`).

__Installation__:

Just use Maven to create the .war archive and deploy it on your server of choice.
```
mvn package
```

As per deployment two URLs are available for usage:
- `/feed/{Your Feed Identifier}` to request your aggregated RSS feed
- `/settings/{Your Feed Identifier}` to configure your subscribed animes on nyaa.se

Questions? Ask away!<br>
Cheers.
