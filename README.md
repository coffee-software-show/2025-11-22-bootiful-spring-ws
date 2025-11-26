# README

* introducing Spring WS
* introduce the `service.xsd`
* nb: there's a namespace for the XML document, in this case `http://example.com/ws`. the resulting code will be generated into `com.example.ws`. 
* set up the JAXB model in a separate project.
* set up a new spring ws project. remove the spring boot maven plugin. add the following maven plugin.
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>xjc</id>
            <goals>
                <goal>xjc</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <sources>
            <source>${project.basedir}/src/main/resources/service.xsd</source>
        </sources>
    </configuration>
</plugin>
```
* move `service.xsd` to src/main/resources in the `aot` folder.
* install it to maven local m2.
* build our first service, an `@Endpoint`, called `CountryEndpoint`.
* hit the Spring Initializr: add Java 25, webmvc, security, graalvm, and - most importantly  - "Spring WebServices" (NB: Spring WS doesn't search particularly well!)
```java
@Endpoint
class CountryEndpoint {

    private static final String NAMESPACE_URI = "http://example.com/ws";

    private final Map<String, Country> countries = new ConcurrentHashMap<>();

    CountryEndpoint() {
        this.add("Spain", "Madrid", Currency.EUR, 46704314);
        this.add("Poland", "Warsaw", Currency.PLN, 38186860);
        this.add("United Kingdom", "London", Currency.GBP, 63705000);
    }

    @ResponsePayload
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
    GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
        var response = new GetCountryResponse();
        response.setCountry(this.countries.get(request.getName()));
        return response;
    }

    private void add(String name, String capital, Currency currency, int population) {
        this.countries.computeIfAbsent(name, s -> {
            var country = new Country();
            country.setCurrency(currency);
            country.setName(name);
            country.setCapital(capital);
            country.setPopulation(population);
            return country;
        });
    }
}
```
* we can try this out two ways, from the CLI, and using Spring's `WebServicesTemplate`. Let's try it out from the CLI.
* add the following XMl to a document called `request-1.xml`:
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:gs="http://example.com/ws">
    <soapenv:Header/>
    <soapenv:Body>
        <gs:getCountryRequest>
            <gs:name>Spain</gs:name>
        </gs:getCountryRequest>
    </soapenv:Body>
</soapenv:Envelope>
```
* test it out: 
```shell
#!/usr/bin/env bash
curl -v --header "content-type: text/xml" -d @request.xml http://localhost:8080/ws
```
* let's spin up a new module, called `client`. add Spring WS, web.
```java
@Controller
@ResponseBody
class ClientController {

	static final Resource REQUEST_RESOURCE = new ClassPathResource("/request.xml");

	private final RestClient rest;

	private final String xml;

	ClientController(RestClient rest) {
		this.rest = rest;
		try {
			this.xml = REQUEST_RESOURCE.getContentAsString(Charset.defaultCharset());
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@GetMapping("/rest")
	String restClient(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
		var token = client.getAccessToken().getTokenValue();
		return this.rest //
			.post() //
			.uri("http://localhost:8080/ws") //
			.contentType(MediaType.TEXT_XML)//
			.body(this.xml)//
			.retrieve()//
			.body(String.class);
	}
}
```
* copy `request.xml` into `src/main/resources` in the `client`.
* make sure that it spins up on port `8081`. 
* hit `http://localhost:8081/rest` and you should see the results from the downstream service.
* nice, but this is using the `RestClient` which is, well, clearly, not what this is.
* which reminds me. SOAP is _not_ an HTTP technology. it doesn't assume HTTP at all. it's transport agonostic. Even in Spring WS, we have support for various other protocols. 
* add `ws-support` to see the support for other protocols.  [JMS](https://docs.spring.io/spring-ws/docs/current/reference/html/#_jms_transport) and [e-mail](https://docs.spring.io/spring-ws/docs/current/reference/html/#_email_transport) and [XMPP](https://docs.spring.io/spring-ws/docs/current/reference/html/#_xmpp_transport). 
* it works really well if you're using it in a (synchronous) Tomcat-based Servlet context, obviously, and especially so in a Spring Boot context. Spring Boot ships with an autoconfiguration that stands up an embedded webserver and installs the requisite `MessageDispatcherServlet`. 
* funny enough, Spring WS - which is a _very_ old project with code that's almost 20 years old - ships with an embedded webserver implementation. Obviously, you won't want to use this since Spring Boot does the job so much better. I just think its interesting. 
* anyway, since Spring WS assumes a Servlet container, you'll want to enable virtual threads: `spring.threads.virtual.enabled=true`, on both the client and the service.
* 











