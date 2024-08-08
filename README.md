# Martian Aleph plugin

This is a Clojure lib for a [Martian](https://github.com/oliyh/martian) plugin
to use the [Aleph](https://aleph.io) http client.

## Usage

For more details on how to use Martian, see the [Martian docs](https://github.com/oliyh/martian#latest-versions--api-docs).

In order to use Aleph as your http client, include the lib in your `deps.edn`:

```clojure
{:deps {com.monkeyprojects/martian-aleph {:mvn/version "<VERSION>"}}}
```
Or when using Leiningen:
```clojure
(...
 :dependencies [[com.monkeyprojects/martian-aleph "<VERSION>"]])
```

If the endpoint provides an OpenAPI definition, you can set up a context:
```clojure
(require '[monkey.martian.aleph :as mma])

(def ctx (mma/bootstrap-openapi "https://some-rest-endpoint/swagger.json"))
```
After that, you can use `martian.core/response-for` to send requests to the remote API.

When defining your own routes (when the other end does not provide a spec), you
can use `bootstrap`:
```clojure
(def routes
 [{:route-name ::test-route
   :path-parts ["/test"]
   :method :get
   :produces ["application/json"]}])

(def ctx (mma/bootstrap "http://remote-endpoint" routes))
;; You can also pass in additional options as a third argument
```

This will set up a Martian context that adds interceptors specific to using Aleph HTTP
clients.  Since Aleph always uses async requests, the response is a [Manifold](https://github.com/clj-commons/manifold)
deferred, so you need to `deref` the response, or you can compose it with other async calls.

## Testing

Martian provides functionality for mocking endpoints for testing purposes.  Unfortunately,
this is not client-agnostic, so we have provided custom functions for creating a Martian
test context.

```clojure
(require '[martian.test :as mt])
(require '[martian.core :as mc])

;; Create a test context
(def test-ctx (-> (mma/as-test-context ctx)
                  (mt/respond-with {::test-route {:status 200}})))

;; Testing
(is (= 200 (:status @(mc/response-for test-ctx ::test-route))))
```

In the future, it's the intention to actually PR this into the Martian source code
itself, so we can also include the test code in `martian.test` itself.  But until then,
consider this a workaround.

## License

Copyright (c) 2024 by [Monkey Projects](https://www.monkey-projects.be).

[MIT License](LICENSE)