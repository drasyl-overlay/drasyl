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
    <appender name="Sentry" class="io.sentry.logback.SentryAppender">\n\
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">\n\
            <level>WARN</level>\n\
        </filter>\n\
        <encoder>\n\
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>\n\
        </encoder>\n\
    </appender>\n\
\n\
    <logger name="io.sentry" level="ERROR">\n\
    </logger>\n\
\n\
    <logger name="io.netty" level="WARN">\n\
    </logger>\n\
\n\
    <logger name="com.offbynull.portmapper" level="OFF">\n\
    </logger>\n\
\n\
    <root level="WARN">\n\
        <appender-ref ref="Console"/>\n\
        <appender-ref ref="Sentry"/>\n\
    </root>\n\
</configuration>' >> /usr/local/share/drasyl/logback.xml

EXPOSE 22527

ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/drasyl/logback.xml"

ENTRYPOINT ["drasyl"]