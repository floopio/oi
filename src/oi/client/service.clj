(ns oi.client.service
  (:require [cheshire.core :refer :all]
            [clojure.tools.logging :as log]
            [figgus.core :as cfg]
            [org.httpkit.client :as http]))

(def node-id (atom nil))

(defn register-service [service-name metadata]
  (if @node-id
    (do (log/warn "This node is already registered with ID " @node-id " you cannot register it again.")
        @node-id)
    (http/put (str (cfg/get "client.master") "/api/service/" service-name)
              {:content-type "application/json"
               :body (generate-string {:metadata metadata})}
              (fn [{:keys [status headers body error]}]
                (if (not (= 201 status))
                  (log/error "Unable to register node!")
                  (swap! node-id (fn [_] (get (parse-string body true) :instance-id))))))))
