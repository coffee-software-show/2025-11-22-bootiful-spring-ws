# README

## the basics

* introducing Spring WS
* what is SOAP? (simple object access protocol? simple? really?)
* it's not REST, that's for dang sure. (see the swamp of POX!)
* that said, tons of folks still use it and maintain systems that use it, and they'll be pleased to know you can still
  eke out some good performance and scalability and security, as we'll show in this video.
* introduce the `service.xsd`
* nb: there's a namespace for the XML document, in this case `http://example.com/ws`. the resulting code will be
  generated into `com.example.ws`.
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

* move `service.xsd` to src/main/resources in the `model` folder.
* install it to maven local m2.
* hit the Spring Initializr: add Java 25, webmvc, security, graalvm, and - most importantly - "Spring WebServices" 
* in `application.properties`, add `spring.webservices.path=/ws`
* build our first service, an `@Endpoint`, called `CountryEndpoint`.
  Spring WS doesn't search particularly well!)

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

* Spring WS is a framework for building SOAP-based services. SOAP assumes XML. Spring has a lot of rich support for XML
  processing, obviously. In Spring Framework itself, there's a very interesting module called Spring OXM that contains
  marshallers and unmarshallers for marshalling objects to and from XML. This package was contributed, not surprisingly,
  by Arjen Poutsma, the creator and first lead of the Spring WS project.

## take a walk on the client-side

* we can try this out a few different ways. from the CLI, using Spring's `RestClient`, and using Spring's
  `WebServicesTemplate`. Let's first try it out from the CLI.
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
* which reminds me. SOAP is _not_ an HTTP technology. it doesn't assume HTTP at all. it's transport agonostic. Even in
  Spring WS, we have support for various other protocols.
