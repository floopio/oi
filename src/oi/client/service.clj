(ns oi.client.service
  (:require [cheshire.core :refer :all]
            [clojure.tools.logging :as log]
            [figgus.core :as cfg]
            [org.httpkit.client :as http]))

(def service-name (ref nil))
(def node-id (ref nil))
(def service-state (ref nil))

(defn heartbeat []
  "Function to send the hearbeat message with the state of the instance. 
   This function will block."
  (dosync (when-not @node-id
            (throw (IllegalStateException. "You must call register-service fisrt"))))
  (http/put (str (cfg/get "client.master") "/api/service/" @service-name "/" @node-id)
            {:content-type "application/json"
             :body (generate-string {:state @service-state})}
            (fn [{:keys [status headers body error]}]
              (log/debug "Status:" status)))
  true)

(defn- heartbeater []
  "Function for sending heartbeats to the master."
  (log/debug "Heartbeat thread -" @node-id "-" @service-state)
  (when (heartbeat)
    (Thread/sleep (cfg/get "client.heartbeatms" 4000))
    (recur)))

(defn register-service [name metadata]
  (dosync (when @service-name
            (throw (IllegalStateException. "Register has already been called - this can only be called once.")))
          (alter service-name (fn [_] name)))
  (if @node-id
    (do (log/warn "This node is already registered with ID " @node-id " you cannot register it again.")
        @node-id)
    (http/put (str (cfg/get "client.master") "/api/service/" name)
              {:content-type "application/json"
               :body (generate-string {:metadata metadata})}
              (fn [{:keys [status headers body error]}]
                (if (not (= 201 status))
                  (log/error "Unable to register node!")
                  (do
                    (dosync
                     (alter node-id (fn [_] (get (parse-string body true) :instance-id)))
                     (alter service-state (fn [_] :starting)))
                    (log/info "Starting heartbeat thread")
                    (future (heartbeater))))))))

(defn set-state [state]
  "Function to set the state of the service instance on next heartbeat."
  (log/info "Setting instance state to" state "-" @node-id)
  (dosync (alter service-state (fn [_] state)))
  nil)
