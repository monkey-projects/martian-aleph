(ns monkey.martian.aleph
  "Plugin for Martian that uses Aleph as http client"
  (:require [aleph.http :as http]
            [cheshire.core :as json]
            [clj-commons.byte-streams :as bs]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [manifold.deferred :as md]
            [martian
             [core :as mc]
             [encoders :as me]
             [interceptors :as mi]]
            [tripod.context :as tc])
  (:import java.io.PushbackReader))

(defn aleph-request
  "Actually performs the HTTP request given the Martian context."
  [{:keys [request] :as ctx}]
  (letfn [(apply-interceptors [r]
            ;; Process the deferred response by applying the interceptors
            (-> r
                (as-> x (assoc ctx :response x))
                (tc/execute)
                :response))]
    (log/trace "Sending HTTP request:" request)
    ;; Return the deferred as a response, don't apply interceptors here
    (-> ctx
        (mi/remove-stack)
        (assoc :response (md/chain
                          (http/request request)
                          apply-interceptors)))))

(def perform-request
  {:name :martian.aleph/perform-request
   :leave aleph-request})

(defn- parse-edn [s]
  (with-open [r (PushbackReader. (bs/to-reader s))]
    (edn/read r)))

(defn- parse-json [s]
  (with-open [r (bs/to-reader s)]
    (json/parse-stream r keyword)))

(def encoders
  {"application/edn"  {:encode pr-str
                       :decode parse-edn}
   "application/json" {:encode json/generate-string
                       :decode parse-json}})

(def default-interceptors
  (concat mc/default-interceptors [mi/default-encode-body
                                   (mi/coerce-response encoders)
                                   perform-request]))

(def default-opts
  {:interceptors default-interceptors})