* add `ws-support` to see the support for other
  protocols.  [JMS](https://docs.spring.io/spring-ws/docs/current/reference/html/#_jms_transport)
  and [e-mail](https://docs.spring.io/spring-ws/docs/current/reference/html/#_email_transport)
  and [XMPP](https://docs.spring.io/spring-ws/docs/current/reference/html/#_xmpp_transport).
* it works really well if you're using it in a (synchronous) Tomcat-based Servlet context, obviously, and especially so
  in a Spring Boot context. Spring Boot ships with an autoconfiguration that stands up an embedded webserver and
  installs the requisite `MessageDispatcherServlet`.
* funny enough, Spring WS - which is a _very_ old project with code that's almost 20 years old - ships with an embedded
  webserver implementation. Obviously, you won't want to use this since Spring Boot does the job so much better. I just
  think its interesting.
* anyway, since Spring WS assumes a Servlet container and runtime, as opposed to a reactive runtime like Netty, you'll
  want to enable virtual threads: `spring.threads.virtual.enabled=true`, on both the client and the service.

## the `WebServiceTemplate`

* the `RestClient` works well enough for low-level HTTP interactions with SOAP-based services, but again, remember, the
  factg that this SOAP service is using HTTP is almost incidental.
* spring ws ships with out-of-the-box support for client usecases with the `WebServiceTemplate`. Add another endpoint to
  the controller, like this:

````java

@GetMapping("/username")
String usernamePassworedSecuredWebServiceTemplate() throws Exception {
    var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    var getMeElement = doc.createElementNS("http://example.com/ws", "getMeRequest");
    var request = new DOMSource(getMeElement);
    var response = new StringResult();
    this.ws.sendSourceAndReceiveToResult(request, response);
    return response.toString();
}

````

* we'll need to define the `WebServiceTemplate`, too:

```java

@Bean
WebServiceTemplate webServiceTemplate(
        Jaxb2Marshaller jaxb2Marshaller,
        WebServiceTemplateBuilder builder) {
    return builder //
            .setDefaultUri("http://localhost:8080/ws") //
            .setMarshaller(jaxb2Marshaller)//
            .setUnmarshaller(jaxb2Marshaller)//
            .build();
}
```

* hit `localhost:8081/ws` and you should get information about the country. Huzzah.

## ws-security and wss4j

* spring WS is a framework for building SOAP services. it runs on the web, but does not assume the web. so its not
  practical to assume that HTTP layer transports apply to SOAP.
* Instead, the convention is that there be in the XML request payload itself extra elements (unhelpfully called
  _headers_) that stipulate out of band information (like credentials) for a given SOAP envelope.
* what goes in those headers for security is specified by a separate specification called WS-Security.
* it is a message-level security specification.
* WS-security covers a _ton_ of possible scenarios:
    * username / password tokens
    * X.509 certificates
    * SAML tokens
    * Kerberos tickets
    * custom tokens (text and binary)
    * digital signatures (with XML-DSIg), allowing you to sign parts or all of the SOAP envelope
    * encryption (uses XML encryption), allowing you to encrypt parts or all of the SOAP body.
* WS-Security in turn can be used with a _slew_ of other specifications, some of which never went GA, including
  WS-Policy, WS-Trust, WS-Federation, WS-SecureConversation, etc.
* sounds like a confusing rat's nest? it is and we shan't get too much further into it.
* WS-Security started with the best of intentions. before WS-Security, SOAP used transport-level security like HTTPS and
  HTTP Basic.
* but this meant that messages were either entirely signed, or not, entirely enccrypted, or not.
* it also meant that there was no way to ensure multi-hop routing securely.
* in 2002, IBM, Microsoft, and VeriSign submitted a spec to OASIS and that became the foundation of WS-Security, and
  indeed when we first got the notion of WS-* (whose name is now a curse)
* here's where things get tedious.
* remember, this project is _old_. I first started using it around 2007, if memory serves.
* at the time, the world of security was very different.
* there's a project called Apache WSS4J - "web services security 4 java"
* it built on Apache XML and served as a reference implementation of the spec. it supports a ton of features, but they
  were all bespoke and implemented as part of the library itself.
* it does _not_ make integration with backend infrastructure particularly easy, but it is a sort of de-facto standard.
* so, we're going to look at setting up security with Spring WS, keeping in mind that we'll also be integrating witht
  his library. Ive looked at the code and Spring WS, like all Spring projects, has a rich interceptor model.
* there's no reason we couldn't re-implement a lot of this stuff directly on top of Spring WS, sidestepping WSS4J, but
  WSS4J seems to be implied in most set ups, so that's what we'll use.
* WSS4J in turn was a dependency of countless other projects including Apache CXF, Axis2, Mule ESB, and so many other
  enterprise-y SOA things from WSO2, and countless other WS-Security integrations.
* internally WSS4J sets up a pipeline of processors, which inspect a given request and establish its needs, and
  validators.
* there's other stuff, like actions and cryptography, but for our purposes, what we need to know is we'll spend a lot of
  time telling WSS4J what we want and - importantly - what validator we want invoked as a result. it can do almost
  anything, but we want to customize the validation.
* if we used the default integration in Spring WS, we'd get something that does password based authentication with
  Spring Security by comparing the password in our `UserDetailsService` (which, should be one-way hashed!) with the
  plaintext-password that you get from the request. Not good, and we're not gonna tolerate it.
* also, it's good to remember that the SOAP spec does _not_ assume HTTP. so in SOAP everything must be crammed into the
  envelope of the message itself, and not handled as part of HTTP transport layer security protocols. if ur on HTTP,
  then of course you're going to want to use HTTPS, and you could maybe avoid signing messages in the Java layer.
  likewise, you could use OAuth for the network service, but there's no guarantee you'll have HTTP, so you'll need to
  present an OAuth bearer token as part of the envelope of the message itself.

## a typical WS-Security integration

* let's look at a typical integration. we'll need WSS4J. add `org.springframework.boot`:`spring-boot-starter-security`. add `org.springframework.ws`:`spring-ws-security`, etc.
* we'll need some common beans in play for all of our integrations, so i've excised them out to a separate abstract super class.

```java

abstract class AbstractSecurityConfiguration implements WsConfigurer {

	private final ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors;

	AbstractSecurityConfiguration(ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
		this.wss4jSecurityInterceptors = wss4jSecurityInterceptors;
	}

	@Override
	public void addInterceptors(List<EndpointInterceptor> interceptors) {
		interceptors.add(wss4jSecurityInterceptors.getObject());
	}

	@Bean
	Customizer<HttpSecurity> defaultSpringWsHttpSecurityCustomizer(@Value("${spring.webservices.path}") String wsPath) {
		return http -> http
            .csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(a -> a //
				.requestMatchers(wsPath.endsWith("/**") ? wsPath : wsPath + "/**")
				.permitAll());
	}

	abstract WSSConfig wssConfig();

	abstract Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig);

}


```
* you can see we're extending `WsConfigurer`, which in turn lets us plug in some interceptors, including the one provided by Spring WS called `Wss4jSecurityInterceptor`.
* we'll need to customize this interceptor and the `WSSConfig` that it will depend on, so these are left as `abstract` methods.
* we want Spring Security to _not_ lock down requests to the SOAP handler, which is mounted, again, at `/ws`. The reason, again, is because we can't rely on transport-level security like HTTP BASIC, so the request needs to reach the Spring WS `MessageDispatcherServlet` where the message can be read, routed to WSS4J and ultimately passed to our Spring Security integration code to handle the challenge. 
* so we'll disable CSRF, and permit all requests to the `spring.webservices.path`.
* let's now look our first integration, this time using usernames and passwords. 

```java

package com.example.ws;

import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.callback.AbstractWsPasswordCallbackHandler;

import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.util.Set;

@Configuration
class UsernameTokenAuthenticationSecurityConfiguration extends AbstractSecurityConfiguration {

    UsernameTokenAuthenticationSecurityConfiguration(
            ObjectProvider<@NonNull Wss4jSecurityInterceptor> wss4jSecurityInterceptors) {
        super(wss4jSecurityInterceptors);
    }

    @Bean
    @Override
    WSSConfig wssConfig() {
        var wssconfig = WSSConfig.getNewInstance();
        wssconfig.setValidator(WSConstants.USERNAME_TOKEN, this.userDetailsServiceUsernameTokenValidator(null));
        return wssconfig;
    }

    @Bean
    @Override
    Wss4jSecurityInterceptor wss4jSecurityInterceptor(WSSConfig wssConfig) {
        var ws4jsi = new Wss4jSecurityInterceptor();
        ws4jsi.setValidationActions("UsernameToken");
        ws4jsi.setWssConfig(wssConfig);
        ws4jsi.setValidationCallbackHandler(new AbstractWsPasswordCallbackHandler() {
            @Override
            protected void handleUsernameToken(@NonNull WSPasswordCallback callback) {
                // noop. don't care. the validator will do the hardest work.
            }
        });
        return ws4jsi;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder passwordEncoder) {
        var users = Set.of("stephane", "rob", "josh")
                .stream()
                .map(username -> User //
                        .withUsername(username)//
                        .password(passwordEncoder.encode("pw"))
                        .roles("USER") //
                        .build() //
                )
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService) {
        return new DaoAuthenticationProvider(userDetailsService);
    }

    @Bean
    UserDetailsServiceUsernameTokenValidator userDetailsServiceUsernameTokenValidator(
            DaoAuthenticationProvider daoAuthenticationProvider) {
        return new UserDetailsServiceUsernameTokenValidator(daoAuthenticationProvider);
    }

    static class UserDetailsServiceUsernameTokenValidator extends AbstractAuthenticationProviderValidator {

        UserDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider jwtAuthenticationProvider) {
            super(jwtAuthenticationProvider, (credential, _) -> {
                var credentialUsernametoken = credential.getUsernametoken();
                var pw = credentialUsernametoken.getPassword();
                var name = credentialUsernametoken.getName();
                return new UsernamePasswordAuthenticationToken(name, pw);
            });
        }

    }

}


```
* most of this configuration is standard Spring Security username and password handling. 
* this includes the  `DaoAuthenticcationProvider`, which in turn delegates to a `UserDetailsService` integration (the `InMemoryUserDetailsManager`). This in turn delegates to a `PasswordEncoder` for hashing the passwords. Obviously it'd be trivial (nothing would change, conceptually) to instead use a `JdbcUserDetailsManager` and source the usernames and passwords from a SQL database. You'd still wanna use `PasswordEncoder` before writing the password to the SQL database, of course. Or you could implement your own `UserDetailsManager`.
* important bits - the things that are really the crux of our integration - are the `UserDetailsServiceUsernameTokenValidator` (phew!) and the `WSSConfig`.
* behind the scenes, there's a map of actions to validation handlers (basically just classes that get invoked to _handle_ validating a credential). we configure this in the `WSSConfig` class and we point it to an implementation of our `AbstractAuthenticationProviderValidator`, which looks like this:
```java
  static class UserDetailsServiceUsernameTokenValidator extends AbstractAuthenticationProviderValidator {

    UserDetailsServiceUsernameTokenValidator(DaoAuthenticationProvider jwtAuthenticationProvider) {
        super(jwtAuthenticationProvider, (credential, _) -> {
            var credentialUsernametoken = credential.getUsernametoken();
            return new UsernamePasswordAuthenticationToken(
                    credentialUsernametoken.getName(),
                    credentialUsernametoken.getPassword());
        });
    }
}
```
 
## usernames and passwords

* fundamentally youre going to want to build a

## graalvm native images

* so far we've been running the service embedded in the Spring Boot application. obviously, today there are many ways to
  dramatically improve the runtime efficiency of JVM-based code. One of my favorites is to use GraalVM to pre-compile a
  JVm program into native code.
* I wondered how difficult it might be to do that for a Spring WS application. It's not _too_ bad, though I'll tell you
  it wasn't trivial, either. anyway, ive packaged up all the work in my little aggregation
  project, [here](https://github.com/bootiful-spring-graalvm/hints). this project is not an official project and is in
  no way maintained or supported. but, for this application in this moment, it works. it's apache 2 licensed so you can
  always go here and just grab the bits yourself.
* i've added this to the build for both `client` and `ws` : `com.joshlong` : `hints` : `0.0.13`.
* now compile them both thusly: `./mvnw -DskipTests -Pnative native:compile`. run the application, and, huzzah! you've
  got a lightning fast and super lightweight application that starts in no time at all. and just _look_ at that RAM! 

## conclusion 