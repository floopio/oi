(ns oi.core
  (:require [oi.resources :as resources]
            [oi.watchdog :as dog]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes ANY]]
            [liberator.dev :refer [wrap-trace]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.params :refer [wrap-params]]))

(defroutes app
  (ANY "/" [] (resources/index))
  (ANY "/api/service/:svc-name" [svc-name]
       (resources/service svc-name))
  (ANY "/api/service/:svc-name/:instance-id" [svc-name instance-id]
       (resources/instance svc-name instance-id))
  (ANY "/api/service/:svc-name/:instance-id/:metadata-key" [svc-name instance-id metadata-key]
       (resources/metadata svc-name instance-id metadata-key)))

(def handler
  (-> app
      (wrap-trace :header)
      (wrap-params)))

(defn -main []
  (dog/start)
  (log/info "Starting jetty...")
  (run-jetty #'handler {:port 8080})
  (dog/stop)
  (log/info "Exiting..."))

