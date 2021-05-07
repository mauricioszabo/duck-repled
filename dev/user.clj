(ns user)

(defn watch []
  (let [start-server (requiring-resolve 'shadow.cljs.devtools.server/start!)
        start-watch (requiring-resolve 'shadow.cljs.devtools.api/watch)]
    (start-server)
    (start-watch :tests)))

(defn release []
  (let [start-server (requiring-resolve 'shadow.cljs.devtools.server/start!)
        release (requiring-resolve 'shadow.cljs.devtools.api/release)]
    (start-server)
    (release :tests)))

(defn stop []
  (let [stop-server (requiring-resolve 'shadow.cljs.devtools.server/stop!)]
    (stop-server)))
