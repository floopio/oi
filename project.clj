(defproject io.floop/oi "0.1.0-SNAPSHOT"
  :description "A service-locator service."
  :url "http://github.com/floopio/oi"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0"]
                 [compojure "1.1.6"]
                 [http-kit "2.1.16"]
                 [io.floop/figgus "0.2.3"]
                 [log4j/log4j "1.2.17"]
                 [liberator "0.10.0"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring-mock "0.1.5" :scope "test"]]
  :plugins [[lein-kibit "0.0.8"]]
;  :aot :all
  :main oi.core)
