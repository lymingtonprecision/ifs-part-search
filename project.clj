(defproject ifs-part-search "1.0.2-SNAPSHOT"
  :description "A web API for searching for parts within IFS."
  :url "https://github.com/lymingtonprecision/ifs-part-search"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 ;; components/system
                 [com.stuartsierra/component "0.2.3"]
                 [environ "1.0.0"]
                 [prismatic/schema "0.4.2"]
                 [prismatic/plumbing "0.4.2"]
                 [instaparse "1.4.0"]
                 [org.clojure/math.combinatorics "0.1.1"]

                 ;; logging
                 [org.clojure/tools.logging "0.3.1"]
                 [org.spootnik/logconfig "0.7.3"]

                 ;; database
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojars.zentrope/ojdbc "11.2.0.3.0"]
                 [hikari-cp "1.2.3"]
                 [honeysql "0.5.2"]

                 ;; web
                 [http-kit "2.1.19"]
                 [ring/ring-core "1.3.2"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 [prismatic/fnhouse "0.1.1"]
                 [com.cognitect/transit-clj "0.8.271"]
                 [ring-transit "0.1.3"]]

  :main ifs-part-search.main

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.10"]
                                  [org.clojure/test.check "0.7.0"]]}
             :repl {:source-paths ["dev" "src"]}
             :uberjar {:aot [ifs-part-search.main]}}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :repl-options {:init-ns user
                 :init (user/init)})
