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
             [file :as mf]
             [interceptors :as mi]
             [openapi :as mo]]
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

(defn- parse-json [s key-fn]
  (with-open [r (bs/to-reader s)]
    (json/parse-stream r key-fn)))

(defn make-encoders [key-fn]
  {"application/edn"  {:encode pr-str
                       :decode parse-edn}
   "application/json" {:encode json/generate-string
                       :decode #(parse-json % key-fn)}})

(def encoders (make-encoders keyword))

(def default-interceptors
  (concat mc/default-interceptors [mi/default-encode-body
                                   (mi/coerce-response encoders)
                                   perform-request]))

(def default-opts
  {:interceptors default-interceptors})

(defn bootstrap [api-root routes & [opts]]
  (mc/bootstrap api-root routes (merge default-opts opts)))

(defn- load-definition [url load-opts]
  (letfn [(verify-response [resp]
            (if (>= (:status resp) 400)
              (throw (ex-info "Unable to load OpenAPI spec" resp))
              resp))]
    (or (mf/local-resource url)
        @(md/chain
          (http/get url (merge {:as :text} load-opts))
          verify-response
          :body
          #(parse-json % keyword)))))

(defn bootstrap-openapi [url & [{:keys [server-url] :as opts} load-opts]]
  (let [definition (load-definition url load-opts)
        base-url (mo/base-url url server-url definition)]
    (mc/bootstrap-openapi base-url definition (merge default-opts opts))))

(def bootstrap-swagger bootstrap-openapi)

(defn as-test-context
  "Given a Martian context, converts it into a test context that can be used with
   the functions as provided by `martian.test`"
  [ctx]
  ;; Replace the request handler name with the one from httpkit, because that's
  ;; supported by martian.test and is also an async handler.
  (update ctx :interceptors mi/inject
          (assoc perform-request :name :martian.httpkit/perform-request)
          :replace
          (:name perform-request)))
