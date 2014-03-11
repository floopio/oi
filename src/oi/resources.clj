(ns oi.resources
  (:require [oi.service :as service]
            [oi.util :as util]
            [cheshire.core :refer :all]
            [figgus.core :as cfg]
            [liberator.core :refer [defresource]]
            [liberator.representation :refer [ring-response]]
            [liberator.util :refer [by-method]]))

(def ^:private server-instance-id (util/random-uuid))

(defn- str->keyword [m]
  (into {} (for [[k v] m] [(keyword k) (if (string? v) (keyword v) v)])))

(defresource index []
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [_] {:name (cfg/get "server.name" "Service Locator")
                      :server-instance-id server-instance-id}))

(defresource service [svc-name]
  :available-media-types ["application/json"]
  :allowed-methods [:get :put]
  :exists? (fn [_] (pos? (count (service/get-service svc-name))))
  :handle-ok (fn [{{params :params} :request}]
               {:server-instance-id server-instance-id
                :instances (service/get-service svc-name (str->keyword (dissoc params :svc-name)))})
  :processable? (by-method :put (fn [{{body-stream :body} :request}]
                                  (if body-stream
                                    (let [body (parse-string (slurp body-stream) true)]
                                      {::metadata (str->keyword (:metadata body))})
                                    true))
                           :get true)
  :put! (fn [ctx] {::instance (service/register-svc svc-name (or (::metadata ctx) {}))})
  :new? (fn [_] true)
  :handle-created (fn [{instance ::instance
                        request :request}]
                    (ring-response {:headers {"Location" (str (:uri request) "/" (:instance-id instance))}
                                    :body (generate-string instance)})))

(defresource instance [svc-name instance-id]
  :available-media-types ["application/json"]
  :allowed-methods [:get :put]
  :exists? (fn [_] (not (nil? (service/get-instance instance-id))))
  :handle-ok (fn [_] (generate-string (service/get-instance instance-id)))
  :can-put-to-missing? false
  :processable? (by-method :put (fn [{{body-stream :body} :request}]
                                  (let [body (slurp body-stream)
                                        state (keyword (:state (parse-string body true)))]
                                    (when (service/valid-state? state)
                                      {::state state})))
                           :get true)
  :put! (fn [ctx] (service/heartbeat instance-id (::state ctx)))
  :new? false)

(defresource metadata [svc-name instance-id metadata-key]
  :available-media-types ["application/json"]
  :allowed-methods [:get :put :delete]
  :exists? (fn [_] (let [exists? (not (nil? (service/get-metadata instance-id (keyword metadata-key))))]
                     [exists? {::exists? exists?}]))
  :handle-ok (fn [_] {metadata-key (service/get-metadata instance-id (keyword metadata-key))})
  :can-put-to-missing? true
  :new? (fn [ctx] (not (::exists? ctx)))
  :put! (fn [{{body-stream :body} :request}]
          (service/set-metadata instance-id (keyword metadata-key) (keyword(slurp body-stream))))
  :delete! (fn [_] (service/del-metadata instance-id (keyword metadata-key))))
