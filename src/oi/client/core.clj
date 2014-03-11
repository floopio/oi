(ns oi.client.core
  (:require [cheshire.core :refer :all]
            [clojure.tools.logging :as log]
            [figgus.core :as cfg]
            [org.httpkit.client :as http]))


;; SERVICE LISTING
;; Maintain a mapping of instance to ID, and a mapping of svc-name to map of ID & instances
;; * warn if ID changes (indicates server restart)
;; * periodically update the list of instances for a given service
;; * if the service is not known about, synchronously call and then return
;;
;; CONNECTING
;; * Handle the host not being there, try hosts until a connection is found
;; * provide wrappers for http
;;
;; WATCHDOG
;; * need a watchdog to expire the entires in the remote-services map - investigate cache impls




;; Maps the service name to a map of server-id->service-instances
(def remote-services (ref {}))

;; Updates the remote-services map with the contents of a remote server
(defn update-service-handler [name]
  (fn [{:keys [status headers body error]}]
    (log/debug "update-service-handler -" status)
    (if error
      (log/error "Unable to process request - status:" status "\nError:" error "\nBody:" body)
      (do
        (let [b (parse-string body)]
          (dosync
           (when (not (contains? @remote-services name))
             (alter remote-services merge {name {}}))
           (alter remote-services merge {name (merge (get @remote-services name)
                                                     {(get b "server-instance-id") (get b "instances")})})))))))

(defn known-instances []
  "Returns a vector of instances this client knows about, this vector matches the ordering of priority of services."
  (cfg/get "client.instances" []))

(defn get-service [name & {:keys [no-cache] :or {no-cache false}}]
  "Returns the collection of available instances for a given service."

  (doseq [f (map #(http/get (str % "/api/service/" name)
                            (update-service-handler name))
                 (cfg/get "client.instances" []))]
    @f)

  (get @remote-services name))

(defn connect [name]
  "Returns a socket to the service, will ensure that it is connected."
  nil)



