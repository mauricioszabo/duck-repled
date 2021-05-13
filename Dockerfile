FROM circleci/clojure:openjdk-11-tools-deps-node

COPY . .
ENV PATH=/home/circleci/.local/bin:/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games:/usr/local/openjdk-11:/usr/local/openjdk-11/bin
RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod +x lein
RUN mv lein /usr/local/bin

RUN clojure -Stree
RUN lein shadow-release
