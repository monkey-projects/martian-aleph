(ns monkey.martian.aleph-test
  (:require [clojure.test :refer [deftest testing is]]
            [aleph
             [http :as http]
             [netty :as an]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [manifold.deferred :as md]
            [martian
             [core :as mc]
             [swagger :as ms]
             [test :as mt]]
            [monkey.martian.aleph :as sut]
            [reitit
             [ring :as rr]
             [swagger :as swagger]]))

(deftest aleph-request
  (testing "performs http request"
    (with-redefs [http/request (constantly (md/success-deferred
                                            {:status 200}))]
      (is (= 200
             (-> {:request
                  {:method :get
                   :url "http://test"}}
                 (sut/aleph-request)
                 :response
                 deref
                 :status))))))

(defn json-test-handler [_]
  {:status 200
   :body (json/generate-string {:key "value"})
   :headers {:content-type "application/json"}})

(defn edn-test-handler [_]
  {:status 200
   :body (pr-str {:key "value"})
   :headers {:content-type "application/edn"}})

(defn wrap-swagger [handler]
  (fn [req]
    (-> (handler req)
        (update :body json/generate-string))))

(def router
  (rr/router
   [""
    [["/json"
      {:get json-test-handler
       :operationId :json}]
     ["/edn"
      {:get edn-test-handler
       :operationId :edn}]
     ["/swagger.json"
      {:no-doc true
       :middleware [wrap-swagger]
       :get (swagger/create-swagger-handler)}]]]))

(deftest integration-test
  (with-open [server (http/start-server (rr/ring-handler router) {:port 0})]
    (testing "json call"
      (let [port (an/port server)
            routes [{:route-name :test-route
                     :path-parts ["/json"]
                     :method :get
                     :produces ["application/json"]}]
            ctx (sut/bootstrap (str "http://localhost:" port) routes)
            resp (mc/response-for ctx :test-route)]
        (is (= 200 (:status @resp)))
        (is (= {:key "value"} (:body @resp)))))
    
    (testing "edn call"
      (let [port (an/port server)
            routes [{:route-name :test-route
                     :path-parts ["/edn"]
                     :method :get
                     :produces ["application/edn"]}]
            ctx (sut/bootstrap (str "http://localhost:" port) routes)
            resp (mc/response-for ctx :test-route)]
        (is (= 200 (:status @resp)))
        (is (= {:key "value"} (:body @resp)))))

    (testing "from swagger"
      (let [port (an/port server)
            ctx (sut/bootstrap-openapi (format "http://localhost:%d/swagger.json" port))
            spec (http/get (format "http://localhost:%d/swagger.json" port))]

        (testing "can fetch swagger"
          (is (= 200 (:status @spec)))
          (let [swagger (json/parse-string (slurp (:body @spec)))]
            (is (not-empty swagger))
            (is (some? (ms/swagger->handlers swagger)))))

        (testing "provides handlers"
          (is (not-empty (:handlers ctx))))

        (testing "can invoke endpoint"
          (is (= 200 (-> (mc/response-for ctx :json)
                         deref
                         (:status)))))))))

(deftest as-test-context
  (testing "can use martian test functions with it"
    (let [routes [{:route-name ::test
                   :path-parts ["/test"]
                   :produces ["application/json"]}]
          ctx (-> (sut/bootstrap "http://test" routes)
                  (sut/as-test-context)
                  (mt/respond-with {::test {:status 200}}))]
      (is (= 200 (:status @(mc/response-for ctx ::test {}))))
      (is (thrown? Exception (:status @(mc/response-for ctx ::other {})))))))
