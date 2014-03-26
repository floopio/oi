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
;;
;;
;; c1   -> s1   ->   r1, r2, r3
;;      -> s2   ->   r1, r4, r5
;;
;;

;; Flag to update from the server instances - on false all threads will die
(def ^:private run-updates (atom true))

;; Maps the service name to a map of server-id->service-instances
(def remote-services (ref {}))

;; Updates the remote-services map with the contents of a remote server
(defn update-service-handler [name]
  (fn [{:keys [status headers body error]}]
    (log/debug "update-service-handler -" status)
    (if (or error (not (= status 200)))
      (if (= status 404)
        (dosync (alter remote-services dissoc name))
        (log/error "Unable to process request - status:" status "\nError:" error "\nBody:" body))
      (let [b (parse-string body)]
        (dosync
         (when-not (contains? @remote-services name)
           (alter remote-services merge {name {}}))
         (alter remote-services merge {name (merge (get @remote-services name)
                                                   {(get b "server-instance-id") (get b "instances")})}))))))

(defn- known-instances []
  "Returns a vector of instances this client knows about, this vector matches the ordering of priority of services."
  (cfg/get "client.instances" []))

(defn- update-service [name]
  (doseq [f (map #(http/get (str % "/api/service/" name)
                            {:query-params {:status "up"}
                             :content-type "application/json"}
                            (update-service-handler name))
                 (known-instances))]
    @f)
  nil)

(defn- init-client []
  (log/info "Initialising the client...")
  (swap! run-updates (fn [_] true))
  (log/debug "Starting update-thread")
  (future (loop [names (keys @remote-services)]
            (log/info "Updating service names:" names)
            (doseq [name names]
              (log/debug "Updating service:" name)
              (update-service name))
            (Thread/sleep (cfg/get "client.updateperiodms" 5000))
            (when @run-updates
              (recur (keys @remote-services)))))
  (log/info "Client initialisation complete"))

(defn stop-client []
  (log/info "Stopping the client...")
  (swap! run-updates (fn [_] false))
  (log/info "Client stopped, update thread will die"))

(defn get-service [name & {:keys [no-cache] :or {no-cache false}}]
  "Returns the collection of available instances for a given service."
  (when (or no-cache (not (contains? @remote-services name)))
    (log/debug "Forcing update of service:" name)
    (update-service name))
  (get @remote-services name))



(defn connect [name]
  "Returns a socket to the service, will ensure that it is connected."
  nil)

;; Start the update threads
(init-client)
