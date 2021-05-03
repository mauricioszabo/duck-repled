(defproject duck-repled "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies []

  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}

  :source-paths ["src" "pathom3/src"]

  :repl-options {:init-ns user}
  :profiles {:dev {:dependencies [[thheller/shadow-cljs "2.12.5"]
                                  [check "0.2.0-SNAPSHOT"]]
                   :source-paths ["dev" "test"]}})
