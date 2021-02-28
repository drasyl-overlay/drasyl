FROM kubeless/unzip AS build

ADD ./drasyl-*.zip ./

RUN unzip -qq ./drasyl-*.zip && \
    rm ./drasyl-*.zip

FROM openjdk:11-jre-slim

RUN mkdir /usr/local/share/drasyl && \
    ln -s ../share/drasyl/bin/drasyl /usr/local/bin/drasyl

COPY --from=build ./drasyl-* /usr/local/share/drasyl/

# use logback.xml without timestamps
RUN echo '<configuration>\n\
    <statusListener class="ch.qos.logback.core.status.NopStatusListener"/>\n\
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">\n\
        <encoder>\n\
            <pattern>%-5level --- [%12.12thread] %-40.40logger{40} : %msg%n</pattern>\n\
        </encoder>\n\
    </appender>\n\
\n\
    <logger name="io.netty" level="WARN">\n\
    </logger>\n\
\n\
    <root level="WARN">\n\
        <appender-ref ref="Console"/>\n\
    </root>\n\
</configuration>' >> /usr/local/share/drasyl/logback.xml

EXPOSE 22527

ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/drasyl/logback.xml"

ENTRYPOINT ["drasyl"]
