FROM eclipse-temurin:21.0.2_13-jdk-alpine

MAINTAINER Kevin S. Clarke - ksclarke@ksclarke.io

RUN apk add --no-cache busybox-extras bash nano \
    && addgroup -S jpv3 && adduser -S jpv3 -G jpv3 -s bash

COPY --chown=jpv3:jpv3 --chmod=0500 jpv3.cgi /var/www/cgi-bin/jpv3.cgi
COPY --chown=jpv3:jpv3 --chmod=0400 jpv3.jar /var/www/jpv3.jar
COPY --chown=jpv3:jpv3 --chmod=0400 imports.jsh /var/www/imports.jsh

EXPOSE 80

USER jpv3

CMD [ "httpd", "-v", "-f", "-p", "80", "-h", "/var/www" ]
