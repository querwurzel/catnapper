Catnapper
=========

RSS combinator for nyaa.se animes (torrents).
Fetches your favorite nyaa anime series and creates a single RSS feed to be subscribed by your RSS reader of choice.
This feed is slightly rearranged so that each feed item will contain the link to its __torrent file for quicker access__.

The URL for each anime must be entered once by you like:<br>
`http://www.nyaa.se/?page=rss&term=HorribleSubs+sword+art+online+ii+720p+mkv`

__Requirements__:
- Java 8+ (JRE or JDK)
- each JEE 6+ Web Profile compliant Servlet Container / application server (e.g. Tomcat, Jetty)

Have a look at the deployment descriptor for some simple configuration (`/WebContent/WEB-INF/web.xml`).
Also take a look at the _example.json_ to get the idea how user feeds are created and configured (`/WebContent/WEB-INF/conf/example.json`).
The path where those files need to be located is `/WebContent/WEB-INF/conf/` unless specified different in the deployment descriptor!

As per deployment two URLs are available for usage:
- `/feed/{Your Feed Identifier}` to request your aggregated RSS feed
- `/settings/{Your Feed Identifier}` to configure your subscribed animes on nyaa.se

Questions? Ask away!<br>
Cheers.
