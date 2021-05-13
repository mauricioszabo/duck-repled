FROM openjdk:alpine

USER root
WORKDIR /work
COPY . .
RUN apk add --no-cache curl npm bash git
RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod +x lein
RUN mv lein /usr/local/bin

RUN curl -O https://download.clojure.org/install/linux-install-1.10.3.839.sh
RUN chmod +x linux-install-1.10.3.839.sh
RUN ./linux-install-1.10.3.839.sh

RUN clojure -Stree
RUN lein deps
RUN lein shadow-release
