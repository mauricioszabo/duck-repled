(defproject link.szabo.mauricio/duck-repled "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[metosin/malli "0.5.1"]]

  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}

  :source-paths ["src" "pathom3/src"]

  :repl-options {:init-ns user}
  :aliases {"watch" ["run" "-m" "user/watch"]
            "shadow-release" ["run" "-m" "user/release"]}
  :profiles {:dev {:dependencies [[thheller/shadow-cljs "2.12.5"]
                                  [check "0.2.0-SNAPSHOT"]
                                  [borkdude/sci "0.2.5"]]
                   :source-paths ["dev" "test"]}})
